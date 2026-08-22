package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.*
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

private fun formatarValorDescarga(valor: String): String {
    val apenasNumeros = valor.replace(Regex("[^0-9]"), "")
    if (apenasNumeros.isEmpty()) return ""
    val numero = apenasNumeros.toLongOrNull() ?: 0L
    val reais = numero / 100
    val centavos = numero % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}

// Classe delegate FORA do @Composable
private class AdicionarDescargaCameraDelegate(
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
                onMessage("Erro: ${e.message}", true)
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
actual fun AdicionarDescargaScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Cores
    val primaryColor = Color(0xFF1E88E5)
    val backgroundColor = Color(0xFFF5F7FA)
    val cardColor = Color.White
    val errorColor = Color(0xFFEF4444)
    val inputBackground = Color(0xFFF9FAFB)
    val borderColor = Color(0xFFE5E7EB)
    val labelColor = Color(0xFF374151)
    val placeholderColor = Color(0xFF9CA3AF)

    // Estados básicos
    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }

    // Dados carregados
    var motorista by remember { mutableStateOf<br.com.lfsystem.app.database.Motorista?>(null) }
    var viagemAtual by remember { mutableStateOf<br.com.lfsystem.app.database.ViagemAtual?>(null) }

    // Placa não é mais escolhida aqui — é sempre a do veículo da viagem ativa.
    val placaVeiculo = viagemAtual?.placa ?: ""

    // Campos do formulário
    var data by remember { mutableStateOf(dataAtualFormatada()) }
    var ordemDescarga by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var fotoBase64 by remember { mutableStateOf<String?>(null) }
    var fotoImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Carregar dados de forma segura
    LaunchedEffect(Unit) {
        try {
            motorista = repository.getMotoristaLogado()
            viagemAtual = repository.getViagemAtual()

            // Tentar carregar a viagem (se não houver localmente) da API
            motorista?.let { m ->
                try {
                    val response = ApiClient.abastecimentoDados(
                        AbastecimentoDadosRequest(

                            motorista_id = m.motorista_id
                        )
                    )
                    if (response.status == "ok") {
                        // Se não havia viagem em andamento localmente, tenta achar via API
                        // (evita bloquear o usuário quando o cache local de ViagemAtual está vazio)
                        if (viagemAtual == null && response.viagens.isNotEmpty()) {
                            val viagem = response.viagens.first()
                            repository.salvarViagemAtualComComposicao(
                                viagemId = viagem.id.toLong(),
                                destino = viagem.destino,
                                dataInicio = viagem.data,
                                placa = viagem.placa,
                                veiculoId = viagem.veiculo_id?.toLong(),
                                implemento1Placa = viagem.implemento1_placa,
                                implemento2Placa = viagem.implemento2_placa
                            )
                            viagemAtual = repository.getViagemAtual()
                        }
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            mostrarMensagem("Erro ao carregar dados", isErro = true)
        } finally {
            carregando = false
        }
    }

    // Delegate persistente - criado uma vez e mantido
    val cameraDelegate = remember {
        AdicionarDescargaCameraDelegate(
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

    // Escolher da galeria — Android já oferece essa opção aqui, iOS só tinha câmera.
    fun escolherDaGaleria() {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (viewController == null) {
            mostrarMensagem("Não foi possível abrir a galeria", isErro = true)
            return
        }

        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            allowsEditing = false
            delegate = cameraDelegate
        }

        viewController.presentViewController(picker, true, null)
    }

    fun salvarDescarga() {
        val viagem = viagemAtual
        val mot = motorista

        if (viagem == null) {
            mostrarMensagem("Nenhuma viagem em andamento", isErro = true)
            return
        }

        if (mot == null) {
            mostrarMensagem("Erro ao carregar dados do motorista", isErro = true)
            return
        }

        if (data.isEmpty()) {
            mostrarMensagem("Informe a data", isErro = true)
            return
        }
        if (ordemDescarga.isBlank()) {
            mostrarMensagem("Informe a ordem de descarga", isErro = true)
            return
        }
        val ordemDescargaInt = ordemDescarga.toIntOrNull()
        if (ordemDescargaInt == null || ordemDescargaInt <= 0) {
            mostrarMensagem("Nº Ordem de Descarga inválido. Valor informado excede o limite permitido (máx. 2.147.483.647).", isErro = true)
            return
        }
        if (valor.text.isBlank()) {
            mostrarMensagem("Informe o valor", isErro = true)
            return
        }
        if (fotoBase64 == null) {
            mostrarMensagem("Tire uma foto do comprovante", isErro = true)
            return
        }

        val dataAPI = converterDataParaAPI(data)

        salvando = true

        scope.launch {
            try {
                // PRIMEIRO: Tentar API
                try {
                    val response = ApiClient.salvarDescarga(
                        SalvarDescargaRequest(

                            motorista_id = mot.motorista_id,
                            viagem_id = viagem.viagem_id.toInt(),
                            data = dataAPI,
                            placa = placaVeiculo,
                            ordem_descarga = ordemDescargaInt,
                            valor = valor.text,
                            foto = fotoBase64
                        )
                    )
                    if (response.status == "ok") {
                        // API salvou com sucesso
                        sucessoMsg = "Descarga registrada com sucesso!"
                    } else {
                        mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                    }
                } catch (e: Exception) {
                    // Sem internet - salvar localmente para sincronização
                    repository.salvarDescarga(
                        motoristaId = mot.motorista_id,
                        viagemId = viagem.viagem_id,
                        data = dataAPI,
                        placa = placaVeiculo,
                        ordemDescarga = ordemDescargaInt.toLong(),
                        valor = valor.text,
                        foto = fotoBase64
                    )
                    sucessoMsg = "Descarga salva! Sincronize quando tiver internet."
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro ao salvar: ${e.message}", isErro = true)
            } finally {
                salvando = false
            }
        }
    }

    // ========== TELA DE CARREGAMENTO ==========
    if (carregando) {
        Box(
            modifier = Modifier.fillMaxSize().background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = primaryColor)
        }
        return
    }

    // ========== SEM VIAGEM ==========
    if (viagemAtual == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(AppColors.Orange.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = AppColors.Orange,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Nenhuma viagem em andamento",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Inicie uma viagem primeiro",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = onVoltar,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Voltar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        return
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    // ========== FORMULÁRIO ==========
    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Adicionar Descarga",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                .verticalScroll(scrollState)
        ) {
            // Info da viagem
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryColor.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(primaryColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            null,
                            tint = primaryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Descarga na viagem",
                            fontSize = 12.sp,
                            color = primaryColor.copy(alpha = 0.7f)
                        )
                        Text(
                            listOfNotNull(placaVeiculo.ifBlank { null }, viagemAtual?.destino).joinToString(" • "),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor
                        )
                    }
                }
            }

            // Card do formulário
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {

                    // Data
                    DateInputField(
                        value = data,
                        onValueChange = { data = it },
                        label = "Data",
                        primaryColor = primaryColor,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(20.dp))

                    // Ordem de descarga
                    Text(
                        "Nº Ordem de Descarga",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ordemDescarga,
                        onValueChange = { ordemDescarga = it.filter { c -> c.isDigit() }.take(10) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Numbers, null, tint = placeholderColor)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("Ex: 12345", color = placeholderColor) }
                    )

                    Spacer(Modifier.height(20.dp))

                    // Valor
                    Text(
                        "Valor (R$)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { newValue ->
                            val formatted = formatarValorDescarga(newValue.text)
                            valor = TextFieldValue(formatted, selection = TextRange(formatted.length))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Text(
                                "R$",
                                color = placeholderColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("0,00", color = placeholderColor) }
                    )

                    Spacer(Modifier.height(24.dp))

                    // Foto
                    Text(
                        "Foto do Comprovante",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor
                    )
                    Spacer(Modifier.height(8.dp))
                    if (fotoImageBitmap != null) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                bitmap = fotoImageBitmap!!,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
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
                                    .background(errorColor, RoundedCornerShape(50))
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Remover",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { escolherDaGaleria() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF1976D2), RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        "Da galeria",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { abrirCamera() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(primaryColor, RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        "Nova foto",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable { abrirCamera() },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = inputBackground),
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        null,
                                        tint = placeholderColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Tirar foto",
                                        fontSize = 14.sp,
                                        color = placeholderColor
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                                    .clickable { escolherDaGaleria() },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = inputBackground),
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.PhotoLibrary,
                                        null,
                                        tint = placeholderColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Da galeria",
                                        fontSize = 14.sp,
                                        color = placeholderColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // Botão salvar
                    Button(
                        onClick = { salvarDescarga() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !salvando,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Salvar Descarga",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}