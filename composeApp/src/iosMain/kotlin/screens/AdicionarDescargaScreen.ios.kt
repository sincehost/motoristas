package screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    private val onFotoCaptured: (String) -> Unit,
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

                onFotoCaptured(base64)
                onMessage("✓ Foto capturada com sucesso!", false)
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
actual fun AdicionarDescargaScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Cores
    val primaryColor = Color(0xFF1E88E5)
    val backgroundColor = Color(0xFFF5F7FA)
    val cardColor = Color.White
    val successColor = Color(0xFF10B981)
    val errorColor = Color(0xFFEF4444)
    val inputBackground = Color(0xFFF9FAFB)
    val borderColor = Color(0xFFE5E7EB)
    val labelColor = Color(0xFF374151)
    val placeholderColor = Color(0xFF9CA3AF)

    // Estados básicos
    var salvando by remember { mutableStateOf(false) }
    var sucesso by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }

    // Dados carregados
    var motorista by remember { mutableStateOf<br.com.lfsystem.app.database.Motorista?>(null) }
    var viagemAtual by remember { mutableStateOf<br.com.lfsystem.app.database.ViagemAtual?>(null) }
    var placas by remember { mutableStateOf<List<String>>(emptyList()) }

    // Campos do formulário
    var data by remember { mutableStateOf(dataAtualFormatada()) }
    var placaSelecionada by remember { mutableStateOf<String?>(null) }
    var ordemDescarga by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var fotoBase64 by remember { mutableStateOf<String?>(null) }
    var expandedPlaca by remember { mutableStateOf(false) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = mensagem,
                duration = if (isErro) SnackbarDuration.Long else SnackbarDuration.Short
            )
        }
    }

    // Carregar dados de forma segura
    LaunchedEffect(Unit) {
        try {
            motorista = repository.getMotoristaLogado()
            viagemAtual = repository.getViagemAtual()
            placas = repository.getEquipamentosParaDropdown().map { it.second }

            // Tentar carregar placas da API
            motorista?.let { m ->
                try {
                    val response = ApiClient.abastecimentoDados(
                        AbastecimentoDadosRequest(

                            motorista_id = m.motorista_id
                        )
                    )
                    if (response.status == "ok" && response.placas.isNotEmpty()) {
                        placas = response.placas
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
            onFotoCaptured = { base64 ->
                fotoBase64 = base64
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

        if (placaSelecionada.isNullOrBlank()) {
            mostrarMensagem("Selecione a placa", isErro = true)
            return
        }
        if (ordemDescarga.isBlank()) {
            mostrarMensagem("Informe a ordem de descarga", isErro = true)
            return
        }
        if (valor.isBlank()) {
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
                            placa = placaSelecionada!!,
                            ordem_descarga = ordemDescarga.toIntOrNull() ?: 0,
                            valor = valor,
                            foto = fotoBase64
                        )
                    )
                    if (response.status == "ok") {
                        // API salvou com sucesso - NÃO salvar localmente
                        sucesso = true
                        mostrarMensagem("✓ Descarga registrada com sucesso!")
                    } else {
                        mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                    }
                } catch (e: Exception) {
                    // Sem internet - salvar localmente para sincronização
                    repository.salvarDescarga(
                        motoristaId = mot.motorista_id,
                        viagemId = viagem.viagem_id,
                        data = dataAPI,
                        placa = placaSelecionada!!,
                        ordemDescarga = ordemDescarga.toLongOrNull() ?: 0,
                        valor = valor,
                        foto = fotoBase64
                    )
                    sucesso = true
                    mostrarMensagem("✓ Descarga salva! Sincronize quando tiver internet.")
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

    // ========== TELA DE SUCESSO ==========
    if (sucesso) {
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
                            .background(successColor.copy(alpha = 0.1f), RoundedCornerShape(40.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = successColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Descarga Registrada!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Dados salvos com sucesso",
                        fontSize = 16.sp,
                        color = Color(0xFF6B7280)
                    )
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = onSucesso,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                    ) {
                        Text("Voltar ao Menu", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
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

    // ========== FORMULÁRIO ==========
    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Adicionar Descarga",
                onBackClick = onVoltar
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(16.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (data.visuals.message.contains("sucesso", ignoreCase = true) ||
                        data.visuals.message.contains("salva", ignoreCase = true))
                        successColor else errorColor,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    actionColor = Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor)
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
                            "Viagem em Andamento",
                            fontSize = 12.sp,
                            color = primaryColor.copy(alpha = 0.7f)
                        )
                        Text(
                            viagemAtual?.destino ?: "",
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

                    // Placa
                    Text(
                        "Placa do Veículo",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor
                    )
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedPlaca,
                        onExpandedChange = { expandedPlaca = it }
                    ) {
                        OutlinedTextField(
                            value = placaSelecionada ?: "Selecione a placa",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.DirectionsCar, null, tint = placeholderColor)
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPlaca)
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPlaca,
                            onDismissRequest = { expandedPlaca = false }
                        ) {
                            placas.forEach { placa ->
                                DropdownMenuItem(
                                    text = { Text(placa) },
                                    onClick = {
                                        placaSelecionada = placa
                                        expandedPlaca = false
                                    }
                                )
                            }
                        }
                    }

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
                        onValueChange = { ordemDescarga = it.filter { c -> c.isDigit() } },
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
                        onValueChange = { valor = formatarValorDescarga(it) },
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clickable { abrirCamera() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (fotoBase64 != null)
                                successColor.copy(alpha = 0.1f)
                            else
                                inputBackground
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (fotoBase64 != null) successColor else borderColor
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                if (fotoBase64 != null) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
                                null,
                                tint = if (fotoBase64 != null) successColor else placeholderColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (fotoBase64 != null) "Foto capturada ✓" else "Toque para tirar foto",
                                fontSize = 14.sp,
                                color = if (fotoBase64 != null) successColor else placeholderColor
                            )
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