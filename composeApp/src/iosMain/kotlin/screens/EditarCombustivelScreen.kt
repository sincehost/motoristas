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
import platform.darwin.NSObject
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.dataAtualFormatada
import util.converterDataParaAPI
import util.converterDataParaExibicao

// Delegate da câmera nativa iOS - fora do @Composable - suporta as duas fotos (marcador/cupom)
private class EditarCombustivelCameraDelegate(
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
        onMessage("Captura cancelada", false)
        picker.dismissViewControllerAnimated(true, null)
    }
}

@Composable
private fun FotoCapturaCombustivelEdit(
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
                    .background(AppColors.Primary, RoundedCornerShape(50))
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
                .border(2.dp, AppColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .background(AppColors.Primary.copy(alpha = 0.05f))
                .clickable { onCapturar() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(40.dp), tint = AppColors.Primary)
                Spacer(Modifier.height(8.dp))
                Text("Tirar Foto", color = AppColors.Primary, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarCombustivelScreen(
    repository: AppRepository,
    abastecimentoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Formas de pagamento e tipos de combustível (catálogo da empresa, cadastrado no admin)
    val formasPagamento = remember { repository.getAllFormasPagamento() }
    val tiposCombustivel = remember { repository.getAllTiposCombustivel() }

    var carregando by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Dados da viagem (info)
    var destino by remember { mutableStateOf("") }
    var dataViagem by remember { mutableStateOf("") }

    // Campos do formulário
    var placa by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    var nomePosto by remember { mutableStateOf("") }
    var kmPosto by remember { mutableStateOf("") }
    var tipoCombustivel by remember { mutableStateOf("Diesel Caminhão") }
    var horas by remember { mutableStateOf("") }
    var litros by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorLitro by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorTotal by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var tipoPagamento by remember { mutableStateOf("dinheiro") }
    var fotoMarcadorBase64 by remember { mutableStateOf<String?>(null) }
    var fotoCupomBase64 by remember { mutableStateOf<String?>(null) }
    val fotoMarcadorBitmap = remember(fotoMarcadorBase64) { fotoMarcadorBase64?.let { CameraHelper.base64ToImageBitmap(it) } }
    val fotoCupomBitmap = remember(fotoCupomBase64) { fotoCupomBase64?.let { CameraHelper.base64ToImageBitmap(it) } }

    var combustivelExpanded by remember { mutableStateOf(false) }

    // Carrega dados da API
    LaunchedEffect(Unit) {
        carregando = true
        try {
            val response = api.ApiClient.buscarAbastecimento(
                api.BuscarAbastecimentoRequest(abastecimento_id = abastecimentoId, motorista_id = motorista?.motorista_id ?: "")
            )
            if (response.status == "ok" && response.abastecimento != null) {
                val a = response.abastecimento
                destino = a.destino; dataViagem = a.data_viagem; placa = a.placa
                data = converterDataParaExibicao(a.data_abastecimento); nomePosto = a.nome_posto; kmPosto = a.km_posto
                tipoCombustivel = a.tipo_combustivel; horas = a.horas
                litros = TextFieldValue(a.litros_abastecidos, selection = TextRange(a.litros_abastecidos.length))
                valorLitro = TextFieldValue(a.valor_litro, selection = TextRange(a.valor_litro.length))
                valorTotal = TextFieldValue(a.valor_total, selection = TextRange(a.valor_total.length))
                tipoPagamento = a.forma_pagamento.lowercase()
                fotoMarcadorBase64 = a.foto_marcador; fotoCupomBase64 = a.foto_cupom
            } else {
                erroMsg = "Abastecimento não encontrado"
            }
        } catch (e: Exception) {
            erroMsg = "Erro ao carregar: ${e.message}"
        }
        carregando = false
    }

    // Delegates persistentes da câmera - um para cada tipo de foto
    val cameraDelegateMarcador = remember {
        EditarCombustivelCameraDelegate(
            tipoFoto = "marcador",
            onFotoCaptured = { _, base64 -> fotoMarcadorBase64 = base64 },
            onMessage = { msg, erro -> if (erro) erroMsg = msg }
        )
    }
    val cameraDelegateCupom = remember {
        EditarCombustivelCameraDelegate(
            tipoFoto = "cupom",
            onFotoCaptured = { _, base64 -> fotoCupomBase64 = base64 },
            onMessage = { msg, erro -> if (erro) erroMsg = msg }
        )
    }

    // Função para abrir a câmera nativa iOS
    fun abrirCamera(tipo: String) {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (viewController == null) {
            erroMsg = "Não foi possível abrir a câmera"
            return
        }
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            erroMsg = "Câmera não disponível neste dispositivo"
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

    fun salvar() {
        if (nomePosto.isBlank()) { erroMsg = "Informe o posto"; return }
        if (kmPosto.isBlank()) { erroMsg = "Informe o KM"; return }
        if (tiposCombustivel.find { it.nome == tipoCombustivel }?.requer_horas == 1L && horas.isBlank()) { erroMsg = "Informe as horas"; return }
        if (litros.text.isBlank()) { erroMsg = "Informe os litros"; return }
        if (valorLitro.text.isBlank()) { erroMsg = "Informe o valor do litro"; return }
        if (valorTotal.text.isBlank()) { erroMsg = "Informe o valor total"; return }

        scope.launch {
            salvando = true
            try {
                val resp = api.ApiClient.atualizarAbastecimento(
                    api.AtualizarAbastecimentoRequest(
                        abastecimento_id = abastecimentoId, motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemId, placa = placa, data_abastecimento = converterDataParaAPI(data),
                        nome_posto = nomePosto, km_posto = kmPosto, tipo_combustivel = tipoCombustivel,
                        horas = horas, litros_abastecidos = litros.text.replace(",", "."),
                        valor_litro = valorLitro.text.replace(",", "."), valor_total = valorTotal.text.replace(",", "."),
                        forma_pagamento = tipoPagamento, foto_cupom = fotoCupomBase64, foto_marcador = fotoMarcadorBase64
                    )
                )
                if (resp.status == "ok") sucessoMsg = "Abastecimento atualizado!" else erroMsg = resp.mensagem ?: "Erro"
            } catch (e: Exception) { erroMsg = "Erro: ${e.message}" }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }

    Scaffold(topBar = { GradientTopBar(title = "Editar Combustível", onBackClick = onVoltar) }) { padding ->
        when {
            carregando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary); Spacer(Modifier.height(16.dp))
                    Text("Carregando dados...", color = AppColors.TextSecondary)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }.verticalScroll(rememberScrollState()).padding(16.dp)) {
                // Info da viagem
                if (destino.isNotBlank()) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary)
                            Spacer(Modifier.width(8.dp))
                            Column { Text(destino, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(dataViagem, fontSize = 12.sp, color = AppColors.TextSecondary) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(placa, {}, readOnly = true, label = { Text("Placa") },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(data, { data = it }, label = { Text("Data") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(nomePosto, { nomePosto = it }, label = { Text("Nome do posto") },
                            leadingIcon = { Icon(Icons.Default.LocalGasStation, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(kmPosto, { kmPosto = it.filter { c -> c.isDigit() } }, label = { Text("KM no posto") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        // Foto Quilometragem
                        Text("Foto Quilometragem", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaCombustivelEdit(
                            bitmap = fotoMarcadorBitmap,
                            onRemover = { fotoMarcadorBase64 = null },
                            onCapturar = { abrirCamera("marcador") }
                        )
                        Spacer(Modifier.height(12.dp))

                        // Tipo combustível
                        ui.AppDropdownField(
                            label = "Tipo combustível",
                            selectedText = tipoCombustivel,
                            expanded = combustivelExpanded,
                            onExpandedChange = { combustivelExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tiposCombustivel.forEach { tc ->
                                ui.AppDropdownMenuItem(
                                    text = { Text(tc.nome) },
                                    onClick = { tipoCombustivel = tc.nome; combustivelExpanded = false }
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        if (tiposCombustivel.find { it.nome == tipoCombustivel }?.requer_horas == 1L) {
                            OutlinedTextField(horas, { horas = it }, label = { Text("Horas") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(12.dp))
                        }

                        OutlinedTextField(
                            value = litros,
                            onValueChange = { newValue ->
                                val filtered = newValue.text.filter { c -> c.isDigit() || c == ',' || c == '.' }
                                litros = TextFieldValue(text = filtered, selection = TextRange(filtered.length))
                            },
                            label = { Text("Litros") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valorLitro,
                            onValueChange = { newValue ->
                                val filtered = newValue.text.filter { c -> c.isDigit() || c == ',' || c == '.' }
                                valorLitro = TextFieldValue(text = filtered, selection = TextRange(filtered.length))
                            },
                            label = { Text("Valor por litro (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valorTotal,
                            onValueChange = { newValue ->
                                val filtered = newValue.text.filter { c -> c.isDigit() || c == ',' || c == '.' }
                                valorTotal = TextFieldValue(text = filtered, selection = TextRange(filtered.length))
                            },
                            label = { Text("Valor total (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        // Pagamento
                        Text("Forma de pagamento", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            formasPagamento.forEach { fp ->
                                FilterChip(selected = tipoPagamento == fp.codigo, onClick = { tipoPagamento = fp.codigo },
                                    label = { Text(fp.nome, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.Primary, selectedLabelColor = Color.White))
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Foto Cupom Fiscal
                        Text("Foto Cupom Fiscal", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaCombustivelEdit(
                            bitmap = fotoCupomBitmap,
                            onRemover = { fotoCupomBase64 = null },
                            onCapturar = { abrirCamera("cupom") }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar Alterações", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
