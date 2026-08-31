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
import platform.UIKit.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlinx.cinterop.*
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.mensagemErroAmigavel
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

private val CorFrete = Color(0xFFF59E0B)

// Classe delegate FORA do @Composable — mesmo padrão de AdicionarArlaScreen.ios.kt.
private class FreteCameraDelegate(
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
                    } else {
                        onMessage("Erro ao processar imagem", true)
                    }
                } else {
                    onMessage("Erro ao comprimir imagem", true)
                }
            } catch (e: Exception) {
                onMessage("Erro: ${mensagemErroAmigavel(e.message)}", true)
            }
        } else {
            onMessage("Nenhuma imagem selecionada", true)
        }

        picker.dismissViewControllerAnimated(true, null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

/**
 * "Adicionar Frete" — Rota Contínua: motorista cadastra vários fretes
 * avulsos (Normal ou Retorno) durante a mesma viagem. Mesmo padrão
 * offline-first de AdicionarArlaScreen.ios.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AdicionarFreteScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var modoOffline by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }
    var fretesJaCadastrados by remember { mutableStateOf<List<br.com.lfsystem.app.database.Frete>>(emptyList()) }

    fun recarregarFretesCadastrados(viagemId: Long) {
        fretesJaCadastrados = try { repository.getFretesPorViagemId(viagemId) } catch (e: Exception) { emptyList() }
    }

    LaunchedEffect(Unit) {
        carregando = true

        val viagemLocal = repository.getViagemAtual()
        if (viagemLocal != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemLocal.viagem_id.toInt(),
                destino = viagemLocal.destino,
                data = viagemLocal.data_inicio
            )
            modoOffline = false
            recarregarFretesCadastrados(viagemLocal.viagem_id)
            carregando = false
            return@LaunchedEffect
        }

        val viagemNaoSincronizada = repository.getUltimaViagemNaoSincronizada()
        if (viagemNaoSincronizada != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemNaoSincronizada.id.toInt(),
                destino = viagemNaoSincronizada.destino_nome,
                data = viagemNaoSincronizada.data_viagem
            )
            modoOffline = true
            recarregarFretesCadastrados(viagemNaoSincronizada.id)
            carregando = false
            return@LaunchedEffect
        }

        try {
            val response = api.ApiClient.arlaDados(
                api.ArlaDadosRequest(motorista_id = motorista?.motorista_id ?: "")
            )
            if (response.status == "ok" && response.viagens.isNotEmpty()) {
                val viagem = response.viagens.first()
                viagemEmAndamento = viagem
                repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data)
                modoOffline = false
                recarregarFretesCadastrados(viagem.id.toLong())
            } else {
                semViagemAberta = true
            }
        } catch (e: Exception) {
            modoOffline = true
            semViagemAberta = true
        }
        carregando = false
    }

    var tipo by remember { mutableStateOf("Normal") }
    var numeroIdentificacao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }

    var fotoComprovante by remember { mutableStateOf<ImageBitmap?>(null) }
    var fotoBase64 by remember { mutableStateOf<String?>(null) }

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) erroMsg = mensagem else sucessoMsg = mensagem
    }

    val cameraDelegate = remember {
        FreteCameraDelegate(
            onFotoCaptured = { base64, bitmap ->
                fotoBase64 = base64
                fotoComprovante = bitmap
            },
            onMessage = { msg, erro -> mostrarMensagem(msg, erro) }
        )
    }

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

    fun salvarLocal() {
        if (viagemEmAndamento == null) {
            mostrarMensagem("Nenhuma viagem em andamento", isErro = true)
            return
        }
        if (valor.text.isEmpty() || valor.text.replace(",", ".").toDoubleOrNull() == 0.0) {
            mostrarMensagem("Informe o valor do frete", isErro = true)
            return
        }

        scope.launch {
            salvando = true
            try {
                try {
                    val response = api.ApiClient.salvarFrete(
                        api.SalvarFreteRequest(
                            motorista_id = motorista?.motorista_id ?: "",
                            viagem_id = viagemEmAndamento!!.id,
                            tipo = tipo,
                            numero_identificacao = numeroIdentificacao,
                            valor = valor.text,
                            foto_base64 = fotoBase64
                        )
                    )
                    if (response.status == "ok") {
                        sucessoMsg = response.mensagem ?: "Frete registrado com sucesso!"
                    } else {
                        mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                    }
                } catch (e: Exception) {
                    repository.salvarFrete(
                        motoristaId = motorista?.motorista_id ?: "",
                        viagemId = viagemEmAndamento!!.id.toLong(),
                        tipo = tipo,
                        numeroIdentificacao = numeroIdentificacao,
                        valor = valor.text,
                        foto = fotoBase64
                    )
                    sucessoMsg = "Frete salvo! Sincronize quando tiver internet."
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro ao salvar: ${mensagemErroAmigavel(e.message)}", isErro = true)
            }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Adicionar Frete",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CorFrete)
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
                    .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                if (modoOffline) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text("Modo Offline. Frete será salvo para sincronizar depois.", color = AppColors.Orange, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (semViagemAberta) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, null, tint = AppColors.Error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Nenhuma viagem em andamento", fontWeight = FontWeight.Bold, color = AppColors.Error, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Inicie uma viagem primeiro para adicionar frete.", color = AppColors.TextSecondary, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onVoltar, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                                Text("Voltar")
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF273159).copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = CorFrete, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Viagem em andamento", fontSize = 12.sp, color = AppColors.TextSecondary)
                                Text(viagemEmAndamento?.destino ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (fretesJaCadastrados.isNotEmpty()) {
                        Text(
                            "Fretes cadastrados nesta viagem (${fretesJaCadastrados.size})",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                fretesJaCadastrados.forEach { f ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(f.tipo, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 14.sp)
                                            if (f.numero_identificacao.isNotBlank()) {
                                                Text(f.numero_identificacao, color = AppColors.TextSecondary, fontSize = 12.sp)
                                            }
                                        }
                                        Text("R$ ${f.valor}", fontWeight = FontWeight.Bold, color = CorFrete, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("Tipo de Frete *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { tipo = "Normal" },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (tipo == "Normal") CorFrete else Color(0xFFE5E7EB)
                                    )
                                ) {
                                    Text(
                                        "Normal",
                                        color = if (tipo == "Normal") Color.White else AppColors.TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Button(
                                    onClick = { tipo = "Retorno" },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (tipo == "Retorno") CorFrete else Color(0xFFE5E7EB)
                                    )
                                ) {
                                    Text(
                                        "Retorno",
                                        color = if (tipo == "Retorno") Color.White else AppColors.TextSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            Text("Valor do Frete (R\$) *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = valor,
                                onValueChange = { newValue ->
                                    val formatted = formatarMoedaFreteScreen(newValue.text)
                                    valor = TextFieldValue(formatted, selection = TextRange(formatted.length))
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = CorFrete) },
                                prefix = { Text("R$ ") },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) }
                            )

                            Spacer(Modifier.height(16.dp))

                            Text("Número/Identificação", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = numeroIdentificacao,
                                onValueChange = { numeroIdentificacao = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Tag, null, tint = CorFrete) },
                                placeholder = { Text("Nº da ordem, CT-e etc. (opcional)", color = Color(0xFF9CA3AF)) }
                            )

                            Spacer(Modifier.height(16.dp))

                            Text("Foto/Comprovante (opcional)", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))

                            if (fotoComprovante != null) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Image(
                                        bitmap = fotoComprovante!!,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = { fotoComprovante = null; fotoBase64 = null },
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                            .size(36.dp).background(AppColors.Error, RoundedCornerShape(50))
                                    ) {
                                        Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(
                                        onClick = { abrirCamera() },
                                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                                            .size(36.dp).background(CorFrete, RoundedCornerShape(50))
                                    ) {
                                        Icon(Icons.Default.CameraAlt, "Nova foto", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            } else {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(
                                        modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                                            .border(2.dp, CorFrete.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .clickable { abrirCamera() },
                                        color = CorFrete.copy(alpha = 0.05f)
                                    ) {
                                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Icon(Icons.Default.CameraAlt, null, tint = CorFrete, modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.height(6.dp))
                                            Text("Tirar Foto", color = CorFrete, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                                            .border(2.dp, Color(0xFF1976D2).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                            .clickable { escolherDaGaleria() },
                                        color = Color(0xFF1976D2).copy(alpha = 0.05f)
                                    ) {
                                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF1976D2), modifier = Modifier.size(32.dp))
                                            Spacer(Modifier.height(6.dp))
                                            Text("Da Galeria", color = Color(0xFF1976D2), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            Button(
                                onClick = { salvarLocal() },
                                enabled = !salvando,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CorFrete)
                            ) {
                                if (salvando) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Save, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("SALVAR FRETE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

private fun formatarMoedaFreteScreen(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val reais = value / 100
    val centavos = value % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}
