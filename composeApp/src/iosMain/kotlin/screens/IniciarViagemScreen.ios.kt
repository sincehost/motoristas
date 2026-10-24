package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import api.ViagemRequest
import database.AppRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import platform.UIKit.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlinx.cinterop.*
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI
import util.formatarKmVeiculo
import util.normalizarKmVeiculoParaEnvio
import util.kmParaDouble
import util.kmParaCampoVeiculo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

// Classe delegate FORA do @Composable
private class CameraDelegate(
    private val onFotoCaptured: (String, ImageBitmap) -> Unit,
    private val onMessage: (String, Boolean) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    @OptIn(ExperimentalForeignApi::class)
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage

        if (image != null) {
            try {
                val base64 = CameraHelper.imageToBase64(image)
                if (base64 == null) {
                    onMessage("Erro ao converter imagem para base64", true)
                    picker.dismissViewControllerAnimated(true, null)
                    return
                }

                val imageData = UIImageJPEGRepresentation(image, 0.7)
                if (imageData != null) {
                    val length = imageData.length.toInt()
                    val bytes = ByteArray(length)
                    bytes.usePinned { pinned ->
                        imageData.getBytes(pinned.addressOf(0), length.toULong())
                    }

                    val skiaImage = SkiaImage.makeFromEncoded(bytes)
                    if (skiaImage != null) {
                        val bitmap = skiaImage.toComposeImageBitmap()
                        onFotoCaptured(base64, bitmap)
                        // Foto capturada silenciosamente - a prévia já confirma
                    } else {
                        onMessage("Erro ao processar imagem", true)
                    }
                } else {
                    onMessage("Erro ao comprimir imagem", true)
                }
            } catch (e: Exception) {
                onMessage(util.mensagemErroAmigavel(e.message), true)
            }
        } else {
            onMessage("Nenhuma imagem selecionada", true)
        }

        picker.dismissViewControllerAnimated(true, null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        // Sem mensagem: cancelar a foto é ação normal do motorista, não precisa
        // de aviso — e "Captura cancelada" ia pelo caminho de SUCESSO (sucessoMsg),
        // cujo dismiss chama onSucesso()/onVoltar() e navegava pra fora da tela.
        picker.dismissViewControllerAnimated(true, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun IniciarViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit,
    onFinalizarViagem: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val destinos = remember { repository.getAllDestinos() }
    // Se a frota ainda não tiver nenhum equipamento classificado (Veículo x
    // Implemento — depende de migração no servidor), cai pra lista completa
    // no seletor de veículo principal, senão a tela ficaria sem opção nenhuma.
    val todosEquipamentos = remember { repository.getAllEquipamentos() }
    val veiculos = remember { repository.getVeiculos().ifEmpty { todosEquipamentos } }
    val implementos = remember { repository.getImplementos() }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    var erroMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Estados de verificação de viagem em andamento
    var carregando by remember { mutableStateOf(true) }
    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }
    var finalizacaoAnteriorNaFila by remember { mutableStateOf(false) }

    // Verifica se já tem viagem em andamento (LOCAL primeiro, depois API)
    LaunchedEffect(Unit) {
        carregando = true

        // 1. Verifica LOCALMENTE primeiro
        val viagemLocal = repository.getViagemAtual()
        if (viagemLocal != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemLocal.viagem_id.toInt(),
                destino = viagemLocal.destino,
                data = viagemLocal.data_inicio
            )
            carregando = false
            return@LaunchedEffect
        }

        // 2. Se não tem local, tenta API
        try {
            val response = api.ApiClient.abastecimentoDados(
                api.AbastecimentoDadosRequest(

                    motorista_id = motorista?.motorista_id ?: ""
                )
            )
            if (response.status == "ok" && response.viagens.isNotEmpty() &&
                repository.temFinalizacaoPendenteDaViagem(response.viagens.first().id.toLong())) {
                // Já finalizada no celular, mas a finalização espera os
                // lançamentos dela subirem — o servidor recusaria a nova.
                finalizacaoAnteriorNaFila = true
            } else if (response.status == "ok" && response.viagens.isNotEmpty()) {
                val viagem = response.viagens.first()
                viagemEmAndamento = viagem
                // Salva localmente para funcionar offline depois
                val kmInicioExistente = repository.getViagemAtual()?.km_inicio ?: ""
                val kmInicioParaSalvar = if (viagem.km_inicio.isNotEmpty()) viagem.km_inicio else kmInicioExistente
                repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data, kmInicioParaSalvar)
            }
        } catch (e: Exception) {
            // Sem internet e sem viagem local - permite iniciar
        }
        carregando = false
    }

    // Rascunho (util/Rascunho.kt): ordem de frete, CT-e, rota e peso voltam
    // se o motorista sair da tela antes de iniciar. Placa e KM já são
    // preenchidos sozinhos (último veículo / último KM da placa).
    val rascunho = util.rememberRascunho(repository, "iniciar_viagem", motorista?.motorista_id ?: "")
    val r = rascunho.valores
    var mostrarAvisoRascunho by remember { mutableStateOf(rascunho.recuperado) }

    var numerobd by remember { mutableStateOf(r["numerobd"] ?: "") }
    var cte by remember { mutableStateOf(r["cte"] ?: "") }
    var mostrarCampos2 by remember { mutableStateOf(!r["numerobd2"].isNullOrBlank() || !r["cte2"].isNullOrBlank()) }
    var numerobd2 by remember { mutableStateOf(r["numerobd2"] ?: "") }
    var cte2 by remember { mutableStateOf(r["cte2"] ?: "") }

    // Rota Fixa (padrão) x Rota Contínua — ver admin_config.php?tab=app.
    // Em Rota Contínua: destino vira texto livre, Peso da Carga/Valor do
    // Frete somem (viram opcionais). Nada disso muda pra quem está em
    // Rota Fixa — fica tudo exatamente como sempre foi.
    val rotaContinua = remember { repository.isRotaContinua() }

    var destinoExpandido by remember { mutableStateOf(false) }
    var buscaRota by remember { mutableStateOf("") }
    // Rota do rascunho só volta se ainda existir na lista.
    var destinoSelecionado by remember {
        mutableStateOf(
            r["destinoId"]?.toLongOrNull()
                ?.let { id -> destinos.firstOrNull { it.servidor_id == id } }
                ?.let { Pair(it.servidor_id, it.nome) }
        )
    }
    // Só usado quando rotaContinua — texto digitado livremente pelo motorista.
    var destinoLivre by remember { mutableStateOf(r["destinoLivre"] ?: "") }

    var placaExpandida by remember { mutableStateOf(false) }
    var buscaPlaca by remember { mutableStateOf("") }
    var veiculoSelecionadoId by remember { mutableStateOf<Long?>(null) }
    var placaSelecionada by remember { mutableStateOf<String?>(null) }

    // Composição — até 2 implementos opcionais (carreta, semirreboque…).
    var implemento1Visivel by remember { mutableStateOf(false) }
    var implemento1Expandido by remember { mutableStateOf(false) }
    var implemento1Id by remember { mutableStateOf<Long?>(null) }
    var implemento1Placa by remember { mutableStateOf<String?>(null) }

    var implemento2Visivel by remember { mutableStateOf(false) }
    var implemento2Expandido by remember { mutableStateOf(false) }
    var implemento2Id by remember { mutableStateOf<Long?>(null) }
    var implemento2Placa by remember { mutableStateOf<String?>(null) }

    var dataViagem by remember { mutableStateOf(dataAtualFormatada()) }
    var kmInicio by remember { mutableStateOf(TextFieldValue("")) }
    // true enquanto o KM de Início ainda é o que o app preencheu sozinho
    // (último KM da placa) — vira false assim que o motorista digita.
    var kmAutoPreenchido by remember { mutableStateOf(false) }
    // true quando o KM veio da foto do painel (2.1) — some quando ele digita.
    var kmLidoPelaCamera by remember { mutableStateOf(false) }

    // Pré-preenche com o último veículo usado neste aparelho — só se ele
    // ainda estiver na lista (veículo desativado/removido não volta).
    LaunchedEffect(Unit) {
        if (placaSelecionada.isNullOrBlank()) {
            repository.getUltimoVeiculoUsado()?.let { (placa, _) ->
                veiculos.firstOrNull { it.placa == placa }?.let { equip ->
                    veiculoSelecionadoId = equip.servidor_id
                    placaSelecionada = equip.placa
                }
            }
        }
    }

    // KM de Início = último KM conhecido da placa. Só mexe no campo se ele
    // estiver vazio ou ainda com o valor que o próprio app colocou — nunca
    // sobrescreve o que o motorista digitou.
    LaunchedEffect(placaSelecionada) {
        val placa = placaSelecionada
        if (placa.isNullOrBlank()) return@LaunchedEffect
        if (kmInicio.text.isNotBlank() && !kmAutoPreenchido) return@LaunchedEffect
        val km = repository.getUltimoKmConhecido(placa)
        val texto = if (km != null) kmParaCampoVeiculo(km, repository.hodometroTemDecimal(placa)) else ""
        kmInicio = TextFieldValue(texto, TextRange(texto.length))
        kmAutoPreenchido = texto.isNotEmpty()
    }
    var pesoCarga by remember { mutableStateOf(TextFieldValue(r["peso"] ?: "", selection = TextRange((r["peso"] ?: "").length))) }

    util.SalvarRascunhoAutomatico(
        rascunho,
        "numerobd" to numerobd, "cte" to cte, "numerobd2" to numerobd2, "cte2" to cte2,
        "destinoId" to (destinoSelecionado?.first?.toString() ?: ""),
        "destinoLivre" to destinoLivre, "peso" to pesoCarga.text
    )

    fun limparFormulario() {
        numerobd = ""; cte = ""; numerobd2 = ""; cte2 = ""; mostrarCampos2 = false
        destinoSelecionado = null; destinoLivre = ""
        pesoCarga = TextFieldValue("")
        rascunho.limpar(encerrar = false)
        mostrarAvisoRascunho = false
    }

    var fotoBase64 by remember { mutableStateOf<String?>(null) }
    var fotoImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Foto do painel tirada → tenta ler o KM dela (Vision, no aparelho). Só
    // preenche se passar na conferência com o último KM da placa e se o
    // motorista ainda não tiver digitado nada.
    LaunchedEffect(fotoBase64) {
        val foto = fotoBase64 ?: return@LaunchedEffect
        val placa = placaSelecionada
        if (placa.isNullOrBlank()) return@LaunchedEffect
        if (kmInicio.text.isNotBlank() && !kmAutoPreenchido && !kmLidoPelaCamera) return@LaunchedEffect
        val temDecimal = repository.hodometroTemDecimal(placa)
        val referencia = repository.getUltimoKmConhecido(placa)?.let { util.kmParaDouble(it) }
        val km = util.lerKmDaFoto(foto, referencia, 5000.0, temDecimal) ?: return@LaunchedEffect
        if (kmInicio.text.isNotBlank() && !kmAutoPreenchido && !kmLidoPelaCamera) return@LaunchedEffect
        val texto = util.kmParaCampoVeiculo(util.formatarKmExibicao(km), temDecimal)
        kmInicio = TextFieldValue(texto, TextRange(texto.length))
        kmAutoPreenchido = false
        kmLidoPelaCamera = true
    }

    var loading by remember { mutableStateOf(false) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Delegate persistente - criado uma vez e mantido
    val cameraDelegate = remember {
        CameraDelegate(
            onFotoCaptured = { base64, bitmap ->
                fotoBase64 = base64
                fotoImageBitmap = bitmap
            },
            onMessage = { msg, erro ->
                mostrarMensagem(msg, erro)
            }
        )
    }

    // Função para abrir câmera iOS
    fun abrirCamera() {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (viewController == null) {
            mostrarMensagem("Não foi possível abrir a câmera", isErro = true)
            return
        }

        if (CameraHelper.cameraSemPermissao()) {
            mostrarPermissaoCameraNegada = true
            return
        }

        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            mostrarMensagem("Câmera não disponível neste dispositivo", isErro = true)
            return
        }

        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            allowsEditing = false
            delegate = cameraDelegate
        }

        viewController.presentViewController(picker, true, null)
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Iniciar Viagem",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        // Carregando
        if (carregando) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Verificando viagens...", color = AppColors.TextSecondary)
                }
            }
        }
        // Viagem anterior finalizada, mas ainda subindo — BLOQUEIA com explicação
        else if (finalizacaoAnteriorNaFila) {
            FinalizacaoAnteriorNaFilaAviso(modifier = Modifier.padding(padding), onVoltar = onVoltar)
        }
        // Já tem viagem em andamento - BLOQUEIA
        else if (viagemEmAndamento != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppColors.Background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = AppColors.Orange,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Viagem em andamento",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = AppColors.Orange
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Você já possui uma viagem em andamento. Finalize-a antes de iniciar uma nova.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))

                        // Card com info da viagem atual
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    null,
                                    tint = AppColors.Primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Destino:", fontSize = 12.sp, color = AppColors.TextSecondary)
                                    Text(
                                        viagemEmAndamento!!.destino,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Iniciada em: ${formatarData(viagemEmAndamento!!.data)}",
                                        fontSize = 12.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Atalho direto — antes só tinha "Voltar", e o motorista
                        // tinha que achar o Finalizar na barra de baixo.
                        Button(
                            onClick = onFinalizarViagem,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Finalizar esta viagem", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onVoltar,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Voltar ao Início")
                        }
                    }
                }
            }
        }
        // Pode iniciar viagem
        else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppColors.Background)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    }
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        if (mostrarAvisoRascunho) {
                            util.RascunhoRecuperadoAviso(onLimpar = { limparFormulario() })
                            Spacer(Modifier.height(12.dp))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = numerobd,
                                onValueChange = { numerobd = it },
                                label = { Text("Ordem de Frete *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Tag, null, tint = AppColors.Primary)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { }
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { mostrarCampos2 = !mostrarCampos2 }) {
                                Icon(
                                    if (mostrarCampos2) Icons.Default.RemoveCircle
                                    else Icons.Default.AddCircle,
                                    "Adicionar segundo",
                                    tint = AppColors.Primary
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = cte,
                            onValueChange = { cte = it },
                            label = { Text("Nº do CT-e *") },
                            leadingIcon = {
                                Icon(Icons.Default.Description, null, tint = AppColors.Primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { }
                            )
                        )

                        if (mostrarCampos2) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = numerobd2,
                                onValueChange = { numerobd2 = it },
                                label = { Text("Ordem de Frete 2") },
                                leadingIcon = {
                                    Icon(Icons.Default.Tag, null, tint = AppColors.TextSecondary)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { }
                                )
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = cte2,
                                onValueChange = { cte2 = it },
                                label = { Text("Nº do CT-e 2") },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, null, tint = AppColors.TextSecondary)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { }
                                )
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        if (rotaContinua) {
                            OutlinedTextField(
                                value = destinoLivre,
                                onValueChange = { destinoLivre = it },
                                label = { Text("Destino *") },
                                placeholder = { Text("Digite o destino da viagem") },
                                leadingIcon = { Icon(Icons.Default.Route, null, tint = AppColors.Primary) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        } else {
                            ui.AppDropdownField(
                                label = "Rota *",
                                selectedText = destinoSelecionado?.second ?: "",
                                expanded = destinoExpandido,
                                onExpandedChange = {
                                    focusManager.clearFocus()
                                    destinoExpandido = it
                                    buscaRota = ""
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Route, null, tint = AppColors.Primary)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                busca = if (destinos.size >= ui.ITENS_PARA_BUSCA) buscaRota else null,
                                onBuscaChange = { buscaRota = it }
                            ) {
                                destinos.filter { ui.combinaBusca(it.nome, buscaRota) }.forEach { destino ->
                                    ui.AppDropdownMenuItem(
                                        text = { Text(destino.nome) },
                                        onClick = {
                                            destinoSelecionado = Pair(destino.servidor_id, destino.nome)
                                            destinoExpandido = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        ui.AppDropdownField(
                            label = "Veículo (Cavalo Mecânico / Caminhão) *",
                            selectedText = placaSelecionada ?: "",
                            expanded = placaExpandida,
                            onExpandedChange = {
                                focusManager.clearFocus()
                                placaExpandida = it
                                buscaPlaca = ""
                            },
                            leadingIcon = {
                                Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            busca = if (veiculos.size >= ui.ITENS_PARA_BUSCA) buscaPlaca else null,
                            onBuscaChange = { buscaPlaca = it }
                        ) {
                            veiculos.filter { ui.combinaBusca(it.placa, buscaPlaca) }.forEach { equip ->
                                ui.AppDropdownMenuItem(
                                    text = { Text(equip.placa) },
                                    onClick = {
                                        veiculoSelecionadoId = equip.servidor_id
                                        placaSelecionada = equip.placa
                                        placaExpandida = false
                                    }
                                )
                            }
                        }

                        // Composição (implemento 1/2) tirada da tela de Iniciar Viagem a
                        // pedido — os estados abaixo continuam declarados e sempre vazios/
                        // invisíveis, então o envio (API/local) já manda null pra esses
                        // campos sozinho, sem precisar mexer na submissão.

                        Spacer(Modifier.height(12.dp))

                        DateInputField(
                            value = dataViagem,
                            onValueChange = { dataViagem = it },
                            label = "Data da Viagem *",
                            primaryColor = AppColors.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        ui.AvisoDataFutura(dataViagem)

                        Spacer(Modifier.height(12.dp))

                        // Característica fixa do veículo (não do motorista digitando) —
                        // ver util/KmUtils.kt e migracao_hodometro_decimal.sql.
                        val hodometroTemDecimal = remember(placaSelecionada) {
                            placaSelecionada?.let { repository.hodometroTemDecimal(it) } ?: true
                        }

                        OutlinedTextField(
                            value = kmInicio,
                            onValueChange = { newValue ->
                                val formatted = formatarKmVeiculo(newValue.text, hodometroTemDecimal)
                                if (formatted != kmInicio.text) { kmAutoPreenchido = false; kmLidoPelaCamera = false }
                                kmInicio = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            label = { Text("KM de Início *") },
                            placeholder = { Text(if (hodometroTemDecimal) "Ex.: 115670.5" else "Ex.: 115670") },
                            leadingIcon = {
                                Icon(Icons.Default.Speed, null, tint = AppColors.Primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (hodometroTemDecimal) KeyboardType.Decimal else KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { }
                            ),
                            singleLine = true
                        )
                        Text(
                            when {
                                kmLidoPelaCamera -> "Lido pela câmera. Confira com o painel antes de salvar."
                                kmAutoPreenchido -> "Último KM registrado desta placa. Confira com o painel antes de salvar."
                                hodometroTemDecimal -> "Digite como aparece no painel. Ex.: 115670.5"
                                else -> "Digite como aparece no painel. Ex.: 115670"
                            },
                            fontSize = 12.sp,
                            color = AppColors.Primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )

                        // Aviso (não bloqueia) se o KM digitado for menor que o último
                        // conhecido do veículo — cache local (sincronizado junto com a
                        // placa), funciona mesmo offline. Pode estar levemente
                        // desatualizado se o veículo rodou noutro aparelho desde o
                        // último sync, então é só aviso — mesma lógica do Android.
                        val kmCadastrado = remember(placaSelecionada) {
                            placaSelecionada?.let { repository.getKmEquipamentoPorPlaca(it) }
                        }
                        val kmMenorQueCadastrado = kmCadastrado != null && kmInicio.text.isNotBlank() &&
                            kmParaDouble(kmInicio.text) < kmParaDouble(kmCadastrado)
                        if (kmMenorQueCadastrado) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Warning, null, tint = AppColors.Orange, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "KM menor que o último registrado para esta placa (${util.formatarKmExibicao(kmParaDouble(kmCadastrado))}). " +
                                        "Confira antes de salvar — se estiver errado, a viagem não vai sincronizar depois.",
                                    fontSize = 12.sp,
                                    color = AppColors.Orange
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Foto do Hodômetro na Saída *",
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))

                        if (fotoImageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Image(
                                    bitmap = fotoImageBitmap!!,
                                    contentDescription = "Foto",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        fotoImageBitmap = null
                                        fotoBase64 = null
                                        // Foto removida silenciosamente
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(Color.Red, RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Remover",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { abrirCamera() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(AppColors.Primary, RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        "Nova foto",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, AppColors.Primary, RoundedCornerShape(12.dp))
                                    .background(AppColors.Primary.copy(alpha = 0.05f))
                                    .clickable { abrirCamera() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        "Tirar foto",
                                        modifier = Modifier.size(48.dp),
                                        tint = AppColors.Primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Tirar Foto",
                                        color = AppColors.Primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Rota Contínua: peso e frete não fazem sentido no início da
                        // viagem (frete vem de "Adicionar Frete" durante o trajeto) —
                        // somem da tela e deixam de ser obrigatórios.
                        if (!rotaContinua) {
                            OutlinedTextField(
                                value = pesoCarga,
                                onValueChange = { newValue ->
                                    val digits = newValue.text.filter { c -> c.isDigit() }.take(5)
                                    val formatted = formatarPeso(digits)

                                    // Atualiza com cursor sempre no final
                                    pesoCarga = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                label = { Text("Peso da Carga (kg) *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Scale, null, tint = AppColors.Primary)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { }
                                ),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                scope.launch {
                                    // Tudo o que falta numa janela só (ver mensagemCamposFaltando).
                                    // Peso da Carga não é exigido em Rota Contínua. Valor do
                                    // Frete não é mais pedido aqui em nenhum dos dois modos —
                                    // frete agora é lançado só em "Adicionar Frete" (Despesas).
                                    val faltando = buildList {
                                        if (numerobd.isBlank()) add("Ordem de Frete")
                                        if (cte.isBlank()) add("Nº do CT-e")
                                        if (rotaContinua) {
                                            if (destinoLivre.isBlank()) add("Destino")
                                        } else if (destinoSelecionado == null) add("Rota")
                                        if (placaSelecionada == null) add("Veículo (placa)")
                                        if (dataViagem.isBlank()) add("Data da viagem")
                                        if (kmInicio.text.isBlank()) add("KM de início")
                                        if (!rotaContinua && pesoCarga.text.isBlank()) add("Peso da carga")
                                        if (fotoBase64 == null) add("Foto do hodômetro na saída")
                                    }
                                    util.mensagemCamposFaltando(faltando)?.let { mostrarMensagem(it, isErro = true); return@launch }

                                    loading = true

                                    // Data de criação no formato ISO8601
                                    val isoFormatter = NSISO8601DateFormatter()
                                    val dataCriacao = isoFormatter.stringFromDate(NSDate())

                                    val dataViagemAPI = converterDataParaAPI(dataViagem)
                                    val kmInicioNormalizado = normalizarKmVeiculoParaEnvio(kmInicio.text, hodometroTemDecimal)

                                    // Rota Contínua: sem destino_id (manda destino_livre), sem
                                    // peso obrigatório. Rota Fixa: comportamento de sempre.
                                    // destinoNomeEfetivo/destinoIdLocal alimentam o cache local
                                    // (ViagemAtual/Viagem), que exige um destino_id — 0 é sentinela,
                                    // nunca usado por um destino real (IDs do servidor começam em 1).
                                    val destinoNomeEfetivo = if (rotaContinua) destinoLivre else destinoSelecionado!!.second
                                    val destinoIdLocal = if (rotaContinua) 0L else destinoSelecionado!!.first
                                    val pesoCargaParaApi = if (rotaContinua) null else pesoCarga.text

                                    try {
                                        val response = kotlinx.coroutines.withTimeoutOrNull(ESPERA_MAXIMA_INICIAR_VIAGEM_MS) { ApiClient.inserirViagem(
                                            ViagemRequest(

                                                motorista_id = motorista?.motorista_id ?: "",
                                                numerobd = numerobd,
                                                cte = cte,
                                                numerobd2 = numerobd2.ifBlank { null },
                                                cte2 = cte2.ifBlank { null },
                                                destino_id = if (rotaContinua) null else destinoSelecionado!!.first.toInt(),
                                                destino_livre = if (rotaContinua) destinoLivre else null,
                                                data_viagem = dataViagemAPI,
                                                km_inicio = kmInicioNormalizado,
                                                placa = placaSelecionada ?: "",
                                                veiculo_id = veiculoSelecionadoId?.toInt(),
                                                implemento1_id = if (implemento1Visivel) implemento1Id?.toInt() else null,
                                                implemento2_id = if (implemento2Visivel) implemento2Id?.toInt() else null,
                                                pesocarga = pesoCargaParaApi,
                                                valorfrete = null,
                                                foto_painel_saida = fotoBase64
                                            )
                                        ) } ?: throw Exception("timeout: servidor não respondeu a tempo")

                                        if (response.status == "ok") {
                                            val viagemIdReal = response.viagem_id ?: 0
                                            repository.salvarViagemAtualComComposicao(
                                                viagemId = viagemIdReal.toLong(),
                                                destino = destinoNomeEfetivo,
                                                dataInicio = dataViagemAPI,
                                                kmInicio = kmInicioNormalizado,
                                                placa = placaSelecionada ?: "",
                                                veiculoId = veiculoSelecionadoId,
                                                implemento1Placa = if (implemento1Visivel) implemento1Placa else null,
                                                implemento2Placa = if (implemento2Visivel) implemento2Placa else null
                                            )
                                            repository.salvarUltimoVeiculoUsado(placaSelecionada ?: "", veiculoSelecionadoId)
                                            rascunho.limpar()
                                            sucessoMsg = "Viagem registrada com sucesso!"
                                        } else {
                                            // API retornou erro de VALIDAÇÃO (dados inválidos, motorista não encontrado, etc.)
                                            // NÃO salvar localmente — registros com erro de validação nunca serão aceitos pelo servidor
                                            loading = false

                                            val mensagemOriginal = response.mensagem ?: ""
                                            val mensagemAmigavel = when {
                                                mensagemOriginal.contains("valorfrete", ignoreCase = true) && mensagemOriginal.contains("null", ignoreCase = true) ->
                                                    "O valor do frete não pode ser vazio. Informe um valor válido."
                                                mensagemOriginal.contains("pesocarga", ignoreCase = true) && mensagemOriginal.contains("null", ignoreCase = true) ->
                                                    "O peso da carga não pode ser vazio. Informe um valor válido."
                                                mensagemOriginal.contains("cannot be null", ignoreCase = true) || mensagemOriginal.contains("SQLSTATE", ignoreCase = true) ->
                                                    "Alguns campos obrigatórios estão vazios. Verifique todos os campos e tente novamente."
                                                mensagemOriginal.contains("motorista", ignoreCase = true) ->
                                                    "Motorista não encontrado. Faça login novamente."
                                                mensagemOriginal.isNotEmpty() -> mensagemOriginal
                                                else -> "Dados inválidos. Verifique as informações e tente novamente."
                                            }

                                            mostrarMensagem(mensagemAmigavel, isErro = true)
                                            return@launch
                                        }
                                    } catch (e: Exception) {
                                        // Exceção de REDE (sem internet, timeout, servidor indisponível)
                                        // Salvar localmente para sincronização posterior — mas só quando é
                                        // mesmo falta de conexão. Um erro técnico inesperado (bug, resposta
                                        // malformada) não deve virar silenciosamente "viagem salva offline",
                                        // senão o motorista nunca fica sabendo que algo deu errado de verdade.
                                        if (!util.isErroDeConectividade(e.message)) {
                                            loading = false
                                            if (util.isErroDeAutenticacao(e.message)) {
                                                // Sessão expirada fora do fluxo de sync em
                                                // segundo plano (que já tratava isso) —
                                                // força logout igual ao SyncManager.comRetry.
                                                AppEvents.emitir(AppEvent.TokenExpirado)
                                            }
                                            mostrarMensagem(util.mensagemErroAmigavel(e.message), isErro = true)
                                            return@launch
                                        }

                                        repository.salvarViagemComComposicao(
                                            numerobd = numerobd,
                                            cte = cte,
                                            numerobd2 = numerobd2.ifBlank { null },
                                            cte2 = cte2.ifBlank { null },
                                            destinoId = destinoIdLocal,
                                            destinoNome = destinoNomeEfetivo,
                                            placa = placaSelecionada ?: "",
                                            dataViagem = dataViagemAPI,
                                            kmInicio = kmInicioNormalizado,
                                            pesoCarga = pesoCargaParaApi ?: "",
                                            valorFrete = null,
                                            fotoPainelSaida = fotoBase64,
                                            dataCriacao = dataCriacao,
                                            veiculoId = veiculoSelecionadoId,
                                            implemento1Id = if (implemento1Visivel) implemento1Id else null,
                                            implemento1Placa = if (implemento1Visivel) implemento1Placa else null,
                                            implemento2Id = if (implemento2Visivel) implemento2Id else null,
                                            implemento2Placa = if (implemento2Visivel) implemento2Placa else null
                                        )
                                        val viagens = repository.getViagensParaSincronizar()
                                        val currentEpochMillis = (NSDate().timeIntervalSince1970 * 1000).toLong()
                                        val idLocal = viagens.lastOrNull()?.id ?: currentEpochMillis
                                        repository.salvarViagemAtualComComposicao(
                                            viagemId = -idLocal,
                                            destino = destinoNomeEfetivo,
                                            dataInicio = dataViagemAPI,
                                            kmInicio = kmInicioNormalizado,
                                            placa = placaSelecionada ?: "",
                                            veiculoId = veiculoSelecionadoId,
                                            implemento1Placa = if (implemento1Visivel) implemento1Placa else null,
                                            implemento2Placa = if (implemento2Visivel) implemento2Placa else null
                                        )
                                        repository.salvarUltimoVeiculoUsado(placaSelecionada ?: "", veiculoSelecionadoId)
                                        rascunho.limpar()
                                        sucessoMsg = "Sem internet. Viagem salva e será sincronizada automaticamente quando conectar."
                                    }

                                    loading = false
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("INICIAR VIAGEM", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ============================================
// FUNÇÕES DE FORMATAÇÃO
// ============================================

private fun formatarData(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else {
            data
        }
    } catch (e: Exception) {
        data
    }
}

private fun formatarPeso(digits: String): String {
    if (digits.isEmpty()) return ""

    // Formata com ponto como separador de milhar
    // Exemplo: "12345" → "12.345"
    return digits.reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
}

