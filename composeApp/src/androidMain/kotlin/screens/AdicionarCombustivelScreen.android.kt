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
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AdicionarCombustivelScreen(
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
    var kmInicioViagem by remember { mutableStateOf("") }

    // Viagem em andamento (pega automaticamente)
    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }

    // Equipamentos (placas)
    var equipamentos by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }

    // Carrega dados ao iniciar (LOCAL primeiro, depois API)
    LaunchedEffect(Unit) {
        carregando = true

        // Carrega equipamentos do banco local
        equipamentos = repository.getEquipamentosParaDropdown()

        // 1. Verifica viagem LOCALMENTE primeiro
        val viagemLocal = repository.getViagemAtual()
        if (viagemLocal != null) {
            viagemEmAndamento = api.ViagemAberta(
                id = viagemLocal.viagem_id.toInt(),
                destino = viagemLocal.destino,
                data = viagemLocal.data_inicio,
                km_inicio = viagemLocal.km_inicio
            )
            kmInicioViagem = viagemLocal.km_inicio
            modoOffline = false
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
            if (response.status == "ok" && response.viagens.isNotEmpty()) {
                val viagem = response.viagens.first()
                viagemEmAndamento = viagem
                kmInicioViagem = viagem.km_inicio
                // Salva localmente para funcionar offline depois
                repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data)
                modoOffline = false

                // Atualiza equipamentos da API
                val syncResp = api.ApiClient.syncDados()
                if (syncResp.status == "ok") {
                    repository.salvarEquipamentos(syncResp.equipamentos.map { it.id to it.placa })
                    equipamentos = repository.getEquipamentosParaDropdown()
                }
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

    // Campos do formulário
    // ★ FIX PROCESS-DEATH: campos que perdiam valor ao abrir câmera
    // Pair<Long,String> não é diretamente rememberSaveable, separamos em dois campos
    var placaSelecionadaId by rememberSaveable { mutableStateOf(-1L) }
    var placaSelecionadaNome by rememberSaveable { mutableStateOf("") }
    val placaSelecionada: Pair<Long, String>? =
        if (placaSelecionadaId >= 0) Pair(placaSelecionadaId, placaSelecionadaNome) else null
    var data by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var nomePosto by rememberSaveable { mutableStateOf("") }
    var kmPosto by rememberSaveable { mutableStateOf("") }
    var tipoCombustivel by rememberSaveable { mutableStateOf("") }
    var horas by rememberSaveable { mutableStateOf("") }
    var litrosAbastecidos by rememberSaveableTextField("")
    var valorLitro by rememberSaveableTextField("")
    var valorTotal by rememberSaveableTextField("")
    var tipoPagamento by rememberSaveable { mutableStateOf("") }

    // Fotos
    val cameraStateMarcador = rememberCameraState(context, prefix = "COMB_MARC")
    val cameraStateCupom = rememberCameraState(context, prefix = "COMB_CUP")

    // Dropdowns
    var placaExpandida by remember { mutableStateOf(false) }
    var combustivelExpandido by remember { mutableStateOf(false) }

    // Camera
    var currentPhotoType by rememberSaveable { mutableStateOf("") }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val currentState = if (currentPhotoType == "marcador") cameraStateMarcador else cameraStateCupom
        if (success || currentState.checkPhotoExistsAfterCapture()) {
            when (currentPhotoType) {
                "marcador" -> cameraStateMarcador.onPhotoTaken()
                "cupom" -> cameraStateCupom.onPhotoTaken()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val uri = when (currentPhotoType) {
                    "marcador" -> cameraStateMarcador.prepareCapture()
                    else -> cameraStateCupom.prepareCapture()
                }
                cameraLauncher.launch(uri)
            } catch (e: Exception) { }
        }
    }

    // Photo Picker — não requer READ_MEDIA_IMAGES
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            when (currentPhotoType) {
                "marcador" -> cameraStateMarcador.onGalleryPicked(uri)
                "cupom" -> cameraStateCupom.onGalleryPicked(uri)
            }
        }
    }

    fun escolherDaGaleria(tipo: String) {
        currentPhotoType = tipo
        galleryLauncher.launch("image/*")
    }

    fun tirarFoto(tipo: String) {
        currentPhotoType = tipo
        try {
            val uri = when (tipo) {
                "marcador" -> cameraStateMarcador.prepareCapture()
                else -> cameraStateCupom.prepareCapture()
            }
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> cameraLauncher.launch(uri)
                else -> permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (e: Exception) { }
    }

    // Função salvar OFFLINE
    fun salvarLocal() {
        // Validação
        if (viagemEmAndamento == null) { erro = "Nenhuma viagem em andamento"; return }
        if (placaSelecionada == null) { erro = "Selecione uma placa"; return }
        if (nomePosto.isEmpty()) { erro = "Informe o nome do posto"; return }
        if (kmPosto.isEmpty()) { erro = "Informe o KM no posto"; return }
        if (tipoCombustivel.isEmpty()) { erro = "Selecione o tipo de combustível"; return }
        if (tipoCombustivel == "Diesel Aparelho" && horas.isEmpty()) { erro = "Informe as horas"; return }
        if (litrosAbastecidos.text.isEmpty()) { erro = "Informe os litros abastecidos"; return }
        if (valorLitro.text.isEmpty()) { erro = "Informe o valor do litro"; return }
        if (valorTotal.text.isEmpty()) { erro = "Informe o valor total"; return }
        if (tipoPagamento.isEmpty()) { erro = "Selecione a forma de pagamento"; return }
        if (cameraStateMarcador.base64 == null) { erro = "Tire a foto da quilometragem"; return }
        if (cameraStateCupom.base64 == null) { erro = "Tire a foto do cupom fiscal"; return }

        scope.launch {
            salvando = true
            erro = null
            try {
                // Salva localmente no SQLite
                repository.salvarAbastecimento(
                    motoristaId = motorista?.motorista_id ?: "",
                    viagemId = viagemEmAndamento!!.id.toLong(),
                    equipamentoId = placaSelecionada!!.first,
                    data = converterDataParaAPI(data),
                    valor = valorTotal.text,
                    litros = litrosAbastecidos.text,
                    posto = nomePosto,
                    kmPosto = kmPosto,
                    foto = cameraStateCupom.base64,
                    fotoMarcador = cameraStateMarcador.base64,
                    tipoPagamento = tipoPagamento,
                    tipoCombustivel = tipoCombustivel,
                    horas = horas.ifEmpty { null }
                )

                sucesso = "Abastecimento salvo! Sincronize quando tiver internet."

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
                title = "Adicionar Combustível",
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
                    CircularProgressIndicator(color = AppColors.Primary)
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
                // Aviso offline - SÓ APARECE QUANDO ESTÁ OFFLINE
                if (modoOffline) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text("Sem conexão. Conecte para registrar abastecimento.", color = AppColors.Orange, fontSize = 14.sp)
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
                            Text("Inicie uma viagem primeiro para registrar abastecimento.", color = AppColors.TextSecondary, fontSize = 14.sp)
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
                        colors = CardDefaults.cardColors(containerColor = AppColors.Primary.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Viagem em andamento", fontSize = 12.sp, color = AppColors.TextSecondary)
                                Text(viagemEmAndamento?.destino ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                                Text("Iniciada em: ${formatarDataBR(viagemEmAndamento?.data ?: "")}", fontSize = 12.sp, color = AppColors.TextSecondary)                            }
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
                                primaryColor = AppColors.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            // Placa
                            Text("Placa *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            ExposedDropdownMenuBox(expanded = placaExpandida, onExpandedChange = { placaExpandida = it }) {
                                OutlinedTextField(
                                    value = placaSelecionada?.second ?: "Selecione uma placa",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placaExpandida) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = AppColors.Primary) })
                                
                                ExposedDropdownMenu(expanded = placaExpandida, onDismissRequest = { placaExpandida = false }) {
                                    equipamentos.forEach { (id, placa) ->
                                        DropdownMenuItem(text = { Text(placa) }, onClick = { placaSelecionadaId = id; placaSelecionadaNome = placa; placaExpandida = false })
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Foto Quilometragem
                            Text("Foto Quilometragem *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            FotoCapturaAbastecimento(foto = cameraStateMarcador.bitmap, onClick = { tirarFoto("marcador") }, onEscolherGaleria = { escolherDaGaleria("marcador") }, onRemover = { cameraStateMarcador.clear() })

                            Spacer(Modifier.height(16.dp))

                            // Nome do Posto
                            Text("Nome do Posto *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = nomePosto,
                                onValueChange = { nomePosto = it },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Business, null, tint = AppColors.Primary) }
                            )

                            Spacer(Modifier.height(16.dp))

                            // KM no Posto
                            Text("KM no Posto *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = kmPosto,
                                onValueChange = { kmPosto = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.Speed, null, tint = AppColors.Primary) }
                            )

                            Spacer(Modifier.height(16.dp))

                            // Tipo de Combustível
                            Text("Tipo de Combustível *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            ExposedDropdownMenuBox(expanded = combustivelExpandido, onExpandedChange = { combustivelExpandido = it }) {
                                OutlinedTextField(
                                    value = tipoCombustivel.ifEmpty { "Selecione" },
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = combustivelExpandido) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                                    colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                    leadingIcon = { Icon(Icons.Default.LocalGasStation, null, tint = AppColors.Primary) }
                                )
                                ExposedDropdownMenu(expanded = combustivelExpandido, onDismissRequest = { combustivelExpandido = false }) {
                                    DropdownMenuItem(text = { Text("Diesel Caminhão") }, onClick = { tipoCombustivel = "Diesel Caminhão"; combustivelExpandido = false })
                                    DropdownMenuItem(text = { Text("Diesel Aparelho") }, onClick = { tipoCombustivel = "Diesel Aparelho"; combustivelExpandido = false })
                                }
                            }

                            // Campo Horas (só para Diesel Aparelho)
                            if (tipoCombustivel == "Diesel Aparelho") {
                                Spacer(Modifier.height(16.dp))
                                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Background)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Horas *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = horas,
                                            onValueChange = { if (it.length <= 5) horas = it.filter { c -> c.isDigit() } },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            leadingIcon = { Icon(Icons.Default.Schedule, null, tint = AppColors.Primary) }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Litros
                            Text("Litros Abastecidos *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = litrosAbastecidos,
                                onValueChange = { newValue ->
                                    val formatted = formatarDecimalComb(newValue.text)
                                    litrosAbastecidos = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.WaterDrop, null, tint = AppColors.Primary) },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                            

                            Spacer(Modifier.height(16.dp))

                            // Valor do Litro
                            Text("Valor do Litro *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = valorLitro,
                                onValueChange = { newValue ->
                                    val formatted = formatarValorLitroComb(newValue.text)
                                    valorLitro = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = AppColors.Primary) },
                                prefix = { Text("R$ ") },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                            

                            Spacer(Modifier.height(16.dp))

                            // Valor Total
                            Text("Valor Total *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = valorTotal,
                                onValueChange = { newValue ->
                                    val formatted = formatarMoedaComb(newValue.text)
                                    valorTotal = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.Calculate, null, tint = AppColors.Primary) },
                                prefix = { Text("R$ ") },
                                placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                            

                            // ★ Card de Consumo km/L (calculado automaticamente)
                            val kmPostoNum = kmPosto.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            val kmInicioNum = kmInicioViagem.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            val litrosNum = try {
                                litrosAbastecidos.text.replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                            } catch (_: Exception) { 0.0 }

                            if (kmPostoNum > 0 && kmInicioNum > 0 && litrosNum > 0 && kmPostoNum > kmInicioNum) {
                                val distancia = kmPostoNum - kmInicioNum
                                val kmPorLitro = distancia.toDouble() / litrosNum

                                Spacer(Modifier.height(16.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (kmPorLitro >= 2.5) Color(0xFF10B981).copy(alpha = 0.08f)
                                                         else if (kmPorLitro >= 1.8) Color(0xFFF59E0B).copy(alpha = 0.08f)
                                                         else Color(0xFFEF4444).copy(alpha = 0.08f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Speed, null,
                                                    tint = if (kmPorLitro >= 2.5) Color(0xFF10B981)
                                                           else if (kmPorLitro >= 1.8) Color(0xFFF59E0B)
                                                           else Color(0xFFEF4444),
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("Consumo Estimado", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextPrimary)
                                            }
                                            Surface(
                                                color = if (kmPorLitro >= 2.5) Color(0xFF10B981)
                                                        else if (kmPorLitro >= 1.8) Color(0xFFF59E0B)
                                                        else Color(0xFFEF4444),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Text(
                                                    String.format("%.2f km/L", kmPorLitro).replace(".", ","),
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("KM percorridos:", fontSize = 12.sp, color = AppColors.TextSecondary)
                                            Text("$distancia km", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("KM início da viagem:", fontSize = 12.sp, color = AppColors.TextSecondary)
                                            Text("$kmInicioNum km", fontSize = 12.sp, color = AppColors.TextSecondary)
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Forma de Pagamento
                            Text("Forma de Pagamento *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                BotaoOpcaoComb("CTF", Icons.Default.CreditCard, tipoPagamento == "ctf", { tipoPagamento = "ctf" }, Modifier.weight(1f))
                                BotaoOpcaoComb("Dinheiro", Icons.Default.Money, tipoPagamento == "dinheiro", { tipoPagamento = "dinheiro" }, Modifier.weight(1f))
                            }

                            Spacer(Modifier.height(16.dp))

                            // Foto Cupom Fiscal
                            Text("Foto Cupom Fiscal *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            FotoCapturaAbastecimento(foto = cameraStateCupom.bitmap, onClick = { tirarFoto("cupom") }, onEscolherGaleria = { escolherDaGaleria("cupom") }, onRemover = { cameraStateCupom.clear() })


                            Spacer(Modifier.height(24.dp))

                            // Botão Salvar
                            Button(
                                onClick = { salvarLocal() },
                                enabled = !salvando,
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                            ) {
                                if (salvando) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Save, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("SALVAR ABASTECIMENTO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                } // fecha else de semViagemAberta
            }
        } // fim do else (não carregando)
    }
}

@Composable
private fun FotoCapturaAbastecimento(foto: Bitmap?, onClick: () -> Unit, onEscolherGaleria: () -> Unit, onRemover: () -> Unit) {
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
        // Prioridade 2 - #9: Duas opções - câmera e galeria
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                    .border(2.dp, AppColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable { onClick() },
                color = AppColors.Primary.copy(alpha = 0.05f)
            ) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.CameraAlt, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Tirar Foto", color = AppColors.Primary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
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

@Composable
private fun BotaoOpcaoComb(texto: String, icone: androidx.compose.ui.graphics.vector.ImageVector, selecionado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(56.dp).clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        color = if (selecionado) AppColors.Primary else Color.White,
        border = androidx.compose.foundation.BorderStroke(2.dp, if (selecionado) AppColors.Primary else AppColors.TextSecondary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icone, null, tint = if (selecionado) Color.White else AppColors.TextSecondary)
            Spacer(Modifier.width(8.dp))
            Text(texto, color = if (selecionado) Color.White else AppColors.TextSecondary, fontWeight = FontWeight.Medium)
        }
    }
}

private fun formatarDecimalComb(input: String): String {
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

private fun formatarValorLitroComb(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    if (value == 0L) return ""  // Não mostra 0,00 quando vazio
    val decimal = value / 100.0
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
    symbols.decimalSeparator = ','
    symbols.groupingSeparator = '.'
    val formatter = java.text.DecimalFormat("#,##0.00", symbols)
    return formatter.format(decimal)
}

private fun formatarMoedaComb(input: String): String {
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
        // Converte de "2026-01-16" para "16/01/2026"
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