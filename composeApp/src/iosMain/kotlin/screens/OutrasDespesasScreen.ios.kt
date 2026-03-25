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

private val TIPOS_DESPESA = listOf("Pedágio", "Refeição", "Hospedagem", "Lavagem", "Estacionamento", "Outros")

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

    var tipoDespesa by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var dataDespesa by remember { mutableStateOf(dataAtualFormatada()) }
    var localDespesa by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
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
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) return
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            delegate = cameraDelegate
        }
        vc.presentViewController(picker, true, null)
    }

    fun salvar() {
        if (tipoDespesa.isBlank()) { erroMsg = "Selecione o tipo de despesa"; return }
        if (valor.isBlank()) { erroMsg = "Informe o valor"; return }
        scope.launch {
            salvando = true
            val dataApi = converterDataParaAPI(dataDespesa)
            val viagemId = viagemAtual?.viagem_id ?: 0L
            try {
                repository.salvarOutraDespesa(motorista?.motorista_id ?: "", viagemId, tipoDespesa,
                    descricao.ifEmpty { null }, valor, dataApi, localDespesa.ifEmpty { null }, fotoBase64)
                try {
                    val resp = api.ApiClient.salvarOutraDespesa(api.SalvarOutraDespesaRequest(
                        motorista?.motorista_id ?: "", viagemId.toInt(), tipoDespesa,
                        descricao.ifEmpty { tipoDespesa }, valor, dataApi, localDespesa.ifEmpty { null }, fotoBase64))
                    if (resp.status == "ok") { repository.getOutrasDespesasParaSincronizar().lastOrNull()?.let { repository.marcarOutraDespesaSincronizada(it.id) } }
                } catch (_: Exception) {}
                sucessoMsg = "Despesa salva com sucesso!"
            } catch (e: Exception) { erroMsg = "Erro: ${e.message}" }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }

    Scaffold(topBar = { GradientTopBar(title = "Outras Despesas", onBackClick = onVoltar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Tipo de Despesa", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIPOS_DESPESA.take(3).forEach { tipo ->
                            FilterChip(selected = tipoDespesa == tipo, onClick = { tipoDespesa = tipo },
                                label = { Text(tipo, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.Primary, selectedLabelColor = Color.White))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TIPOS_DESPESA.drop(3).forEach { tipo ->
                            FilterChip(selected = tipoDespesa == tipo, onClick = { tipoDespesa = tipo },
                                label = { Text(tipo, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.Primary, selectedLabelColor = Color.White))
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição (opcional)") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(valor, { valor = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Valor (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    Text("Foto do Comprovante", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    if (fotoBitmap != null) {
                        Box(Modifier.fillMaxWidth().height(200.dp)) {
                            Image(fotoBitmap!!, "Comprovante", Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            IconButton(onClick = { fotoBase64 = null; fotoBitmap = null },
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))) {
                                Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { abrirCamera() }, modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.CameraAlt, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tirar Foto do Comprovante")
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
