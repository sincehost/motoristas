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
import database.AppRepository
import kotlinx.coroutines.launch
import platform.UIKit.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlinx.cinterop.*
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.dataAtualFormatada
import util.converterDataParaAPI
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

// ===============================
// CAMERA DELEGATE
// ===============================
private class DespesaCameraDelegate(
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
                if (base64 == null) { onMessage("Erro ao converter imagem", true); picker.dismissViewControllerAnimated(true, null); return }
                val bytes = CameraHelper.imageToBytes(image)
                if (bytes != null) {
                    val skImg = SkiaImage.makeFromEncoded(bytes)
                    if (skImg != null) { onFotoCaptured(base64, skImg.toComposeImageBitmap()) }
                    else { onMessage("Erro ao processar imagem", true) }
                } else { onMessage("Erro ao comprimir imagem", true) }
            } catch (e: Exception) { onMessage("Erro: ${e.message}", true) }
        }
        picker.dismissViewControllerAnimated(true, null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun OutrasDespesasScreen(repository: AppRepository, onVoltar: () -> Unit, onSucesso: () -> Unit) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val viagemAtual = remember { repository.getViagemAtual() }
    val tiposDespesa = remember { repository.getAllTiposDespesa() }
    val focusManager = LocalFocusManager.current

    var tipoDespesa by remember { mutableStateOf("") }
    var tipoDespesaExpandido by remember { mutableStateOf(false) }
    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var dataDespesa by remember { mutableStateOf(dataAtualFormatada()) }
    var localDespesa by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // === FOTO ===
    var fotoBase64 by remember { mutableStateOf<String?>(null) }
    var fotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val cameraDelegate = remember {
        DespesaCameraDelegate(
            onFotoCaptured = { base64, bmp -> fotoBase64 = base64; fotoBitmap = bmp },
            onMessage = { _, _ -> }
        )
    }

    fun abrirCamera() {
        val vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        if (CameraHelper.cameraSemPermissao()) {
            mostrarPermissaoCameraNegada = true
            return
        }
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) return
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            delegate = cameraDelegate
        }
        vc.presentViewController(picker, true, null)
    }

    // Escolher da galeria — Android já oferece essa opção aqui, iOS só tinha câmera.
    fun escolherDaGaleria() {
        val vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            delegate = cameraDelegate
        }
        vc.presentViewController(picker, true, null)
    }

    fun salvar() {
        if (tipoDespesa.isBlank()) { erroMsg = "Selecione o tipo de despesa"; return }
        if (valor.text.isBlank()) { erroMsg = "Informe o valor"; return }
        if (dataDespesa.isBlank()) { erroMsg = "Informe a data"; return }
        if (viagemAtual == null) { erroMsg = "Nenhuma viagem em andamento"; return }
        if (fotoBase64.isNullOrEmpty()) {
            erroMsg = "Adicione uma foto do comprovante da despesa para continuar."
            return
        }
        val viagemId = viagemAtual.viagem_id
        scope.launch {
            salvando = true
            val dataApi = converterDataParaAPI(dataDespesa)
            try {
                repository.salvarOutraDespesa(motorista?.motorista_id ?: "", viagemId, tipoDespesa,
                    descricao.ifEmpty { tipoDespesa }, valor.text, dataApi, localDespesa.ifEmpty { null }, fotoBase64)
                try {
                    val resp = api.ApiClient.salvarOutraDespesa(api.SalvarOutraDespesaRequest(
                        motorista?.motorista_id ?: "", viagemId.toInt(), tipoDespesa,
                        descricao.ifEmpty { tipoDespesa }, valor.text, dataApi, localDespesa.ifEmpty { null }, fotoBase64))
                    if (resp.status == "ok") { repository.getOutrasDespesasParaSincronizar().lastOrNull()?.let { repository.marcarOutraDespesaSincronizada(it.id) } }
                } catch (_: Exception) {}
                sucessoMsg = "Despesa salva com sucesso!"
            } catch (e: Exception) { erroMsg = util.mensagemErroAmigavel(e.message) }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(topBar = { GradientTopBar(title = "Outras Despesas", onBackClick = onVoltar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).pointerInput(Unit) {
            detectTapGestures(onTap = {
                focusManager.clearFocus()
            })
        }.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Tipo de Despesa", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    ui.AppDropdownField(
                        label = "Selecione",
                        selectedText = tipoDespesa,
                        expanded = tipoDespesaExpandido,
                        onExpandedChange = { focusManager.clearFocus(); tipoDespesaExpandido = it },
                        leadingIcon = { Icon(Icons.Default.Receipt, null, tint = AppColors.Primary) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tiposDespesa.forEach { t ->
                            ui.AppDropdownMenuItem(
                                text = { Text(t.nome) },
                                onClick = { tipoDespesa = t.nome; tipoDespesaExpandido = false }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição (opcional)") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(valor, { newValue ->
                        val formatted = formatarValorDespesaOutras(newValue.text)
                        valor = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                    },
                        label = { Text("Valor (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(dataDespesa, { dataDespesa = it }, label = { Text("Data") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, null) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(localDespesa, { localDespesa = it }, label = { Text("Local (opcional)") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }
            }

            Spacer(Modifier.height(16.dp))

            // === FOTO DO COMPROVANTE ===
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Foto do Comprovante *", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    if (fotoBitmap != null) {
                        Box(Modifier.fillMaxWidth().height(200.dp)) {
                            Image(fotoBitmap!!, "Comprovante", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            IconButton(onClick = { fotoBase64 = null; fotoBitmap = null },
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp)
                                    .background(AppColors.Error, RoundedCornerShape(50))) {
                                Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { abrirCamera() }, modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.CameraAlt, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Tirar Foto")
                            }
                            OutlinedButton(onClick = { escolherDaGaleria() }, modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.PhotoLibrary, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Galeria")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Salvar Despesa", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun formatarValorDespesaOutras(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val reais = value / 100
    val centavos = value % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}
