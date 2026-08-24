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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
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
import util.formatarKmInput
import util.normalizarKmParaEnvio
import util.mensagemErroAmigavel
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage

// Mesma lista do Android (ManutencaoScreen.android.kt) — antes divergiam
// (iOS tinha Freios/Funilaria e não tinha Borracharia/Mecânica/Aparelhos).
private val SERVICOS = listOf("Troca de Óleo", "Troca de Pneu", "Borracharia (Remendo)", "Elétrica", "Mecânica", "Suspensão", "Aparelhos", "Outros")
private val TIPOS_PNEU_OPCOES = listOf("Novo", "Recapado", "Usado")

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
                onMessage("Erro: ${mensagemErroAmigavel(e.message)}", true)
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
    val focusManager = LocalFocusManager.current

    var dataManutencao by remember { mutableStateOf(dataAtualFormatada()) }
    var placaSelecionada by remember { mutableStateOf("") }
    var servicoSelecionado by remember { mutableStateOf("Troca de Óleo") }
    var tipoManutencaoSelecionado by remember { mutableStateOf("preventiva") }
    var descricaoServico by remember { mutableStateOf("") }
    var localManutencao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var kmTrocaOleo by remember { mutableStateOf(TextFieldValue("")) }
    var kmTrocaPneu by remember { mutableStateOf(TextFieldValue("")) }
    var salvando by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var placaExpanded by remember { mutableStateOf(false) }
    var servicoExpanded by remember { mutableStateOf(false) }

    // === ESTADOS DE PNEUS ===
    var pneusSelecionados by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var tiposPneu by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

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
        focusManager.clearFocus()
        val vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        if (CameraHelper.cameraSemPermissao()) {
            mostrarPermissaoCameraNegada = true
            return
        }
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            fotoMsg = "Câmera não disponível"; fotoMsgIsError = true; return
        }
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            delegate = if (fotoIndex == 1) cameraDelegate1 else cameraDelegate2
        }
        vc.presentViewController(picker, true, null)
    }

    // Escolher da galeria — Android já oferece essa opção aqui, iOS só tinha câmera.
    fun escolherDaGaleria(fotoIndex: Int) {
        focusManager.clearFocus()
        val vc = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
            delegate = if (fotoIndex == 1) cameraDelegate1 else cameraDelegate2
        }
        vc.presentViewController(picker, true, null)
    }

    fun salvar() {
        focusManager.clearFocus()
        if (placaSelecionada.isBlank()) { erroMsg = "Selecione uma placa"; return }
        if (valor.text.isBlank()) { erroMsg = "Informe o valor"; return }
        if (foto1Base64.isNullOrBlank()) { erroMsg = "Tire a foto do cupom fiscal"; return }
        if (servicoSelecionado == "Troca de Óleo" && kmTrocaOleo.text.isBlank()) { erroMsg = "Informe o KM da troca de óleo"; return }
        if (servicoSelecionado == "Troca de Pneu") {
            if (kmTrocaPneu.text.isBlank()) { erroMsg = "Informe o KM da troca de pneu"; return }
            if (pneusSelecionados.isEmpty()) { erroMsg = "Selecione pelo menos um pneu"; return }
            for (pneu in pneusSelecionados) {
                if (tiposPneu[pneu].isNullOrBlank()) { erroMsg = "Selecione o tipo para o pneu $pneu"; return }
            }
        }

        scope.launch {
            salvando = true
            val dataApi = converterDataParaAPI(dataManutencao)
            val kmTrocaOleoNormalizado = if (servicoSelecionado == "Troca de Óleo") normalizarKmParaEnvio(kmTrocaOleo.text) else null
            val kmTrocaPneuNormalizado = if (servicoSelecionado == "Troca de Pneu") normalizarKmParaEnvio(kmTrocaPneu.text) else null
            try {
                val viagemAtual = repository.getViagemAtual()
                val viagemId = viagemAtual?.viagem_id ?: 0L

                try {
                    val resp = ApiClient.salvarManutencao(SalvarManutencaoRequest(
                        motorista_id = motorista?.motorista_id ?: "", viagem_id = viagemId.toInt(),
                        data_manutencao = dataApi, placa = placaSelecionada, servico = servicoSelecionado,
                        tipo_manutencao = tipoManutencaoSelecionado,
                        descricao_servico = descricaoServico, local_manutencao = localManutencao, valor = valor.text,
                        km_troca_oleo = kmTrocaOleoNormalizado,
                        km_troca_pneu = kmTrocaPneuNormalizado,
                        pneus = pneusSelecionados.toList(),
                        tipos_pneu = tiposPneu.mapKeys { it.key },
                        foto_comprovante1 = foto1Base64, foto_comprovante2 = foto2Base64))
                    if (resp.status == "ok") {
                        sucessoMsg = "Manutenção registrada com sucesso!"
                    } else {
                        erroMsg = resp.mensagem ?: "Erro ao salvar"
                    }
                } catch (e: Exception) {
                    repository.salvarManutencao(
                        motorista?.motorista_id ?: "", viagemId, dataApi, placaSelecionada,
                        servicoSelecionado, tipoManutencaoSelecionado, descricaoServico, localManutencao, valor.text,
                        kmTrocaOleoNormalizado,
                        kmTrocaPneuNormalizado,
                        pneusSelecionados.sorted().joinToString(","),
                        tiposPneu.entries.joinToString(";") { "${it.key}:${it.value}" },
                        foto1Base64, foto2Base64
                    )
                    sucessoMsg = "Manutenção salva! Sincronize quando tiver internet."
                }
            } catch (e: Exception) { erroMsg = "Erro ao salvar: ${mensagemErroAmigavel(e.message)}" }
            salvando = false
        }
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(topBar = { GradientTopBar(title = "Adicionar Manutenção", onBackClick = onVoltar) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Background)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    OutlinedTextField(dataManutencao, { dataManutencao = it }, label = { Text("Data") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))

                    ui.AppDropdownField(
                        label = "Placa do veículo",
                        selectedText = placaSelecionada,
                        expanded = placaExpanded,
                        onExpandedChange = { placaExpanded = it },
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        equipamentos.forEach { (_, placa) ->
                            ui.AppDropdownMenuItem(text = { Text(placa) }, onClick = { placaSelecionada = placa; placaExpanded = false })
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    ui.AppDropdownField(
                        label = "Tipo de serviço",
                        selectedText = servicoSelecionado,
                        expanded = servicoExpanded,
                        onExpandedChange = { servicoExpanded = it },
                        leadingIcon = { Icon(Icons.Default.Build, null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SERVICOS.forEach { s ->
                            ui.AppDropdownMenuItem(text = { Text(s) }, onClick = { servicoSelecionado = s; servicoExpanded = false })
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Preventiva x Corretiva — planejada ou reparo/quebra.
                    Text("Tipo *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { tipoManutencaoSelecionado = "preventiva" },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tipoManutencaoSelecionado == "preventiva") Color(0xFF10B981) else Color(0xFFE5E7EB)
                            )
                        ) {
                            Icon(
                                Icons.Default.EventAvailable,
                                null,
                                tint = if (tipoManutencaoSelecionado == "preventiva") Color.White else AppColors.TextSecondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Preventiva",
                                color = if (tipoManutencaoSelecionado == "preventiva") Color.White else AppColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = { tipoManutencaoSelecionado = "corretiva" },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tipoManutencaoSelecionado == "corretiva") Color(0xFFEF4444) else Color(0xFFE5E7EB)
                            )
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = if (tipoManutencaoSelecionado == "corretiva") Color.White else AppColors.TextSecondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Corretiva",
                                color = if (tipoManutencaoSelecionado == "corretiva") Color.White else AppColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        "Preventiva: manutenção planejada. Corretiva: reparo de algo que quebrou.",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(descricaoServico, { descricaoServico = it }, label = { Text("Descrição do serviço") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(localManutencao, { localManutencao = it }, label = { Text("Local da manutenção") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(valor, { newValue ->
                        val digits = newValue.text.filter { c -> c.isDigit() }.take(9)
                        val formatted = formatarValorManutencao(digits)
                        valor = TextFieldValue(formatted, selection = TextRange(formatted.length))
                    },
                        label = { Text("Valor (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))

                    if (servicoSelecionado == "Troca de Óleo") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(kmTrocaOleo, { newValue ->
                            val formatted = formatarKmInput(newValue.text)
                            kmTrocaOleo = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                        },
                            label = { Text("KM da troca") }, placeholder = { Text("Ex.: 115670.5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Text(
                            "Digite como aparece no painel. Ex.: 115670.5",
                            fontSize = 12.sp,
                            color = AppColors.Primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                    if (servicoSelecionado == "Troca de Pneu") {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(kmTrocaPneu, { newValue ->
                            val formatted = formatarKmInput(newValue.text)
                            kmTrocaPneu = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                        },
                            label = { Text("KM da troca") }, placeholder = { Text("Ex.: 115670.5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Text(
                            "Digite como aparece no painel. Ex.: 115670.5",
                            fontSize = 12.sp,
                            color = AppColors.Primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )

                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider(color = AppColors.Background)
                        Spacer(Modifier.height(16.dp))

                        Text("Selecione os Pneus", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(16.dp))

                        ChassiPneusIos(
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
                                TipoPneuSelectorIos(
                                    pneuNumero = pneu,
                                    tipoSelecionado = tiposPneu[pneu] ?: "",
                                    opcoes = TIPOS_PNEU_OPCOES,
                                    onTipoChange = { tipo ->
                                        tiposPneu = tiposPneu.toMutableMap().apply { put(pneu, tipo) }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
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
                        FotoSlotManutencao("Foto 1", foto1Bitmap, { abrirCamera(1) }, { escolherDaGaleria(1) }, { foto1Base64 = null; foto1Bitmap = null }, Modifier.weight(1f))
                        FotoSlotManutencao("Foto 2", foto2Bitmap, { abrirCamera(2) }, { escolherDaGaleria(2) }, { foto2Base64 = null; foto2Bitmap = null }, Modifier.weight(1f))
                    }

                    if (fotoMsg != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(fotoMsg!!, fontSize = 12.sp, color = if (fotoMsgIsError) AppColors.Error else AppColors.Success)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))) {
                if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                else {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("SALVAR MANUTENÇÃO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FotoSlotManutencao(label: String, bitmap: ImageBitmap?, onClick: () -> Unit, onEscolherGaleria: () -> Unit, onRemover: () -> Unit, modifier: Modifier = Modifier) {
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
                    Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
                }
                // Botão pequeno de galeria — Android já oferece essa opção aqui.
                IconButton(onClick = onEscolherGaleria, modifier = Modifier.align(Alignment.BottomEnd).size(28.dp)
                    .background(Color(0xFF1976D2), RoundedCornerShape(14.dp))) {
                    Icon(Icons.Default.PhotoLibrary, "Da galeria", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ===============================
// CHASSI DE PNEUS (igual Android)
// ===============================
@Composable
private fun ChassiPneusIos(pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(8.dp).height(4.dp).background(Color(0xFF374151), RoundedCornerShape(4.dp)))

        Text("Eixo Dianteiro", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusIos(listOf(1, 2), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 2", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusIos(listOf(3, 4), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 3 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploIos(listOf(5, 6, 7, 8), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 4 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploIos(listOf(9, 10, 11, 12), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(4.dp).height(30.dp).background(Color(0xFF9CA3AF)))
        Text("── Carreta ──", fontSize = 10.sp, color = AppColors.TextSecondary)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        Text("Eixo 5", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploIos(listOf(13, 14, 15, 16), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 6", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploIos(listOf(17, 18, 19, 20), pneusSelecionados, onPneuToggle)

        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 7", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploIos(listOf(21, 22, 23, 24), pneusSelecionados, onPneuToggle)
    }
}

@Composable
private fun EixoPneusIos(pneus: List<Int>, pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Box(modifier = Modifier.width(100.dp).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
            PneuCheckboxIos(pneus[0], pneus[0] in pneusSelecionados) { onPneuToggle(pneus[0]) }
        }
        Box(modifier = Modifier.weight(1f).height(12.dp).background(Color(0xFF4B5563), RoundedCornerShape(6.dp)))
        Box(modifier = Modifier.width(100.dp).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
            PneuCheckboxIos(pneus[1], pneus[1] in pneusSelecionados) { onPneuToggle(pneus[1]) }
        }
    }
}

@Composable
private fun EixoPneusDuploIos(pneus: List<Int>, pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PneuCheckboxIos(pneus[0], pneus[0] in pneusSelecionados) { onPneuToggle(pneus[0]) }
            PneuCheckboxIos(pneus[1], pneus[1] in pneusSelecionados) { onPneuToggle(pneus[1]) }
        }
        Box(modifier = Modifier.weight(1f).height(12.dp).background(Color(0xFF4B5563), RoundedCornerShape(6.dp)))
        Row(modifier = Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PneuCheckboxIos(pneus[2], pneus[2] in pneusSelecionados) { onPneuToggle(pneus[2]) }
            PneuCheckboxIos(pneus[3], pneus[3] in pneusSelecionados) { onPneuToggle(pneus[3]) }
        }
    }
}

@Composable
private fun PneuCheckboxIos(numero: Int, selecionado: Boolean, onClick: () -> Unit) {
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
private fun TipoPneuSelectorIos(pneuNumero: Int, tipoSelecionado: String, opcoes: List<String>, onTipoChange: (String) -> Unit) {
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

private fun formatarValorManutencao(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val reais = value / 100
    val centavos = value % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}
