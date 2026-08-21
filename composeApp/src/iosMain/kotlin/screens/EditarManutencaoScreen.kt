package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import api.AtualizarManutencaoRequest
import database.AppRepository
import kotlinx.coroutines.launch
import platform.UIKit.*
import platform.darwin.NSObject
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI
import util.converterDataParaExibicao
import util.formatarKmInput
import util.normalizarKmParaEnvio
import util.formatarKmExibicao
import kotlin.math.roundToLong

// Mesma lista do Android (EditarManutencaoScreen.android.kt / ManutencaoScreen.ios.kt).
private val SERVICOS_EDIT = listOf("Troca de Óleo", "Troca de Pneu", "Borracharia (Remendo)", "Elétrica", "Mecânica", "Suspensão", "Aparelhos", "Outros")
private val TIPOS_PNEU_OPCOES_EDIT = listOf("Novo", "Recapado", "Usado")

// Delegate da câmera nativa iOS - fora do @Composable - suporta os dois comprovantes
private class EditarManutencaoCameraDelegate(
    private val tipoFoto: String,
    private val onFotoCaptured: (String, String) -> Unit, // tipo, base64
    private val onMessage: (String, Boolean) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image != null) {
            val base64 = CameraHelper.imageToBase64(image)
            if (base64 != null) {
                onFotoCaptured(tipoFoto, base64)
            } else {
                onMessage("Erro ao converter imagem para base64", true)
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

@Composable
private fun FotoCapturaManutencaoEdit(
    bitmap: ImageBitmap?,
    onRemover: () -> Unit,
    onCapturar: () -> Unit
) {
    if (bitmap != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onRemover,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .background(AppColors.Error, RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = onCapturar,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .background(Color(0xFF06B6D4), RoundedCornerShape(50))
            ) {
                Icon(Icons.Default.CameraAlt, "Nova foto", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF06B6D4).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .background(Color(0xFF06B6D4).copy(alpha = 0.05f))
                .clickable { onCapturar() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(40.dp), tint = Color(0xFF06B6D4))
                Spacer(Modifier.height(8.dp))
                Text("Tirar Foto", color = Color(0xFF06B6D4), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarManutencaoScreen(
    repository: AppRepository,
    manutencaoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val equipamentos = remember { repository.getEquipamentosParaDropdown() }
    var carregando by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    var modoOffline by remember { mutableStateOf(false) }

    var dataManutencao by remember { mutableStateOf(dataAtualFormatada()) }
    var placa by remember { mutableStateOf("") }
    var servico by remember { mutableStateOf("") }
    var tipoManutencao by remember { mutableStateOf("preventiva") }
    var descricaoServico by remember { mutableStateOf("") }
    var localManutencao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var kmTrocaOleo by remember { mutableStateOf(TextFieldValue("")) }
    var kmTrocaPneu by remember { mutableStateOf(TextFieldValue("")) }
    // Antes eram String livre (placa não editável, serviço texto livre, sem UI
    // de pneus) — agora os mesmos tipos usados em ManutencaoScreen.ios.kt e no
    // Android, pra ter a mesma edição completa nas duas plataformas.
    var pneusSelecionados by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var tiposPneu by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var placaExpanded by remember { mutableStateOf(false) }
    var servicoExpanded by remember { mutableStateOf(false) }
    var fotoComprovante1Base64 by remember { mutableStateOf<String?>(null) }
    var fotoComprovante2Base64 by remember { mutableStateOf<String?>(null) }
    val fotoComprovante1Bitmap = remember(fotoComprovante1Base64) { fotoComprovante1Base64?.let { CameraHelper.base64ToImageBitmap(it) } }
    val fotoComprovante2Bitmap = remember(fotoComprovante2Base64) { fotoComprovante2Base64?.let { CameraHelper.base64ToImageBitmap(it) } }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Delegates persistentes da câmera - um para cada comprovante
    val cameraDelegate1 = remember {
        EditarManutencaoCameraDelegate(
            tipoFoto = "comprovante1",
            onFotoCaptured = { _, base64 -> fotoComprovante1Base64 = base64 },
            onMessage = { msg, erro -> mostrarMensagem(msg, erro) }
        )
    }
    val cameraDelegate2 = remember {
        EditarManutencaoCameraDelegate(
            tipoFoto = "comprovante2",
            onFotoCaptured = { _, base64 -> fotoComprovante2Base64 = base64 },
            onMessage = { msg, erro -> mostrarMensagem(msg, erro) }
        )
    }

    // Função para abrir a câmera nativa iOS
    fun abrirCamera(tipo: String) {
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
        val delegate = if (tipo == "comprovante1") cameraDelegate1 else cameraDelegate2
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            allowsEditing = false
            this.delegate = delegate
        }
        viewController.presentViewController(picker, true, null)
    }

    LaunchedEffect(manutencaoId) {
        carregando = true
        try {
            val response = ApiClient.buscarManutencao(
                api.BuscarManutencaoRequest(
                    manutencao_id = manutencaoId,
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )
            if (response.status == "ok" && response.manutencao != null) {
                val manutencao = response.manutencao
                dataManutencao = converterDataParaExibicao(manutencao.data_manutencao)
                placa = manutencao.placa
                servico = manutencao.servico
                tipoManutencao = manutencao.tipo_manutencao
                descricaoServico = manutencao.descricao_servico ?: ""
                localManutencao = manutencao.local_manutencao ?: ""
                val valorFormatado = formatarValor(((manutencao.valor.toDoubleOrNull() ?: 0.0) * 100).roundToLong().toString())
                valor = TextFieldValue(valorFormatado, selection = TextRange(0, valorFormatado.length))
                val kmTrocaOleoRaw = manutencao.km_troca_oleo ?: ""
                val kmTrocaOleoTexto = kmTrocaOleoRaw.toDoubleOrNull()?.let { formatarKmExibicao(it) } ?: kmTrocaOleoRaw
                kmTrocaOleo = TextFieldValue(kmTrocaOleoTexto, selection = TextRange(0, kmTrocaOleoTexto.length))
                val kmTrocaPneuRaw = manutencao.km_troca_pneu ?: ""
                val kmTrocaPneuTexto = kmTrocaPneuRaw.toDoubleOrNull()?.let { formatarKmExibicao(it) } ?: kmTrocaPneuRaw
                kmTrocaPneu = TextFieldValue(kmTrocaPneuTexto, selection = TextRange(0, kmTrocaPneuTexto.length))
                // Pneus e tipos vêm como string ("1,2,3" e "1:Novo;2:Recapado") —
                // mesmo parsing usado no Android (EditarManutencaoScreen.android.kt).
                if (!manutencao.pneus.isNullOrEmpty()) {
                    pneusSelecionados = manutencao.pneus.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                }
                if (!manutencao.tipos_pneu.isNullOrEmpty()) {
                    tiposPneu = manutencao.tipos_pneu.split(";").mapNotNull { parte ->
                        val partes = parte.split(":")
                        if (partes.size == 2) partes[0].trim().toIntOrNull()?.let { it to partes[1].trim() } else null
                    }.toMap()
                }
                fotoComprovante1Base64 = manutencao.foto_comprovante1
                fotoComprovante2Base64 = manutencao.foto_comprovante2
                modoOffline = false
            } else {
                mostrarMensagem(response.mensagem ?: "Manutenção não encontrada", isErro = true)
            }
        } catch (e: Exception) {
            modoOffline = true
            mostrarMensagem("Erro ao carregar dados: ${e.message}", isErro = true)
        }
        carregando = false
    }

    // Diálogos modais
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar Manutenção",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF06B6D4))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        focusManager.clearFocus()
                    })
                }.verticalScroll(scrollState).padding(16.dp)
            ) {
                // Aviso offline — Editar Combustível/ARLA/Descarga já tinham, Manutenção não.
                if (modoOffline) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text("Sem conexão. Conecte para editar manutenção.", color = AppColors.Orange, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Informações da Manutenção", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(20.dp))

                        DateInputField(
                            value = dataManutencao,
                            onValueChange = { dataManutencao = it },
                            label = "Data da Manutenção",
                            primaryColor = Color(0xFF06B6D4),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        // Antes travada (enabled = false) — Android sempre permitiu trocar a placa aqui.
                        ui.AppDropdownField(
                            label = "Placa *",
                            selectedText = placa,
                            expanded = placaExpanded,
                            onExpandedChange = { placaExpanded = it },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            equipamentos.forEach { (_, p) ->
                                ui.AppDropdownMenuItem(text = { Text(p) }, onClick = { placa = p; placaExpanded = false })
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Antes era texto livre — Android sempre usou uma lista fechada (mesmas
                        // 8 opções de Adicionar Manutenção), permitindo qualquer string aqui.
                        ui.AppDropdownField(
                            label = "Serviço *",
                            selectedText = servico,
                            expanded = servicoExpanded,
                            onExpandedChange = { servicoExpanded = it },
                            leadingIcon = { Icon(Icons.Default.Build, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SERVICOS_EDIT.forEach { s ->
                                ui.AppDropdownMenuItem(text = { Text(s) }, onClick = {
                                    servico = s
                                    servicoExpanded = false
                                    if (s != "Troca de Pneu") {
                                        pneusSelecionados = emptySet()
                                        tiposPneu = emptyMap()
                                    }
                                })
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Preventiva x Corretiva — planejada ou reparo/quebra.
                        Text("Tipo *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { tipoManutencao = "preventiva" },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tipoManutencao == "preventiva") Color(0xFF10B981) else Color(0xFFE5E7EB)
                                )
                            ) {
                                Icon(
                                    Icons.Default.EventAvailable,
                                    null,
                                    tint = if (tipoManutencao == "preventiva") Color.White else AppColors.TextSecondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Preventiva",
                                    color = if (tipoManutencao == "preventiva") Color.White else AppColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = { tipoManutencao = "corretiva" },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tipoManutencao == "corretiva") Color(0xFFEF4444) else Color(0xFFE5E7EB)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    tint = if (tipoManutencao == "corretiva") Color.White else AppColors.TextSecondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Corretiva",
                                    color = if (tipoManutencao == "corretiva") Color.White else AppColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = descricaoServico,
                            onValueChange = { descricaoServico = it },
                            label = { Text("Descrição do Serviço") },
                            leadingIcon = { Icon(Icons.Default.Description, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            maxLines = 5
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = localManutencao,
                            onValueChange = { localManutencao = it },
                            label = { Text("Local da Manutenção") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valor,
                            onValueChange = { newValue ->
                                val digits = newValue.text.filter { c -> c.isDigit() }.take(7)
                                val formatted = formatarValor(digits)
                                valor = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                            },
                            label = { Text("Valor (R\$) *") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))

                        if (servico == "Troca de Óleo") {
                            OutlinedTextField(
                                value = kmTrocaOleo,
                                onValueChange = { newValue ->
                                    val formatted = formatarKmInput(newValue.text)
                                    kmTrocaOleo = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                label = { Text("KM Troca de Óleo *") },
                                placeholder = { Text("Ex.: 115670.5") },
                                leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF06B6D4)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            Text(
                                "Digite como aparece no painel. Ex.: 115670.5",
                                fontSize = 12.sp,
                                color = AppColors.Primary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                        }

                        if (servico == "Troca de Pneu") {
                            OutlinedTextField(
                                value = kmTrocaPneu,
                                onValueChange = { newValue ->
                                    val formatted = formatarKmInput(newValue.text)
                                    kmTrocaPneu = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                label = { Text("KM Troca de Pneu *") },
                                placeholder = { Text("Ex.: 115670.5") },
                                leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF06B6D4)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true
                            )
                            Text(
                                "Digite como aparece no painel. Ex.: 115670.5",
                                fontSize = 12.sp,
                                color = AppColors.Primary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )

                            Spacer(Modifier.height(20.dp))
                            HorizontalDivider(color = AppColors.Background)
                            Spacer(Modifier.height(16.dp))
                            Text("Selecione os Pneus", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(16.dp))
                            ChassiPneusEditIos(
                                pneusSelecionados = pneusSelecionados,
                                onPneuToggle = { pneu ->
                                    pneusSelecionados = if (pneu in pneusSelecionados) pneusSelecionados - pneu else pneusSelecionados + pneu
                                }
                            )
                            if (pneusSelecionados.isNotEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                HorizontalDivider(color = AppColors.Background)
                                Spacer(Modifier.height(16.dp))
                                Text("Tipo de cada Pneu", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                                Spacer(Modifier.height(12.dp))
                                pneusSelecionados.sorted().forEach { pneu ->
                                    TipoPneuSelectorEditIos(
                                        pneuNumero = pneu,
                                        tipoSelecionado = tiposPneu[pneu] ?: "",
                                        opcoes = TIPOS_PNEU_OPCOES_EDIT,
                                        onTipoChange = { tipo -> tiposPneu = tiposPneu + (pneu to tipo) }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        Spacer(Modifier.height(16.dp))

                        Text("Comprovante 1", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaManutencaoEdit(
                            bitmap = fotoComprovante1Bitmap,
                            onRemover = { fotoComprovante1Base64 = null },
                            onCapturar = { abrirCamera("comprovante1") }
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Comprovante 2", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaManutencaoEdit(
                            bitmap = fotoComprovante2Bitmap,
                            onRemover = { fotoComprovante2Base64 = null },
                            onCapturar = { abrirCamera("comprovante2") }
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    if (dataManutencao.isBlank()) { mostrarMensagem("Informe a data", isErro = true); return@launch }
                                    if (servico.isBlank()) { mostrarMensagem("Informe o serviço", isErro = true); return@launch }
                                    if (valor.text.isBlank()) { mostrarMensagem("Informe o valor", isErro = true); return@launch }
                                    if (fotoComprovante1Base64.isNullOrBlank()) { mostrarMensagem("Tire a foto do cupom fiscal", isErro = true); return@launch }
                                    if (servico == "Troca de Óleo" && kmTrocaOleo.text.isBlank()) { mostrarMensagem("Informe o KM da troca de óleo", isErro = true); return@launch }
                                    if (servico == "Troca de Pneu") {
                                        if (kmTrocaPneu.text.isBlank()) { mostrarMensagem("Informe o KM da troca de pneu", isErro = true); return@launch }
                                        if (pneusSelecionados.isEmpty()) { mostrarMensagem("Selecione pelo menos um pneu", isErro = true); return@launch }
                                        for (pneu in pneusSelecionados) {
                                            if (tiposPneu[pneu].isNullOrBlank()) { mostrarMensagem("Selecione o tipo para o pneu $pneu", isErro = true); return@launch }
                                        }
                                    }

                                    salvando = true
                                    try {
                                        val response = ApiClient.atualizarManutencao(
                                            AtualizarManutencaoRequest(
                                                manutencao_id = manutencaoId,
                                                motorista_id = motorista?.motorista_id ?: "",
                                                viagem_id = viagemId,
                                                data_manutencao = converterDataParaAPI(dataManutencao),
                                                placa = placa,
                                                servico = servico,
                                                tipo_manutencao = tipoManutencao,
                                                descricao_servico = descricaoServico.ifBlank { null },
                                                local_manutencao = localManutencao.ifBlank { null },
                                                valor = valor.text,
                                                km_troca_oleo = kmTrocaOleo.text.ifBlank { null }?.let { normalizarKmParaEnvio(it) },
                                                km_troca_pneu = kmTrocaPneu.text.ifBlank { null }?.let { normalizarKmParaEnvio(it) },
                                                pneus = pneusSelecionados.toList(),
                                                tipos_pneu = tiposPneu,
                                                foto_comprovante1 = fotoComprovante1Base64,
                                                foto_comprovante2 = fotoComprovante2Base64
                                            )
                                        )
                                        if (response.status == "ok") {
                                            sucessoMsg = "Manutenção atualizada com sucesso!"
                                        } else {
                                            mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                                        }
                                    } catch (e: Exception) {
                                        mostrarMensagem("Erro: ${e.message}", isErro = true)
                                    }
                                    salvando = false
                                }
                            },
                            enabled = !salvando && !modoOffline,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("SALVAR ALTERAÇÕES", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ===============================
// CHASSI DE PNEUS (mesmo componente de ManutencaoScreen.ios.kt / Android,
// duplicado aqui — cada tela mantém sua própria cópia, mesmo padrão já usado
// no Android entre ManutencaoScreen e EditarManutencaoScreen).
// ===============================
@Composable
private fun ChassiPneusEditIos(pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(8.dp).height(4.dp).background(Color(0xFF374151), RoundedCornerShape(4.dp)))

        Text("Eixo Dianteiro", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusEditIos(listOf(1, 2), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 2", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusEditIos(listOf(3, 4), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 3 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEditIos(listOf(5, 6, 7, 8), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 4 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEditIos(listOf(9, 10, 11, 12), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(4.dp).height(30.dp).background(Color(0xFF9CA3AF)))
        Text("── Carreta ──", fontSize = 10.sp, color = AppColors.TextSecondary)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        Text("Eixo 5", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEditIos(listOf(13, 14, 15, 16), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 6", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEditIos(listOf(17, 18, 19, 20), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 7", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEditIos(listOf(21, 22, 23, 24), pneusSelecionados, onPneuToggle)
    }
}

@Composable
private fun EixoPneusEditIos(pneus: List<Int>, pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Box(modifier = Modifier.width(100.dp).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
            PneuCheckboxEditIos(pneus[0], pneus[0] in pneusSelecionados) { onPneuToggle(pneus[0]) }
        }
        Box(modifier = Modifier.weight(1f).height(12.dp).background(Color(0xFF4B5563), RoundedCornerShape(6.dp)))
        Box(modifier = Modifier.width(100.dp).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
            PneuCheckboxEditIos(pneus[1], pneus[1] in pneusSelecionados) { onPneuToggle(pneus[1]) }
        }
    }
}

@Composable
private fun EixoPneusDuploEditIos(pneus: List<Int>, pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PneuCheckboxEditIos(pneus[0], pneus[0] in pneusSelecionados) { onPneuToggle(pneus[0]) }
            PneuCheckboxEditIos(pneus[1], pneus[1] in pneusSelecionados) { onPneuToggle(pneus[1]) }
        }
        Box(modifier = Modifier.weight(1f).height(12.dp).background(Color(0xFF4B5563), RoundedCornerShape(6.dp)))
        Row(modifier = Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PneuCheckboxEditIos(pneus[2], pneus[2] in pneusSelecionados) { onPneuToggle(pneus[2]) }
            PneuCheckboxEditIos(pneus[3], pneus[3] in pneusSelecionados) { onPneuToggle(pneus[3]) }
        }
    }
}

@Composable
private fun PneuCheckboxEditIos(numero: Int, selecionado: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(32.dp).height(52.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selecionado) Color(0xFF3B82F6) else Color(0xFF1F2937))
            .border(2.dp, if (selecionado) Color(0xFF1D4ED8) else Color(0xFF374151), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(numero.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipoPneuSelectorEditIos(pneuNumero: Int, tipoSelecionado: String, opcoes: List<String>, onTipoChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Pneu $pneuNumero:", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
        ui.AppDropdownField(
            label = "",
            selectedText = if (tipoSelecionado.isBlank()) "Selecione..." else tipoSelecionado,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            opcoes.forEach { opcao ->
                ui.AppDropdownMenuItem(text = { Text(opcao) }, onClick = { onTipoChange(opcao); expanded = false })
            }
        }
    }
}

private fun formatarValor(digits: String): String {
    if (digits.isEmpty()) return ""
    val valor = digits.toLongOrNull() ?: return ""
    val reais = valor / 100
    val centavos = valor % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}
