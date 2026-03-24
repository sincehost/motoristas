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
import util.rememberSaveableTextField
import util.dataAtualFormatada
import util.converterDataParaAPI
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AdicionarArlaScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Estados
    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }
    var modoOffline by remember { mutableStateOf(false) }
    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }

    // ★ FIX PROCESS-DEATH: valor e litros sobrevivem quando câmera mata o processo
    var data by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var valor by rememberSaveableTextField("")
    var litros by rememberSaveableTextField("")
    var posto by rememberSaveable { mutableStateOf("") }
    var kmPosto by rememberSaveable { mutableStateOf("") }

    // Foto
    val cameraState = rememberCameraState(context, prefix = "ARLA")

    // Mensagens de erro/sucesso (dialog modal)
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Função para mostrar mensagens profissionais
    fun mostrarMensagem(mensagem: String, erro: Boolean = false) {
        if (erro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Carrega viagem em andamento ao iniciar
    LaunchedEffect(Unit) {
        carregando = true

        // 1. Verifica LOCALMENTE primeiro (ViagemAtual)
        val viagemLocal = repository.getViagemAtual()
        if (viagemLocal != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemLocal.viagem_id.toInt(),
                destino = viagemLocal.destino,
                data = viagemLocal.data_inicio
            )
            modoOffline = false
            carregando = false
            return@LaunchedEffect
        }

        // 2. Se não tem ViagemAtual, verifica viagem NÃO SINCRONIZADA (offline)
        val viagemNaoSincronizada = repository.getUltimaViagemNaoSincronizada()
        if (viagemNaoSincronizada != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemNaoSincronizada.id.toInt(),
                destino = viagemNaoSincronizada.destino_nome,
                data = viagemNaoSincronizada.data_viagem
            )
            modoOffline = true
            carregando = false
            return@LaunchedEffect
        }

        // 3. Se não tem local, tenta API
        try {
            val response = api.ApiClient.arlaDados(
                api.ArlaDadosRequest(
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )
            if (response.status == "ok" && response.viagens.isNotEmpty()) {
                val viagem = response.viagens.first()
                viagemEmAndamento = viagem
                // Salva localmente para funcionar offline depois
                repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data)
                modoOffline = false
            } else {
                // Nenhuma viagem em andamento
                semViagemAberta = true
            }
        } catch (e: Exception) {
            // Sem internet e sem viagem local
            modoOffline = true
            semViagemAberta = true
        }
        carregando = false
    }

    // Funções de foto
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

    // Photo Picker — não requer READ_MEDIA_IMAGES
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            cameraState.onGalleryPicked(uri)
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
    fun salvarLocal() {
        // Validações
        if (viagemEmAndamento == null) {
            mostrarMensagem("Nenhuma viagem em andamento", erro = true)
            return
        }
        if (data.isEmpty()) {
            mostrarMensagem("Informe a data", erro = true)
            return
        }
        if (valor.text.isEmpty() || valor.text.replace(",", ".").toDoubleOrNull() == 0.0) {
            mostrarMensagem("Informe o valor", erro = true)
            return
        }
        if (litros.text.isEmpty() || litros.text.replace(",", ".").toDoubleOrNull() == 0.0) {
            mostrarMensagem("Informe os litros", erro = true)
            return
        }
        if (posto.isEmpty()) {
            mostrarMensagem("Informe o nome do posto", erro = true)
            return
        }
        if (kmPosto.isEmpty()) {
            mostrarMensagem("Informe o KM no posto", erro = true)
            return
        }
        if (cameraState.base64 == null) {
            mostrarMensagem("Tire a foto do comprovante", erro = true)
            return
        }

        val dataAPI = converterDataParaAPI(data)

        scope.launch {
            salvando = true
            try {
                // PRIMEIRO: Tentar API
                try {
                    val response = api.ApiClient.salvarArla(
                        api.SalvarArlaRequest(
                            motorista_id = motorista?.motorista_id ?: "",
                            viagem_id = viagemEmAndamento!!.id,
                            data = dataAPI,
                            valor = valor.text,
                            litros = litros.text,
                            posto = posto,
                            km_posto = kmPosto,
                            foto_base64 = cameraState.base64
                        )
                    )
                    if (response.status == "ok") {
                        // API salvou com sucesso
                        mostrarMensagem("${response.mensagem ?: "ARLA registrado com sucesso!"}")
                    } else {
                        mostrarMensagem(response.mensagem ?: "Erro ao salvar", erro = true)
                    }
                } catch (e: Exception) {
                    // Sem internet - salvar localmente para sincronização
                    repository.salvarArla(
                        motoristaId = motorista?.motorista_id ?: "",
                        viagemId = viagemEmAndamento!!.id.toLong(),
                        data = dataAPI,
                        valor = valor.text,
                        litros = litros.text,
                        posto = posto,
                        kmPosto = kmPosto,
                        foto = cameraState.base64
                    )
                    mostrarMensagem("ARLA salvo! Sincronize quando tiver internet.")
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro: ${e.message}", erro = true)
            }
            salvando = false
        }
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) {
        ui.ErroDialog(mensagem = erroMsg!!, onDismiss = { erroMsg = null })
    }
    if (sucessoMsg != null) {
        ui.SucessoDialog(mensagem = sucessoMsg!!, onDismiss = { sucessoMsg = null; onSucesso() })
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Adicionar Arla",
                onBackClick = onVoltar
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = if (data.visuals.message.contains("Erro") ||
                            data.visuals.message.contains("negada") ||
                            data.visuals.message.contains("Informe") ||
                            data.visuals.message.contains("duplicado") ||
                            data.visuals.message.contains("Nenhuma"))
                            AppColors.Error
                        else
                            AppColors.Secondary,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(16.dp)
                    )
                }
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
                    CircularProgressIndicator(color = Color(0xFF06B6D4))
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
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Modo Offline. ARLA será salvo para sincronizar depois.",
                                color = AppColors.Orange,
                                fontSize = 14.sp
                            )
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
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = AppColors.Error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Nenhuma viagem em andamento",
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Error,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Inicie uma viagem primeiro para registrar ARLA.",
                                color = AppColors.TextSecondary,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onVoltar,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
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
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                null,
                                tint = Color(0xFF06B6D4),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Viagem em andamento",
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                                Text(
                                    viagemEmAndamento?.destino ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = AppColors.TextPrimary
                                )
                                Text(
                                    "Iniciada em: ${formatarDataBR(viagemEmAndamento?.data ?: "")}",
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            // Data
                            DateInputField(
                                value = data,
                                onValueChange = { data = it },
                                label = "Data *",
                                primaryColor = Color(0xFF06B6D4),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            // Valor
                            Text(
                                "Valor *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = valor,
                                onValueChange = { newValue ->
                                    val formatted = formatarMoedaArla(newValue.text)
                                    valor = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AttachMoney,
                                        null,
                                        tint = Color(0xFF06B6D4)
                                    )
                                },
                                prefix = { Text("R$ ") },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) }
                            )

                            Spacer(Modifier.height(16.dp))

                            // Litros
                            Text(
                                "Litros *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = litros,
                                onValueChange = { newValue ->
                                    val formatted = formatarLitrosArla(newValue.text)
                                    litros = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.WaterDrop,
                                        null,
                                        tint = Color(0xFF06B6D4)
                                    )
                                },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                            

                            Spacer(Modifier.height(16.dp))

                            // Nome do Posto
                            Text(
                                "Nome do Posto *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = posto,
                                onValueChange = { posto = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Business,
                                        null,
                                        tint = Color(0xFF06B6D4)
                                    )
                                })
                            

                            Spacer(Modifier.height(16.dp))

                            // KM no Posto
                            Text(
                                "KM no Posto *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = kmPosto,
                                onValueChange = { kmPosto = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Speed,
                                        null,
                                        tint = Color(0xFF06B6D4)
                                    )
                                })
                            

                            Spacer(Modifier.height(16.dp))

                            // Foto do Comprovante
                            Text(
                                "Foto do Comprovante *",
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            FotoCapturaArlaScreen(
                                foto = cameraState.bitmap,
                                onClick = { tirarFoto() },
                                onEscolherGaleria = { galleryLauncher.launch("image/*") },
                                onRemover = {
                                    cameraState.clear()
                                    
                                }
                            )

                            Spacer(Modifier.height(24.dp))

                            // Botão Salvar
                            Button(
                                onClick = { salvarLocal() },
                                enabled = !salvando,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                            ) {
                                if (salvando) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Save, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "SALVAR ARLA",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
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
private fun FotoCapturaArlaScreen(
    foto: Bitmap?,
    onClick: () -> Unit,
    onEscolherGaleria: () -> Unit,
    onRemover: () -> Unit
) {
    if (foto != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = foto.asImageBitmap(),
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
            ) {
                Surface(
                    color = AppColors.Error,
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(
                        Icons.Default.Close,
                        null,
                        tint = Color.White,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    } else {
        // Prioridade 2 - #9: Duas opções - câmera e galeria
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF06B6D4).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable { onClick() },
                color = Color(0xFF06B6D4).copy(alpha = 0.05f)
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF06B6D4), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Tirar Foto", color = Color(0xFF06B6D4), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
            Surface(
                modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF1976D2).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable { onEscolherGaleria() },
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
}

private fun formatarMoedaArla(input: String): String {
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

private fun formatarLitrosArla(input: String): String {
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