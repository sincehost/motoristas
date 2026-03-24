package screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.DateInputField
import util.rememberCameraState
import util.dataAtualFormatada
import util.converterDataParaAPI
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarDescargaScreen(
    repository: AppRepository,
    descargaId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Estados
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var sucesso by remember { mutableStateOf<String?>(null) }
    var carregando by remember { mutableStateOf(true) }
    var modoOffline by remember { mutableStateOf(false) }

    // Dados da viagem
    var destino by remember { mutableStateOf("") }
    var dataViagem by remember { mutableStateOf("") }

    // Placas disponíveis
    var placas by remember { mutableStateOf<List<String>>(emptyList()) }

    // Campos do formulário
    var data by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var placaSelecionada by remember { mutableStateOf<String?>(null) }
    var expandedPlaca by remember { mutableStateOf(false) }
    var ordemDescarga by rememberSaveable { mutableStateOf("") }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }

    // Foto
    val cameraState = rememberCameraState(context, prefix = "DESC_EDIT")

    // Camera

    // Carrega dados ao iniciar
    LaunchedEffect(Unit) {
        carregando = true

        try {
            // Carrega placas localmente
            placas = repository.getEquipamentosParaDropdown().map { it.second }

            // Busca dados da descarga da API
            val response = api.ApiClient.buscarDescarga(
                api.BuscarDescargaRequest(
                    descarga_id = descargaId,
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )

            if (response.status == "ok" && response.descarga != null) {
                val desc = response.descarga

                // Dados da viagem
                destino = desc.destino
                dataViagem = desc.data_viagem

                // Campos do formulário
                data = formatarDataBRParaExibicao(desc.data_descarga)
                placaSelecionada = desc.placa
                ordemDescarga = desc.ordem_descarga.toString()
                valor = TextFieldValue(formatarDecimalParaExibicao(desc.valor))

                // Carrega foto se existir

                if (!desc.foto.isNullOrEmpty()) {
                    cameraState.loadExisting(desc.foto)
                }

                modoOffline = false
            } else {
                erro = "Descarga não encontrada"
            }
        } catch (e: Exception) {
            modoOffline = true
            erro = "Erro ao carregar dados: ${e.message}"
        }

        carregando = false
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success || cameraState.checkPhotoExistsAfterCapture()) {
            cameraState.onPhotoTaken()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val uri = cameraState.prepareCapture()
                cameraLauncher.launch(uri)
            } catch (e: Exception) { }
        }
    }

    fun tirarFoto() {
        try {
            val uri = cameraState.prepareCapture()
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> cameraLauncher.launch(uri)
                else -> permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) { }
    }

    // Função atualizar
    fun atualizarDescarga() {
        // Validação
        if (placaSelecionada.isNullOrBlank()) { erro = "Selecione a placa"; return }
        if (ordemDescarga.isEmpty()) { erro = "Informe a ordem de descarga"; return }
        if (valor.text.isEmpty()) { erro = "Informe o valor"; return }
        if (cameraState.base64.isNullOrBlank()) { erro = "Tire a foto do comprovante"; return }

        scope.launch {
            salvando = true
            erro = null
            try {
                val response = api.ApiClient.atualizarDescarga(
                    api.AtualizarDescargaRequest(
                        descarga_id = descargaId,
                        motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemId,
                        data = converterDataParaAPI(data),
                        placa = placaSelecionada!!,
                        ordem_descarga = ordemDescarga.toIntOrNull() ?: 0,
                        valor = valor.text.replace(",", "."),
                        foto = cameraState.base64
                    )
                )

                if (response.status == "ok") {
                    sucesso = "Descarga atualizada com sucesso!"
                    kotlinx.coroutines.delay(1500)
                    onVoltar()
                } else {
                    erro = response.mensagem ?: "Erro ao atualizar"
                }

            } catch (e: Exception) {
                erro = "Erro ao atualizar: ${e.message}"
            }
            salvando = false
        }
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar Descarga",
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
                    CircularProgressIndicator(color = Color(0xFF1E88E5))
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
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Aviso offline
                if (modoOffline) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text("Sem conexão. Conecte para editar descarga.", color = AppColors.Orange, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Card da viagem
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF273159).copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Viagem", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Text(destino, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                            Text("Iniciada em: ${formatarDataBR(dataViagem)}", fontSize = 12.sp, color = AppColors.TextSecondary)
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

                        // Data
                        DateInputField(
                            value = data,
                            onValueChange = { data = it },
                            label = "Data *",
                            primaryColor = Color(0xFF1E88E5),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        // Placa do Veículo
                        Text("Placa do Veículo *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
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
                                leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF1E88E5)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPlaca) })
                            
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

                        Spacer(Modifier.height(16.dp))

                        // Ordem de Descarga
                        Text("Nº Ordem de Descarga *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = ordemDescarga,
                            onValueChange = { ordemDescarga = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Default.Numbers, null, tint = Color(0xFF1E88E5)) },
                            placeholder = { Text("Ex: 12345", color = Color(0xFF9CA3AF)) })
                        

                        Spacer(Modifier.height(16.dp))

                        // Valor
                        Text("Valor *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = valor,
                            onValueChange = { newValue ->
                                val formatted = formatarMoedaDescarga(newValue.text)
                                valor = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF1E88E5)) },
                            prefix = { Text("R$ ") },
                            placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                        

                        Spacer(Modifier.height(16.dp))

                        // Foto do Comprovante
                        Text("Foto do Comprovante *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaDescarga(
                            foto = cameraState.bitmap,
                            onClick = { tirarFoto() },
                            onRemover = { cameraState.clear() }
                        )

                        // Mensagens
                        erro?.let {
                            Spacer(Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = 0.1f))) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, null, tint = AppColors.Error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = AppColors.Error)
                                }
                            }
                        }

                        sucesso?.let {
                            Spacer(Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = if (ui.isDark()) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f))) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Secondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = AppColors.Secondary)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Botão Atualizar
                        Button(
                            onClick = { atualizarDescarga() },
                            enabled = !salvando && !modoOffline,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ATUALIZAR DESCARGA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun FotoCapturaDescarga(foto: Bitmap?, onClick: () -> Unit, onRemover: () -> Unit) {
    if (foto != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = foto.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    IconButton(
                        onClick = onRemover,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Surface(
                            color = AppColors.Error,
                            shape = RoundedCornerShape(50),
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remover foto",
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Foto capturada",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() },
                    color = Color(0xFF1E88E5).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color(0xFF1E88E5),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tirar nova foto",
                            color = Color(0xFF1E88E5),
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF1E88E5).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clickable { onClick() },
            color = Color(0xFF1E88E5).copy(alpha = 0.05f)
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF1E88E5),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tirar Foto",
                    color = Color(0xFF1E88E5),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toque para capturar",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

private fun formatarMoedaDescarga(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val decimal = value / 100.0
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
    symbols.decimalSeparator = ','
    symbols.groupingSeparator = '.'
    val formatter = java.text.DecimalFormat("#,##0.00", symbols)
    return formatter.format(decimal)
}

private fun formatarDataBR(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else {
            data
        }
    } catch (e: Exception) {
        data
    }
}

private fun formatarDataBRParaExibicao(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) {
            "${partes[2]}/${partes[1]}/${partes[0]}"
        } else {
            data
        }
    } catch (e: Exception) {
        data
    }
}

private fun formatarDecimalParaExibicao(valor: String): String {
    return try {
        val decimal = valor.toDoubleOrNull() ?: return ""
        val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
        symbols.decimalSeparator = ','
        symbols.groupingSeparator = '.'
        val formatter = java.text.DecimalFormat("#,##0.00", symbols)
        formatter.format(decimal)
    } catch (e: Exception) {
        valor
    }
}

