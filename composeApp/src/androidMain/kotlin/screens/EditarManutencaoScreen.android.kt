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
import util.dataAtualFormatada
import util.converterDataParaAPI
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.saveable.rememberSaveable
import util.rememberCameraState
import util.ImageCompressor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarManutencaoScreen(
    repository: AppRepository,
    manutencaoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Estados de carregamento
    var carregando by remember { mutableStateOf(true) }
    var modoOffline by remember { mutableStateOf(false) }

    // Estados do formulário
    var dataManutencao by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var placaSelecionada by remember { mutableStateOf<String?>(null) }
    var servicoSelecionado by rememberSaveable { mutableStateOf("Troca de Óleo") }
    var descricaoServico by rememberSaveable { mutableStateOf("") }
    var localManutencao by rememberSaveable { mutableStateOf("") }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var kmTrocaOleo by rememberSaveable { mutableStateOf("") }
    var kmTrocaPneu by rememberSaveable { mutableStateOf("") }

    // Estados para fotos
    val cameraState1 = rememberCameraState(context, prefix = "MANUT_1")
    val cameraState2 = rememberCameraState(context, prefix = "MANUT_2")

    // Estados para pneus selecionados (1-24)
    var pneusSelecionados by remember { mutableStateOf(setOf<Int>()) }
    var tiposPneu by remember { mutableStateOf(mapOf<Int, String>()) }

    // Estados de UI
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var sucesso by remember { mutableStateOf<String?>(null) }
    var expandedPlaca by remember { mutableStateOf(false) }
    var expandedServico by remember { mutableStateOf(false) }

    // Placas do banco local
    var placas by remember { mutableStateOf<List<String>>(emptyList()) }

    // Serviços disponíveis
    val servicos = listOf(
        "Troca de Óleo",
        "Troca de Pneu",
        "Borracharia (Remendo)",
        "Elétrica",
        "Mecânica",
        "Suspensão",
        "Aparelhos",
        "Outros"
    )

    // Tipos de pneu
    val tiposPneuOpcoes = listOf("Novo", "Recapado", "Usado")

    // Camera

    // Carrega dados ao iniciar
    LaunchedEffect(Unit) {
        carregando = true

        // Carrega placas localmente
        placas = repository.getEquipamentosParaDropdown().map { it.second }

        try {
            // Busca dados da manutenção da API
            val response = api.ApiClient.buscarManutencao(
                api.BuscarManutencaoRequest(
                    manutencao_id = manutencaoId,
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )

            if (response.status == "ok" && response.manutencao != null) {
                val manut = response.manutencao

                // Campos do formulário
                dataManutencao = formatarDataBRParaExibicao(manut.data_manutencao)
                placaSelecionada = manut.placa
                servicoSelecionado = manut.servico
                descricaoServico = manut.descricao_servico ?: ""
                localManutencao = manut.local_manutencao ?: ""
                valor = TextFieldValue(formatarDecimalParaExibicao(manut.valor))
                kmTrocaOleo = manut.km_troca_oleo ?: ""
                kmTrocaPneu = manut.km_troca_pneu ?: ""

                // Pneus (se for troca de pneu)
                if (!manut.pneus.isNullOrEmpty()) {
                    pneusSelecionados = manut.pneus.split(",")
                        .mapNotNull { it.toIntOrNull() }
                        .toSet()
                }

                // Tipos de pneu
                if (!manut.tipos_pneu.isNullOrEmpty()) {
                    tiposPneu = manut.tipos_pneu.split(";")
                        .mapNotNull { parte ->
                            val split = parte.split(":")
                            if (split.size == 2) {
                                split[0].toIntOrNull()?.let { it to split[1] }
                            } else null
                        }
                        .toMap()
                }

                // Carrega fotos se existirem

                if (!manut.foto_comprovante1.isNullOrEmpty()) {
                    cameraState1.loadExisting(manut.foto_comprovante1)
                    cameraState1.loadExisting(manut.foto_comprovante1)
                }

                if (!manut.foto_comprovante2.isNullOrEmpty()) {
                    cameraState2.loadExisting(manut.foto_comprovante2)
                    cameraState2.loadExisting(manut.foto_comprovante2)
                }

                modoOffline = false
            } else {
                erro = "Manutenção não encontrada"
            }
        } catch (e: Exception) {
            modoOffline = true
            erro = "Erro ao carregar dados: ${e.message}"
        }

        carregando = false
    }

    var currentPhotoTarget by rememberSaveable { mutableStateOf(0) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val currentState = if (currentPhotoTarget == 1) cameraState1 else cameraState2
        if (success || currentState.checkPhotoExistsAfterCapture()) {
            when (currentPhotoTarget) {
                1 -> cameraState1.onPhotoTaken()
                2 -> cameraState2.onPhotoTaken()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val uri = when (currentPhotoTarget) {
                    1 -> cameraState1.prepareCapture()
                    else -> cameraState2.prepareCapture()
                }
                cameraLauncher.launch(uri)
            } catch (e: Exception) { }
        }
    }

    fun tirarFoto(target: Int) {
        currentPhotoTarget = target
        try {
            val uri = when (target) {
                1 -> cameraState1.prepareCapture()
                else -> cameraState2.prepareCapture()
            }
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                    cameraLauncher.launch(uri)
                }
                else -> {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) { }
    }

    // Função para atualizar
    fun atualizarManutencao() {
        // Validações
        if (placaSelecionada.isNullOrBlank()) { erro = "Selecione uma placa"; return }
        if (valor.text.isEmpty()) { erro = "Informe o valor"; return }
        if (servicoSelecionado == "Troca de Óleo" && kmTrocaOleo.isBlank()) { erro = "Informe o KM da troca de óleo"; return }
        if (servicoSelecionado == "Troca de Pneu") {
            if (kmTrocaPneu.isBlank()) { erro = "Informe o KM da troca de pneu"; return }
            if (pneusSelecionados.isEmpty()) { erro = "Selecione pelo menos um pneu"; return }
            for (pneu in pneusSelecionados) {
                if (tiposPneu[pneu].isNullOrBlank()) { erro = "Selecione o tipo para o pneu $pneu"; return }
            }
        }

        scope.launch {
            salvando = true
            erro = null

            val dataManutencaoAPI = converterDataParaAPI(dataManutencao)

            try {
                val response = api.ApiClient.atualizarManutencao(
                    api.AtualizarManutencaoRequest(
                        manutencao_id = manutencaoId,
                        motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemId,
                        data_manutencao = dataManutencaoAPI,
                        placa = placaSelecionada!!,
                        servico = servicoSelecionado,
                        descricao_servico = descricaoServico,
                        local_manutencao = localManutencao,
                        valor = valor.text,
                        km_troca_oleo = if (servicoSelecionado == "Troca de Óleo") kmTrocaOleo else null,
                        km_troca_pneu = if (servicoSelecionado == "Troca de Pneu") kmTrocaPneu else null,
                        pneus = pneusSelecionados.toList(),
                        tipos_pneu = tiposPneu,
                        foto_comprovante1 = cameraState1.base64,
                        foto_comprovante2 = cameraState2.base64
                    )
                )

                if (response.status == "ok") {
                    sucesso = "Manutenção atualizada com sucesso!"
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
                title = "Editar Manutenção",
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
                    CircularProgressIndicator(color = Color(0xFF8B5CF6))
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
                            Text("Sem conexão. Conecte para editar manutenção.", color = AppColors.Orange, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                        // Data
                        DateInputField(
                            value = dataManutencao,
                            onValueChange = { dataManutencao = it },
                            label = "Data da Manutenção *",
                            primaryColor = Color(0xFF8B5CF6),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        // Placa
                        Text("Placa *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
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
                                leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF8B5CF6)) },
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

                        // Serviço
                        Text("Serviço *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedServico,
                            onExpandedChange = { expandedServico = it }
                        ) {
                            OutlinedTextField(
                                value = servicoSelecionado,
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Build, null, tint = Color(0xFF8B5CF6)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedServico) })
                            
                            ExposedDropdownMenu(
                                expanded = expandedServico,
                                onDismissRequest = { expandedServico = false }
                            ) {
                                servicos.forEach { servico ->
                                    DropdownMenuItem(
                                        text = { Text(servico) },
                                        onClick = {
                                            servicoSelecionado = servico
                                            expandedServico = false
                                            if (servico != "Troca de Pneu") {
                                                pneusSelecionados = emptySet()
                                                tiposPneu = emptyMap()
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Campo KM Troca Óleo (condicional)
                        if (servicoSelecionado == "Troca de Óleo") {
                            Spacer(Modifier.height(16.dp))
                            Text("KM da Troca (Óleo) *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = kmTrocaOleo,
                                onValueChange = { kmTrocaOleo = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF8B5CF6)) },
                                placeholder = { Text("Ex: 150000", color = Color(0xFF9CA3AF)) },
                                singleLine = true
                            )
                        }

                        // Campo KM Troca Pneu (condicional)
                        if (servicoSelecionado == "Troca de Pneu") {
                            Spacer(Modifier.height(16.dp))
                            Text("KM da Troca (Pneu) *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = kmTrocaPneu,
                                onValueChange = { kmTrocaPneu = it.filter { c -> c.isDigit() } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF8B5CF6)) },
                                placeholder = { Text("Ex: 150000", color = Color(0xFF9CA3AF)) },
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Descrição
                        Text("Descrição do Serviço", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = descricaoServico,
                            onValueChange = { descricaoServico = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Description, null, tint = Color(0xFF8B5CF6)) },
                            placeholder = { Text("Descreva o serviço realizado", color = Color(0xFF9CA3AF)) },
                            maxLines = 4
                        )

                        Spacer(Modifier.height(16.dp))

                        // Local
                        Text("Local da Manutenção", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = localManutencao,
                            onValueChange = { localManutencao = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF8B5CF6)) },
                            placeholder = { Text("Ex: Oficina Central", color = Color(0xFF9CA3AF)) },
                            singleLine = true
                        )

                        Spacer(Modifier.height(16.dp))

                        // Valor
                        Text("Valor *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = valor,
                            onValueChange = { newValue ->
                                val formatted = formatarMoedaManutencao(newValue.text)
                                valor = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF8B5CF6)) },
                            prefix = { Text("R$ ") },
                            placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) },
                            singleLine = true
                        )

                        // Seção de Pneus (condicional)
                        if (servicoSelecionado == "Troca de Pneu") {
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = AppColors.Background)
                            Spacer(Modifier.height(16.dp))

                            Text(
                                "Selecione os Pneus",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(16.dp))

                            ChassiPneusEdit(
                                pneusSelecionados = pneusSelecionados,
                                onPneuToggle = { pneu ->
                                    pneusSelecionados = if (pneu in pneusSelecionados) {
                                        pneusSelecionados - pneu
                                    } else {
                                        pneusSelecionados + pneu
                                    }
                                }
                            )

                            if (pneusSelecionados.isNotEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                HorizontalDivider(color = AppColors.Background)
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Tipo de cada Pneu",
                                    fontWeight = FontWeight.Medium,
                                    color = AppColors.TextPrimary
                                )
                                Spacer(Modifier.height(12.dp))

                                pneusSelecionados.sorted().forEach { pneu ->
                                    TipoPneuSelectorEdit(
                                        pneuNumero = pneu,
                                        tipoSelecionado = tiposPneu[pneu] ?: "",
                                        opcoes = tiposPneuOpcoes,
                                        onTipoChange = { tipo ->
                                            tiposPneu = tiposPneu + (pneu to tipo)
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Fotos
                        Text("Fotos dos Comprovantes", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))

                        FotoCapturaManutencaoEdit(
                            foto = cameraState1.bitmap,
                            onClick = { tirarFoto(1) },
                            onRemover = { cameraState1.clear() },
                            titulo = "Comprovante 1"
                        )

                        Spacer(Modifier.height(12.dp))

                        FotoCapturaManutencaoEdit(
                            foto = cameraState2.bitmap,
                            onClick = { tirarFoto(2) },
                            onRemover = { cameraState2.clear() },
                            titulo = "Comprovante 2 (Opcional)"
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
                            onClick = { atualizarManutencao() },
                            enabled = !salvando && !modoOffline,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ATUALIZAR MANUTENÇÃO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
private fun FotoCapturaManutencaoEdit(foto: Bitmap?, onClick: () -> Unit, onRemover: () -> Unit, titulo: String) {
    if (foto != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(titulo, fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = foto.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentScale = ContentScale.Crop
                    )

                    IconButton(
                        onClick = onRemover,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
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
                                modifier = Modifier.padding(8.dp).size(16.dp)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Foto capturada",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onClick() },
                    color = Color(0xFF8B5CF6).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tirar nova foto",
                            color = Color(0xFF8B5CF6),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable { onClick() },
            color = Color(0xFF8B5CF6).copy(alpha = 0.05f)
        ) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(titulo, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
        }
    }
}

// Componentes de Pneus (mesmos da tela de adicionar)
@Composable
private fun ChassiPneusEdit(
    pneusSelecionados: Set<Int>,
    onPneuToggle: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.width(8.dp).height(4.dp).background(Color(0xFF374151), RoundedCornerShape(4.dp)))
        Text("Eixo Dianteiro", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusEdit(listOf(1, 2), pneusSelecionados, onPneuToggle)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 2", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusEdit(listOf(3, 4), pneusSelecionados, onPneuToggle)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 3 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEdit(listOf(5, 6, 7, 8), pneusSelecionados, onPneuToggle)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 4 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEdit(listOf(9, 10, 11, 12), pneusSelecionados, onPneuToggle)
        Box(modifier = Modifier.width(4.dp).height(30.dp).background(Color(0xFF9CA3AF)))
        Text("── Carreta ──", fontSize = 10.sp, color = AppColors.TextSecondary)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 5", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEdit(listOf(13, 14, 15, 16), pneusSelecionados, onPneuToggle)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 6", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEdit(listOf(17, 18, 19, 20), pneusSelecionados, onPneuToggle)
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))
        Text("Eixo 7", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuploEdit(listOf(21, 22, 23, 24), pneusSelecionados, onPneuToggle)
    }
}

@Composable
private fun EixoPneusEdit(pneus: List<Int>, pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Box(modifier = Modifier.width(100.dp).padding(start = 16.dp), contentAlignment = Alignment.CenterStart) {
            PneuCheckboxEdit(pneus[0], pneus[0] in pneusSelecionados) { onPneuToggle(pneus[0]) }
        }
        Box(modifier = Modifier.weight(1f).height(12.dp).background(Color(0xFF4B5563), RoundedCornerShape(6.dp)))
        Box(modifier = Modifier.width(100.dp).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
            PneuCheckboxEdit(pneus[1], pneus[1] in pneusSelecionados) { onPneuToggle(pneus[1]) }
        }
    }
}

@Composable
private fun EixoPneusDuploEdit(pneus: List<Int>, pneusSelecionados: Set<Int>, onPneuToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PneuCheckboxEdit(pneus[0], pneus[0] in pneusSelecionados) { onPneuToggle(pneus[0]) }
            PneuCheckboxEdit(pneus[1], pneus[1] in pneusSelecionados) { onPneuToggle(pneus[1]) }
        }
        Box(modifier = Modifier.weight(1f).height(12.dp).background(Color(0xFF4B5563), RoundedCornerShape(6.dp)))
        Row(modifier = Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PneuCheckboxEdit(pneus[2], pneus[2] in pneusSelecionados) { onPneuToggle(pneus[2]) }
            PneuCheckboxEdit(pneus[3], pneus[3] in pneusSelecionados) { onPneuToggle(pneus[3]) }
        }
    }
}

@Composable
private fun PneuCheckboxEdit(numero: Int, selecionado: Boolean, onClick: () -> Unit) {
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
private fun TipoPneuSelectorEdit(pneuNumero: Int, tipoSelecionado: String, opcoes: List<String>, onTipoChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Pneu $pneuNumero:", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
            OutlinedTextField(
                value = if (tipoSelecionado.isBlank()) "Selecione..." else tipoSelecionado,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(8.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) })
            
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                opcoes.forEach { opcao ->
                    DropdownMenuItem(text = { Text(opcao) }, onClick = { onTipoChange(opcao); expanded = false })
                }
            }
        }
    }
}

private fun formatarMoedaManutencao(input: String): String {
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

private fun formatarDataBRParaExibicao(data: String): String {
    return try {
        val partes = data.split("-")
        if (partes.size == 3) "${partes[2]}/${partes[1]}/${partes[0]}" else data
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

