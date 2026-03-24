package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.*
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

private val SERVICOS = listOf("Troca de Óleo", "Troca de Pneu", "Freios", "Suspensão", "Elétrica", "Funilaria", "Outros")

// ===============================
// CAMERA DELEGATE PARA MANUTENÇÃO
// ===============================
private class ManutencaoCameraDelegate(
    private val fotoIndex: Int,
    private val onFotoCaptured: (Int, String, ImageBitmap) -> Unit,
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
                    onMessage("Erro ao converter imagem", true)
                    picker.dismissViewControllerAnimated(true, null)
                    return
                }

                val bytes = CameraHelper.imageToBytes(image)
                if (bytes != null) {
                    val skiaImage = SkiaImage.makeFromEncoded(bytes)
                    if (skiaImage != null) {
                        val bitmap = skiaImage.toComposeImageBitmap()
                        onFotoCaptured(fotoIndex, base64, bitmap)
                    } else {
                        onMessage("Erro ao processar imagem", true)
                    }
                } else {
                    onMessage("Erro ao comprimir imagem", true)
                }
            } catch (e: Exception) {
                onMessage("Erro: ${e.message}", true)
            }
        }

        picker.dismissViewControllerAnimated(true, null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ManutencaoScreen(repository: AppRepository, onVoltar: () -> Unit) {
    val motorista = remember { repository.getMotoristaLogado() }
    val equipamentos = remember { repository.getEquipamentosParaDropdown() }
    val scope = rememberCoroutineScope()

    var dataManutencao by remember { mutableStateOf(dataAtualFormatada()) }
    var placaSelecionada by remember { mutableStateOf("") }
    var servicoSelecionado by remember { mutableStateOf("Troca de Óleo") }
    var descricaoServico by remember { mutableStateOf("") }
    var localManutencao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var kmTrocaOleo by remember { mutableStateOf("") }
    var kmTrocaPneu by remember { mutableStateOf("") }
    var salvando by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }
    var placaExpanded by remember { mutableStateOf(false) }
    var servicoExpanded by remember { mutableStateOf(false) }

    // === ESTADOS DE FOTO ===
    var foto1Base64 by remember { mutableStateOf<String?>(null) }
    var foto1Bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var foto2Base64 by remember { mutableStateOf<String?>(null) }
    var foto2Bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var fotoMsg by remember { mutableStateOf<String?>(null) }
    var fotoMsgIsError by remember { mutableStateOf(false) }

    val cameraDelegate1 = remember {
        ManutencaoCameraDelegate(1, { _, base64, bmp -> foto1Base64 = base64; foto1Bitmap = bmp },
            { msg, err -> fotoMsg = msg; fotoMsgIsError = err })
    }
    val cameraDelegate2 = remember {
        ManutencaoCameraDelegate(2, { _, base64, bmp -> foto2Base64 = base64; foto2Bitmap = bmp },
            { msg, err -> fotoMsg = msg; fotoMsgIsError = err })
    }

    fun abrirCamera(fotoIndex: Int) {
        val vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            fotoMsg = "Câmera não disponível"; fotoMsgIsError = true; return
        }
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            delegate = if (fotoIndex == 1) cameraDelegate1 else cameraDelegate2
        }
        vc.presentViewController(picker, true, null)
    }

    fun salvar() {
        if (placaSelecionada.isBlank()) { erroMsg = "Selecione uma placa"; return }
        if (valor.isBlank()) { erroMsg = "Informe o valor"; return }
        if (servicoSelecionado == "Troca de Óleo" && kmTrocaOleo.isBlank()) { erroMsg = "Informe o KM da troca de óleo"; return }

        scope.launch {
            salvando = true
            val dataApi = converterDataParaAPI(dataManutencao)
            val viagemAtual = repository.getViagemAtual()
            val viagemId = viagemAtual?.viagem_id ?: 0L
            try {
                val manutencaoId = repository.salvarManutencao(
                    motorista?.motorista_id ?: "", viagemId, dataApi, placaSelecionada,
                    servicoSelecionado, descricaoServico, localManutencao, valor,
                    if (servicoSelecionado == "Troca de Óleo") kmTrocaOleo else null,
                    if (servicoSelecionado == "Troca de Pneu") kmTrocaPneu else null,
                    null, null, foto1Base64, foto2Base64
                )
                try {
                    val resp = ApiClient.salvarManutencao(SalvarManutencaoRequest(
                        motorista_id = motorista?.motorista_id ?: "", viagem_id = viagemId.toInt(),
                        data_manutencao = dataApi, placa = placaSelecionada, servico = servicoSelecionado,
                        descricao_servico = descricaoServico, local_manutencao = localManutencao, valor = valor,
                        km_troca_oleo = if (servicoSelecionado == "Troca de Óleo") kmTrocaOleo else null,
                        km_troca_pneu = if (servicoSelecionado == "Troca de Pneu") kmTrocaPneu else null,
                        pneus = emptyList(), tipos_pneu = emptyMap(),
                        foto_comprovante1 = foto1Base64, foto_comprovante2 = foto2Base64))
                    if (resp.status == "ok") { repository.marcarManutencaoSincronizada(manutencaoId); sucessoMsg = "Manutenção registrada!" }
                    else { sucessoMsg = "Manutenção salva. Será sincronizada automaticamente." }
                } catch (_: Exception) { sucessoMsg = "Manutenção salva offline! Sincronize quando tiver internet." }
            } catch (e: Exception) { erroMsg = "Erro: ${e.message}" }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }

    Scaffold(topBar = { GradientTopBar(title = "Adicionar Manutenção", onBackClick = onVoltar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(dataManutencao, { dataManutencao = it }, label = { Text("Data") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))

                    ExposedDropdownMenuBox(expanded = placaExpanded, onExpandedChange = { placaExpanded = it }) {
                        OutlinedTextField(placaSelecionada, {}, readOnly = true, label = { Text("Placa do veículo") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placaExpanded) },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = placaExpanded, onDismissRequest = { placaExpanded = false }) {
                            equipamentos.forEach { (_, placa) -> DropdownMenuItem(text = { Text(placa) }, onClick = { placaSelecionada = placa; placaExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    ExposedDropdownMenuBox(expanded = servicoExpanded, onExpandedChange = { servicoExpanded = it }) {
                        OutlinedTextField(servicoSelecionado, {}, readOnly = true, label = { Text("Tipo de serviço") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = servicoExpanded) },
                            leadingIcon = { Icon(Icons.Default.Build, null) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp))
                        ExposedDropdownMenu(expanded = servicoExpanded, onDismissRequest = { servicoExpanded = false }) {
                            SERVICOS.forEach { s -> DropdownMenuItem(text = { Text(s) }, onClick = { servicoSelecionado = s; servicoExpanded = false }) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(descricaoServico, { descricaoServico = it }, label = { Text("Descrição do serviço") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(localManutencao, { localManutencao = it }, label = { Text("Local da manutenção") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(valor, { valor = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Valor (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                    if (servicoSelecionado == "Troca de Óleo") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(kmTrocaOleo, { kmTrocaOleo = it.filter { c -> c.isDigit() } },
                            label = { Text("KM da troca") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                    if (servicoSelecionado == "Troca de Pneu") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(kmTrocaPneu, { kmTrocaPneu = it.filter { c -> c.isDigit() } },
                            label = { Text("KM da troca") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // === CARD DE FOTOS ===
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Fotos do Comprovante", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                    Text("Tire fotos do comprovante de serviço (opcional)", fontSize = 12.sp, color = AppColors.TextSecondary)
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FotoSlotManutencao("Foto 1", foto1Bitmap, { abrirCamera(1) }, { foto1Base64 = null; foto1Bitmap = null }, Modifier.weight(1f))
                        FotoSlotManutencao("Foto 2", foto2Bitmap, { abrirCamera(2) }, { foto2Base64 = null; foto2Bitmap = null }, Modifier.weight(1f))
                    }

                    if (fotoMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(fotoMsg!!, fontSize = 12.sp, color = if (fotoMsgIsError) AppColors.Error else AppColors.Success)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Salvar Manutenção", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FotoSlotManutencao(label: String, bitmap: ImageBitmap?, onClick: () -> Unit, onRemover: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (bitmap != null) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Image(bitmap, label, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                IconButton(onClick = onRemover, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))) {
                    Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).clickable { onClick() },
                contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }
}
