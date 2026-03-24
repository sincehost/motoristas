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
import api.*
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.DateInputField
import util.rememberCameraState
import util.rememberSaveableTextField
import util.dataAtualFormatada
import util.converterDataParaAPI
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AdicionarDescargaScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
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

    // Viagem em andamento
    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }

    // Placas disponíveis
    var placas by remember { mutableStateOf<List<String>>(emptyList()) }

    // Carrega viagem em andamento ao iniciar
    LaunchedEffect(Unit) {
        carregando = true

        // 1. Verifica LOCALMENTE primeiro
        val viagemLocal = repository.getViagemAtual()
        if (viagemLocal != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemLocal.viagem_id.toInt(),
                destino = viagemLocal.destino,
                data = viagemLocal.data_inicio
            )
            modoOffline = false

            // Carrega placas localmente
            placas = repository.getEquipamentosParaDropdown().map { it.second }

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
            if (response.status == "ok") {
                // Carrega placas
                placas = response.placas

                // Verifica viagem
                if (response.viagens.isNotEmpty()) {
                    val viagem = response.viagens.first()
                    viagemEmAndamento = viagem
                    // Salva localmente
                    repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data)
                    modoOffline = false
                } else {
                    semViagemAberta = true
                }
            } else {
                semViagemAberta = true
            }
        } catch (e: Exception) {
            // Sem internet e sem viagem local
            modoOffline = true
            semViagemAberta = true
            // Tenta carregar placas localmente
            placas = repository.getEquipamentosParaDropdown().map { it.second }
        }
        carregando = false
    }

    // ★ FIX PROCESS-DEATH: campos sobrevivem quando câmera mata o processo
    var data by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var placaSelecionada by rememberSaveable { mutableStateOf("") }
    var expandedPlaca by remember { mutableStateOf(false) }
    var ordemDescarga by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveableTextField("")

    // Foto
    val cameraState = rememberCameraState(context, prefix = "DESC")

    // Camera

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

    // Função salvar
    fun salvarDescarga() {
        // Validações ANTES de tentar salvar
        if (viagemEmAndamento == null) {
            erro = "Nenhuma viagem em andamento"
            return
        }
        if (data.isEmpty()) {
            erro = "Informe a data"
            return
        }
        if (placaSelecionada.isBlank()) {
            erro = "Selecione a placa"
            return
        }
        if (ordemDescarga.isEmpty()) {
            erro = "Informe a ordem de descarga"
            return
        }
        if (valor.text.isEmpty()) {
            erro = "Informe o valor"
            return
        }

        // VALIDAÇÃO CRÍTICA: Foto é obrigatória
        if (cameraState.base64.isNullOrBlank()) {
            erro = "Tire a foto do comprovante"
            return
        }

        val dataAPI = converterDataParaAPI(data)

        scope.launch {
            salvando = true
            erro = null
            try {
                // PRIMEIRO: Tentar API
                try {
                    val response = api.ApiClient.salvarDescarga(
                        api.SalvarDescargaRequest(
                            motorista_id = motorista?.motorista_id ?: "",
                            viagem_id = viagemEmAndamento!!.id,
                            data = dataAPI,
                            placa = placaSelecionada,
                            ordem_descarga = ordemDescarga.toIntOrNull() ?: 0,
                            valor = valor.text,
                            foto = cameraState.base64!! // Garantido que não é null pela validação acima
                        )
                    )
                    if (response.status == "ok") {
                        sucesso = "Descarga registrada com sucesso!"
                    } else {
                        erro = response.mensagem ?: "Erro ao salvar"
                    }
                } catch (e: Exception) {
                    if (cameraState.base64.isNullOrBlank()) {
                        erro = "Foto do comprovante é obrigatória"
                        return@launch
                    }

                    repository.salvarDescarga(
                        motoristaId = motorista?.motorista_id ?: "",
                        viagemId = viagemEmAndamento!!.id.toLong(),
                        data = dataAPI,
                        placa = placaSelecionada,
                        ordemDescarga = ordemDescarga.toLongOrNull() ?: 0,
                        valor = valor.text,
                        foto = cameraState.base64!!
                    )
                    sucesso = "Descarga salva! Sincronize quando tiver internet."
                }

            } catch (e: Exception) {
                erro = "Erro ao salvar: ${e.message}"
            }
            salvando = false
        }
    }

    // Diálogos modais de erro e sucesso
    if (erro != null) {
        ui.ErroDialog(mensagem = erro!!, onDismiss = { erro = null })
    }
    if (sucesso != null) {
        ui.SucessoDialog(mensagem = sucesso!!, onDismiss = { sucesso = null; onSucesso() })
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Adicionar Descarga",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
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
                            Text("Sem conexão. Conecte para registrar descarga.", color = AppColors.Orange, fontSize = 14.sp)
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
                        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, null, tint = AppColors.Error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Nenhuma viagem em andamento", fontWeight = FontWeight.Bold, color = AppColors.Error, fontSize = 18.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Inicie uma viagem primeiro para registrar descarga.", color = AppColors.TextSecondary, fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onVoltar, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                                Text("Voltar")
                            }
                        }
                    }
                } else {
                    // Card da viagem em andamento
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF273159).copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Viagem em andamento", fontSize = 12.sp, color = AppColors.TextSecondary)
                                Text(viagemEmAndamento?.destino ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                                Text("Iniciada em: ${formatarDataBR(viagemEmAndamento?.data ?: "")}", fontSize = 12.sp, color = AppColors.TextSecondary)
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
                                    value = if (placaSelecionada.isBlank()) "Selecione a placa" else placaSelecionada,
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
                            FotoCapturaDescargaScreen(
                                foto = cameraState.bitmap,
                                onClick = { tirarFoto() },
                                onRemover = { cameraState.clear() }
                            )


                            Spacer(Modifier.height(24.dp))

                            // Botão Salvar
                            Button(
                                onClick = { salvarDescarga() },
                                enabled = !salvando,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
                            ) {
                                if (salvando) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Save, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("SALVAR DESCARGA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
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
private fun FotoCapturaDescargaScreen(foto: Bitmap?, onClick: () -> Unit, onRemover: () -> Unit) {
    if (foto != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = foto.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            IconButton(onClick = onRemover, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Surface(color = AppColors.Error, shape = RoundedCornerShape(50)) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF1E88E5).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable { onClick() },
            color = Color(0xFF1E88E5).copy(alpha = 0.05f)
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text("Tirar Foto", color = Color(0xFF1E88E5), fontWeight = FontWeight.Medium)
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