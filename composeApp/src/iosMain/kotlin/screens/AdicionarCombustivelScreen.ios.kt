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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import platform.UIKit.*
import platform.Foundation.*
import platform.Vision.*
import platform.darwin.NSObject
import kotlinx.cinterop.*
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI
import util.formatarKmInput
import util.normalizarKmParaEnvio
import util.analisarTextoCupom
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

// Classe delegate FORA do @Composable - com tipo de foto
private class CombustivelCameraDelegate(
    private val tipoFoto: String, // "marcador" ou "cupom"
    private val onFotoCaptured: (String, String, ImageBitmap, UIImage) -> Unit, // tipo, base64, bitmap, imagem original (p/ Scanear Nota)
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
                        onFotoCaptured(tipoFoto, base64, bitmap, image)
                        // Foto capturada silenciosamente - a prévia já confirma
                    } else {
                        onMessage("Erro ao processar imagem", true)
                    }
                } else {
                    onMessage("Erro ao comprimir imagem", true)
                }
            } catch (e: Exception) {
                onMessage("Erro: ${e.message}", true)
            }
        } else {
            onMessage("Nenhuma imagem selecionada", true)
        }

        picker.dismissViewControllerAnimated(true, null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        onMessage("Captura cancelada", false)
        picker.dismissViewControllerAnimated(true, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AdicionarCombustivelScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    // Formas de pagamento e tipos de combustível (catálogo da empresa, cadastrado no admin)
    val formasPagamento = remember { repository.getAllFormasPagamento() }
    val tiposCombustivel = remember { repository.getAllTiposCombustivel() }

    var erroMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Estados
    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var modoOffline by remember { mutableStateOf(false) }

    // Viagem em andamento (pega automaticamente)
    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }

    // Equipamentos (placas)
    var equipamentos by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }

    // Carrega dados ao iniciar (LOCAL primeiro, depois API)
    LaunchedEffect(Unit) {
        carregando = true

        // Carrega equipamentos do banco local
        equipamentos = repository.getEquipamentosParaDropdown()

        // 1. Verifica viagem LOCALMENTE primeiro
        val viagemLocal = repository.getViagemAtual()
        if (viagemLocal != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemLocal.viagem_id.toInt(),
                destino = viagemLocal.destino,
                data = viagemLocal.data_inicio
            )
            modoOffline = false
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
            if (response.status == "ok" && response.viagens.isNotEmpty()) {
                val viagem = response.viagens.first()
                viagemEmAndamento = viagem
                // Salva localmente para funcionar offline depois
                repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data)
                modoOffline = false

                // Atualiza equipamentos da API
                try {
                    val syncResp = api.ApiClient.syncDados(motorista?.motorista_id ?: "")
                    if (syncResp.status == "ok") {
                        repository.salvarEquipamentos(syncResp.equipamentos.map { Triple(it.id, it.placa, it.km) })
                        equipamentos = repository.getEquipamentosParaDropdown()
                    }
                } catch (e: Exception) {
                    // Continua com equipamentos locais
                }
            } else {
                // Nenhuma viagem em andamento
                semViagemAberta = true
            }
        } catch (e: Exception) {
            // Sem internet e sem viagem local
            modoOffline = true
            semViagemAberta = true
        }
        carregando = false
    }

    // Campos do formulário
    var placaSelecionada by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var data by remember { mutableStateOf(dataAtualFormatada()) }
    var nomePosto by remember { mutableStateOf("") }
    var kmPosto by remember { mutableStateOf(TextFieldValue("")) }
    var tipoCombustivel by remember { mutableStateOf("") }
    var horas by remember { mutableStateOf("") }
    var litrosAbastecidos by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorLitro by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorTotal by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var tipoPagamento by remember { mutableStateOf("") }

    // Fotos
    var fotoMarcador by remember { mutableStateOf<ImageBitmap?>(null) }
    var fotoCupom by remember { mutableStateOf<ImageBitmap?>(null) }
    var fotoMarcadorBase64 by remember { mutableStateOf<String?>(null) }
    var fotoCupomBase64 by remember { mutableStateOf<String?>(null) }

    // Dropdowns
    var placaExpandida by remember { mutableStateOf(false) }
    var combustivelExpandido by remember { mutableStateOf(false) }

    // Função para mostrar mensagens via modal
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Scanear Nota — OCR + QR/código de barras 100% on-device (Vision framework).
    // Reaproveita a mesma foto do cupom fiscal: essa foto vale como comprovante
    // E como fonte da leitura, o motorista não precisa tirar duas fotos.
    var escaneandoNota by remember { mutableStateOf(false) }
    var aguardandoScan by remember { mutableStateOf(false) }
    var resultadoScan by remember { mutableStateOf<String?>(null) }

    fun executarScanCupom(imagem: UIImage) {
        escaneandoNota = true
        resultadoScan = null
        scope.launch(Dispatchers.Default) {
            val (texto, codigo) = runVisionScan(imagem)
            withContext(Dispatchers.Main) {
                escaneandoNota = false
                if (texto.isBlank() && codigo == null) {
                    resultadoScan = "Não conseguimos ler o cupom automaticamente. Preencha os campos manualmente."
                    return@withContext
                }
                val resultado = analisarTextoCupom(texto, qrCodeText = codigo, barcodeText = codigo)
                var achouAlgo = false
                resultado.valorDetectado?.let { valorTotal = decimalParaTextFieldValueComb(it); achouAlgo = true }
                resultado.litrosDetectado?.let { litrosAbastecidos = decimalParaTextFieldValueComb(it); achouAlgo = true }
                resultado.dataDetectada?.let { data = it; achouAlgo = true }
                resultado.postoDetectado?.let { if (nomePosto.isBlank()) { nomePosto = it; achouAlgo = true } }

                // Se achou valor total e litros, calcula o valor do litro (evita depender
                // do OCR pegar esse número específico, que costuma vir bem pequeno no cupom).
                val litrosNum = resultado.litrosDetectado?.toDoubleOrNull()
                val valorNum = resultado.valorDetectado?.toDoubleOrNull()
                if (litrosNum != null && litrosNum > 0 && valorNum != null) {
                    valorLitro = decimalParaTextFieldValueComb((valorNum / litrosNum).toString())
                }

                resultadoScan = if (achouAlgo) "Cupom lido! Confira os dados antes de salvar."
                                else "Não conseguimos identificar os dados automaticamente. Preencha manualmente."
            }
        }
    }

    // Delegates persistentes - um para cada tipo de foto
    val cameraDelegateMarcador = remember {
        CombustivelCameraDelegate(
            tipoFoto = "marcador",
            onFotoCaptured = { tipo, base64, bitmap, _ ->
                fotoMarcadorBase64 = base64
                fotoMarcador = bitmap
            },
            onMessage = { msg, erro ->
                mostrarMensagem(msg, erro)
            }
        )
    }

    val cameraDelegateCupom = remember {
        CombustivelCameraDelegate(
            tipoFoto = "cupom",
            onFotoCaptured = { tipo, base64, bitmap, uiImage ->
                fotoCupomBase64 = base64
                fotoCupom = bitmap
                if (aguardandoScan) {
                    aguardandoScan = false
                    executarScanCupom(uiImage)
                }
            },
            onMessage = { msg, erro ->
                mostrarMensagem(msg, erro)
            }
        )
    }

    // Função para abrir câmera iOS
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

        val delegate = if (tipo == "marcador") cameraDelegateMarcador else cameraDelegateCupom

        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            allowsEditing = false
            this.delegate = delegate
        }

        viewController.presentViewController(picker, true, null)
    }

    // Escolher da galeria — Android já oferece essa opção aqui, iOS só tinha câmera.
    // (O botão "Scanear Nota" continua só câmera, não passa por aqui.)
    fun escolherDaGaleria(tipo: String) {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (viewController == null) {
            mostrarMensagem("Não foi possível abrir a galeria", isErro = true)
            return
        }

        val delegate = if (tipo == "marcador") cameraDelegateMarcador else cameraDelegateCupom

        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            allowsEditing = false
            this.delegate = delegate
        }

        viewController.presentViewController(picker, true, null)
    }

    // Função salvar OFFLINE
    fun salvarLocal() {
        // Validação
        if (viagemEmAndamento == null) {
            mostrarMensagem("Nenhuma viagem em andamento", isErro = true)
            return
        }
        if (placaSelecionada == null) {
            mostrarMensagem("Selecione uma placa", isErro = true)
            return
        }
        if (nomePosto.isEmpty()) {
            mostrarMensagem("Informe o nome do posto", isErro = true)
            return
        }
        if (kmPosto.text.isEmpty()) {
            mostrarMensagem("Informe o KM no posto", isErro = true)
            return
        }
        if (tipoCombustivel.isEmpty()) {
            mostrarMensagem("Selecione o tipo de combustível", isErro = true)
            return
        }
        if (tiposCombustivel.find { it.nome == tipoCombustivel }?.requer_horas == 1L && horas.isEmpty()) {
            mostrarMensagem("Informe as horas", isErro = true)
            return
        }
        if (litrosAbastecidos.text.isEmpty()) {
            mostrarMensagem("Informe os litros abastecidos", isErro = true)
            return
        }
        if (valorLitro.text.isEmpty()) {
            mostrarMensagem("Informe o valor do litro", isErro = true)
            return
        }
        if (valorTotal.text.isEmpty()) {
            mostrarMensagem("Informe o valor total", isErro = true)
            return
        }
        if (tipoPagamento.isEmpty()) {
            mostrarMensagem("Selecione a forma de pagamento", isErro = true)
            return
        }
        if (fotoMarcadorBase64 == null) {
            mostrarMensagem("Tire a foto da quilometragem", isErro = true)
            return
        }
        if (fotoCupomBase64 == null) {
            mostrarMensagem("Tire a foto do cupom fiscal", isErro = true)
            return
        }

        scope.launch {
            salvando = true
            try {
                // Salva localmente no SQLite
                repository.salvarAbastecimento(
                    motoristaId = motorista?.motorista_id ?: "",
                    viagemId = viagemEmAndamento!!.id.toLong(),
                    equipamentoId = placaSelecionada!!.first,
                    data = converterDataParaAPI(data),
                    valor = valorTotal.text,
                    litros = litrosAbastecidos.text,
                    posto = nomePosto,
                    kmPosto = normalizarKmParaEnvio(kmPosto.text),
                    foto = fotoCupomBase64,
                    fotoMarcador = fotoMarcadorBase64,
                    tipoPagamento = tipoPagamento,
                    tipoCombustivel = tipoCombustivel,
                    horas = horas.ifEmpty { null },
                    valorLitro = valorLitro.text
                )

                mostrarMensagem("Abastecimento salvo! Sincronize quando tiver internet.")

            } catch (e: Exception) {
                mostrarMensagem("Erro ao salvar: ${e.message}", isErro = true)
            }
            salvando = false
        }
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Adicionar Combustível",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
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
                    Text("Carregando dados...", color = AppColors.TextSecondary)
                }
            }
        } else {
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
                // Aviso offline - SÓ APARECE QUANDO ESTÁ OFFLINE
                if (modoOffline) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Sem conexão. Conecte para registrar abastecimento.",
                                color = AppColors.Orange,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Mensagem se não tiver viagem em andamento
                if (semViagemAberta) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = AppColors.Error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Nenhuma viagem em andamento",
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Error,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Inicie uma viagem primeiro para registrar abastecimento.",
                                color = AppColors.TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onVoltar,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                Text("Voltar")
                            }
                        }
                    }
                } else {
                    // Card da viagem em andamento
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = AppColors.Primary.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                null,
                                tint = AppColors.Primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Viagem em andamento",
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                                Text(
                                    viagemEmAndamento?.destino ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = AppColors.TextPrimary
                                )
                                Text(
                                    "Iniciada em: ${viagemEmAndamento?.data ?: ""}",
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                            // Scanear Nota — OCR + QR/código de barras, preenche os campos abaixo
                            Button(
                                onClick = {
                                    aguardandoScan = true
                                    abrirCamera("cupom")
                                },
                                enabled = !escaneandoNota,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                if (escaneandoNota) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Lendo cupom...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.DocumentScanner, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("SCANEAR NOTA", fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                "Tire uma foto do cupom fiscal e a gente tenta preencher os campos abaixo. Sempre confira antes de salvar.",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            resultadoScan?.let { msg ->
                                Spacer(Modifier.height(8.dp))
                                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Primary.copy(alpha = 0.1f))) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(msg, fontSize = 13.sp, color = AppColors.TextPrimary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            // Data
                            DateInputField(
                                value = data,
                                onValueChange = { data = it },
                                label = "Data *",
                                primaryColor = AppColors.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            // Placa
                            Text(
                                "Placa *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            ui.AppDropdownField(
                                label = "Placa",
                                selectedText = placaSelecionada?.second ?: "",
                                expanded = placaExpandida,
                                onExpandedChange = {
                                    focusManager.clearFocus()
                                    placaExpandida = it
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                equipamentos.forEach { (id, placa) ->
                                    ui.AppDropdownMenuItem(
                                        text = { Text(placa) },
                                        onClick = {
                                            placaSelecionada = id to placa
                                            placaExpandida = false
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Foto Quilometragem
                            Text(
                                "Foto Quilometragem *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            FotoCapturaAbastecimento(
                                foto = fotoMarcador,
                                onClick = { abrirCamera("marcador") },
                                onEscolherGaleria = { escolherDaGaleria("marcador") },
                                onRemover = {
                                    fotoMarcador = null
                                    fotoMarcadorBase64 = null
                                    // Foto removida silenciosamente
                                }
                            )

                            Spacer(Modifier.height(16.dp))

                            // Nome do Posto
                            Text(
                                "Nome do Posto *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nomePosto,
                                onValueChange = { nomePosto = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(Icons.Default.Business, null, tint = AppColors.Primary)
                                }
                            )

                            Spacer(Modifier.height(16.dp))

                            // KM no Posto
                            Text(
                                "KM no Posto *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = kmPosto,
                                onValueChange = { newValue ->
                                    val formatted = formatarKmInput(newValue.text)
                                    kmPosto = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                placeholder = { Text("Ex.: 115670.5") },
                                leadingIcon = {
                                    Icon(Icons.Default.Speed, null, tint = AppColors.Primary)
                                }
                            )
                            Text(
                                "Digite como aparece no painel. Ex.: 115670.5",
                                fontSize = 12.sp,
                                color = AppColors.Primary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )

                            Spacer(Modifier.height(16.dp))

                            // Tipo de Combustível
                            Text(
                                "Tipo de Combustível *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            ui.AppDropdownField(
                                label = "Tipo de Combustível",
                                selectedText = tipoCombustivel,
                                expanded = combustivelExpandido,
                                onExpandedChange = {
                                    focusManager.clearFocus()
                                    combustivelExpandido = it
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LocalGasStation, null, tint = AppColors.Primary)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                tiposCombustivel.forEach { tc ->
                                    ui.AppDropdownMenuItem(
                                        text = { Text(tc.nome) },
                                        onClick = {
                                            tipoCombustivel = tc.nome
                                            combustivelExpandido = false
                                        }
                                    )
                                }
                            }

                            // Campo Horas (só para tipos que exigem horas)
                            if (tiposCombustivel.find { it.nome == tipoCombustivel }?.requer_horas == 1L) {
                                Spacer(Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = AppColors.Background)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Horas *",
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.TextPrimary
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = horas,
                                            onValueChange = {
                                                if (it.length <= 5) horas = it.filter { c -> c.isDigit() }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            leadingIcon = {
                                                Icon(Icons.Default.Schedule, null, tint = AppColors.Primary)
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Litros
                            Text(
                                "Litros Abastecidos *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = litrosAbastecidos,
                                onValueChange = { newValue ->
                                    val formatted = formatarDecimalComb(newValue.text)
                                    litrosAbastecidos = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(Icons.Default.WaterDrop, null, tint = AppColors.Primary)
                                },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                            

                            Spacer(Modifier.height(16.dp))

                            // Valor do Litro
                            Text(
                                "Valor do Litro *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = valorLitro,
                                onValueChange = { newValue ->
                                    val formatted = formatarValorLitroComb(newValue.text)
                                    valorLitro = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(Icons.Default.AttachMoney, null, tint = AppColors.Primary)
                                },
                                prefix = { Text("R$ ") },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                            

                            Spacer(Modifier.height(16.dp))

                            // Valor Total
                            Text(
                                "Valor Total *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = valorTotal,
                                onValueChange = { newValue ->
                                    val formatted = formatarMoedaComb(newValue.text)
                                    valorTotal = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(Icons.Default.Calculate, null, tint = AppColors.Primary)
                                },
                                prefix = { Text("R$ ") },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                            

                            Spacer(Modifier.height(16.dp))

                            // Forma de Pagamento
                            Text(
                                "Forma de Pagamento *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                formasPagamento.forEach { fp ->
                                    BotaoOpcaoComb(
                                        fp.nome,
                                        Icons.Default.CreditCard,
                                        tipoPagamento == fp.codigo,
                                        { tipoPagamento = fp.codigo },
                                        Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Foto Cupom Fiscal
                            Text(
                                "Foto Cupom Fiscal *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            FotoCapturaAbastecimento(
                                foto = fotoCupom,
                                onClick = { abrirCamera("cupom") },
                                onEscolherGaleria = { escolherDaGaleria("cupom") },
                                onRemover = {
                                    fotoCupom = null
                                    fotoCupomBase64 = null
                                    // Foto removida silenciosamente
                                }
                            )

                            Spacer(Modifier.height(24.dp))

                            // Botão Salvar
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    salvarLocal()
                                },
                                enabled = !salvando,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                if (salvando) {
                                    CircularProgressIndicator(
                                        Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Save, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "SALVAR ABASTECIMENTO",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun FotoCapturaAbastecimento(
    foto: ImageBitmap?,
    onClick: () -> Unit,
    onEscolherGaleria: () -> Unit,
    onRemover: () -> Unit
) {
    if (foto != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = foto,
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
                Icon(
                    Icons.Default.Close,
                    "Remover",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, AppColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onClick() },
                color = AppColors.Primary.copy(alpha = 0.05f)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Tirar Foto", color = AppColors.Primary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF1976D2).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onEscolherGaleria() },
                color = Color(0xFF1976D2).copy(alpha = 0.05f)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF1976D2), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Da Galeria", color = Color(0xFF1976D2), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BotaoOpcaoComb(
    texto: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (selecionado) AppColors.Primary else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            2.dp,
            if (selecionado) AppColors.Primary else AppColors.TextSecondary.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icone,
                null,
                tint = if (selecionado) Color.White else AppColors.TextSecondary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                texto,
                color = if (selecionado) Color.White else AppColors.TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatarDecimalComb(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val inteiros = value / 100
    val decimais = value % 100
    val inteirosFormatado = inteiros.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$inteirosFormatado,${decimais.toString().padStart(2, '0')}"
}

private fun formatarValorLitroComb(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val reais = value / 100
    val centavos = value % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}

private fun formatarMoedaComb(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val reais = value / 100
    val centavos = value % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}

/** Converte decimal puro ("123.45", vindo do scan) pro TextFieldValue já formatado em BR (mesmo padrão de formatarMoedaComb). */
private fun decimalParaTextFieldValueComb(valorDecimal: String): TextFieldValue {
    val valor = valorDecimal.toDoubleOrNull() ?: return TextFieldValue("")
    val centavosTotais = kotlin.math.round(valor * 100).toLong()
    if (centavosTotais < 0) return TextFieldValue("")
    val reais = centavosTotais / 100
    val centavos = centavosTotais % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    val formatted = "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
    return TextFieldValue(formatted, selection = TextRange(formatted.length))
}

/**
 * Roda OCR (VNRecognizeTextRequest) e leitura de QR/código de barras
 * (VNDetectBarcodesRequest) na foto do cupom, 100% on-device via Vision
 * framework — sem depender de nenhum serviço externo.
 *
 * Chamar sempre fora da main thread (função não é @Composable-safe pra UI).
 */
@OptIn(ExperimentalForeignApi::class)
private fun runVisionScan(imagem: UIImage): Pair<String, String?> {
    val cgImage = imagem.CGImage ?: return Pair("", null)
    val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())

    var textoDetectado = ""
    val textRequest = VNRecognizeTextRequest { request, _ ->
        val resultados = (request as? VNRecognizeTextRequest)?.results as? List<*>
        textoDetectado = resultados
            ?.filterIsInstance<VNRecognizedTextObservation>()
            ?.mapNotNull { obs -> (obs.topCandidates(1UL).firstOrNull() as? VNRecognizedText)?.string }
            ?.joinToString("\n")
            ?: ""
    }
    textRequest.recognitionLevel = VNRequestTextRecognitionLevelAccurate
    textRequest.usesLanguageCorrection = true
    textRequest.recognitionLanguages = listOf("pt-BR")

    var codigoDetectado: String? = null
    val barcodeRequest = VNDetectBarcodesRequest { request, _ ->
        val resultados = (request as? VNDetectBarcodesRequest)?.results as? List<*>
        codigoDetectado = resultados
            ?.filterIsInstance<VNBarcodeObservation>()
            ?.firstOrNull()
            ?.payloadStringValue
    }

    try {
        // error=null: não precisamos do NSError detalhado — se a leitura falhar
        // (foto ruim, sem texto reconhecível etc.), os requests simplesmente não
        // preenchem textoDetectado/codigoDetectado e o chamador trata isso normal.
        handler.performRequests(listOf(textRequest, barcodeRequest), null)
    } catch (e: Exception) {
        // Leitura falhou — segue com o que já tiver sido preenchido (provavelmente nada).
    }

    return Pair(textoDetectado, codigoDetectado)
}