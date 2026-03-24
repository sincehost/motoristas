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
import api.*
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
import util.rememberSaveableTextField
import util.ImageCompressor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun ManutencaoScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // ★ FIX PROCESS-DEATH: todos os campos sobrevivem quando câmera mata o processo
    var dataManutencao by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var placaSelecionada by rememberSaveable { mutableStateOf("") }
    var servicoSelecionado by rememberSaveable { mutableStateOf("Troca de Óleo") }
    var descricaoServico by rememberSaveable { mutableStateOf("") }
    var localManutencao by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveableTextField("")
    var kmTrocaOleo by rememberSaveable { mutableStateOf("") }
    var kmTrocaPneu by rememberSaveable { mutableStateOf("") }

    // Estados para fotos
    val cameraState1 = rememberCameraState(context, prefix = "MANUT_1")
    val cameraState2 = rememberCameraState(context, prefix = "MANUT_2")

    // Estados para pneus — serializado como String para sobreviver ao process death
    // pneusSelecionadosStr: "1,3,5"  |  tiposPneuStr: "1:Novo;3:Recapado"
    var pneusSelecionadosStr by rememberSaveable { mutableStateOf("") }
    var tiposPneuStr by rememberSaveable { mutableStateOf("") }

    // Funções auxiliares de parse — usadas inline no lugar das propriedades computadas
    fun parsePneus(): Set<Int> =
        if (pneusSelecionadosStr.isBlank()) emptySet()
        else pneusSelecionadosStr.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()

    fun parseTipos(): Map<Int, String> =
        if (tiposPneuStr.isBlank()) emptyMap()
        else tiposPneuStr.split(";").mapNotNull {
            val parts = it.split(":")
            if (parts.size == 2) parts[0].toIntOrNull()?.let { k -> k to parts[1] } else null
        }.toMap()

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
    var currentPhotoTarget by rememberSaveable { mutableStateOf(0) } // 1 ou 2

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

    // Photo Picker — não requer READ_MEDIA_IMAGES
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            when (currentPhotoTarget) {
                1 -> cameraState1.onGalleryPicked(uri)
                2 -> cameraState2.onGalleryPicked(uri)
            }
        }
    }

    fun escolherDaGaleria(target: Int) {
        currentPhotoTarget = target
        galleryLauncher.launch("image/*")
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

    // Carregar placas
    LaunchedEffect(Unit) {
        placas = repository.getEquipamentosParaDropdown().map { it.second }

        try {
            val response = ApiClient.abastecimentoDados(
                AbastecimentoDadosRequest(
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )
            if (response.status == "ok" && response.placas.isNotEmpty()) {
                placas = response.placas
            }
        } catch (e: Exception) { /* usa local */ }
    }

    // Função para salvar
    fun salvarManutencao() {
        // Validações
        if (placaSelecionada.isBlank()) { erro = "Selecione uma placa"; return }
        if (valor.text.isEmpty()) { erro = "Informe o valor"; return }
        if (servicoSelecionado == "Troca de Óleo" && kmTrocaOleo.isBlank()) { erro = "Informe o KM da troca de óleo"; return }
        if (servicoSelecionado == "Troca de Pneu") {
            if (kmTrocaPneu.isBlank()) { erro = "Informe o KM da troca de pneu"; return }
            if (parsePneus().isEmpty()) { erro = "Selecione pelo menos um pneu"; return }
            for (pneu in parsePneus()) {
                if (parseTipos()[pneu].isNullOrBlank()) { erro = "Selecione o tipo para o pneu $pneu"; return }
            }
        }

        scope.launch {
            salvando = true
            erro = null

            val dataManutencaoAPI = converterDataParaAPI(dataManutencao)

            try {
                val viagemAtual = repository.getViagemAtual()
                val viagemId = viagemAtual?.viagem_id ?: 0L

                try {
                    val response = ApiClient.salvarManutencao(
                        SalvarManutencaoRequest(
                            motorista_id = motorista?.motorista_id ?: "",
                            viagem_id = viagemId.toInt(),
                            data_manutencao = dataManutencaoAPI,
                            placa = placaSelecionada,
                            servico = servicoSelecionado,
                            descricao_servico = descricaoServico,
                            local_manutencao = localManutencao,
                            valor = valor.text,
                            km_troca_oleo = if (servicoSelecionado == "Troca de Óleo") kmTrocaOleo else null,
                            km_troca_pneu = if (servicoSelecionado == "Troca de Pneu") kmTrocaPneu else null,
                            pneus = parsePneus().toList(),
                            tipos_pneu = parseTipos(),
                            foto_comprovante1 = cameraState1.base64,
                            foto_comprovante2 = cameraState2.base64
                        )
                    )
                    if (response.status == "ok") {
                        sucesso = "Manutenção registrada com sucesso!"
                    } else {
                        erro = response.mensagem ?: "Erro ao salvar"
                    }
                } catch (e: Exception) {
                    repository.salvarManutencao(
                        motoristaId = motorista?.motorista_id ?: "",
                        viagemId = viagemId,
                        dataManutencao = dataManutencaoAPI,
                        placa = placaSelecionada,
                        servico = servicoSelecionado,
                        descricaoServico = descricaoServico,
                        localManutencao = localManutencao,
                        valor = valor.text,
                        kmTrocaOleo = if (servicoSelecionado == "Troca de Óleo") kmTrocaOleo else null,
                        kmTrocaPneu = if (servicoSelecionado == "Troca de Pneu") kmTrocaPneu else null,
                        pneus = pneusSelecionadosStr,
                        tiposPneu = tiposPneuStr,
                        fotoComprovante1 = cameraState1.base64,
                        fotoComprovante2 = cameraState2.base64
                    )
                    sucesso = "Manutenção salva! Sincronize quando tiver internet."
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
        ui.SucessoDialog(mensagem = sucesso!!, onDismiss = { sucesso = null; onVoltar() })
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Adicionar Manutenção",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Background)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
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
                            value = if (placaSelecionada.isBlank()) "Selecione a placa" else placaSelecionada,
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
                                            pneusSelecionadosStr = ""
                                            tiposPneuStr = ""
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

                        ChassiPneus(
                            pneusSelecionados = parsePneus(),
                            onPneuToggle = { pneu ->
                                val atual = parsePneus()
                                val novo = if (pneu in atual) atual - pneu else atual + pneu
                                pneusSelecionadosStr = novo.joinToString(",")
                            }
                        )

                        if (parsePneus().isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            HorizontalDivider(color = AppColors.Background)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Tipo de cada Pneu",
                                fontWeight = FontWeight.Medium,
                                color = AppColors.TextPrimary
                            )
                            Spacer(Modifier.height(12.dp))

                            parsePneus().sorted().forEach { pneu ->
                                TipoPneuSelector(
                                    pneuNumero = pneu,
                                    tipoSelecionado = parseTipos()[pneu] ?: "",
                                    opcoes = tiposPneuOpcoes,
                                    onTipoChange = { tipo ->
                                        val tiposAtuais = parseTipos().toMutableMap()
                                        tiposAtuais[pneu] = tipo
                                        tiposPneuStr = tiposAtuais.entries.joinToString(";") { "${it.key}:${it.value}" }
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

                    FotoCapturaManutencao(
                        foto = cameraState1.bitmap,
                        onClick = { tirarFoto(1) },
                        onEscolherGaleria = { escolherDaGaleria(1) },
                        onRemover = { cameraState1.clear() },
                        titulo = "Comprovante 1"
                    )

                    Spacer(Modifier.height(12.dp))

                    FotoCapturaManutencao(
                        foto = cameraState2.bitmap,
                        onClick = { tirarFoto(2) },
                        onEscolherGaleria = { escolherDaGaleria(2) },
                        onRemover = { cameraState2.clear() },
                        titulo = "Comprovante 2 (Opcional)"
                    )


                    Spacer(Modifier.height(24.dp))

                    // Botão Salvar
                    Button(
                        onClick = { salvarManutencao() },
                        enabled = !salvando,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("SALVAR MANUTENÇÃO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FotoCapturaManutencao(foto: Bitmap?, onClick: () -> Unit, onEscolherGaleria: () -> Unit, onRemover: () -> Unit, titulo: String) {
    if (foto != null) {
        Column {
            Text(titulo, fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = foto.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                IconButton(onClick = onRemover, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Surface(color = AppColors.Error, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    } else {
        Column {
            Text(titulo, fontSize = 12.sp, color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            // Prioridade 2 - #9: Duas opções - câmera e galeria
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable { onClick() },
                    color = Color(0xFF8B5CF6).copy(alpha = 0.05f)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Tirar Foto", color = Color(0xFF8B5CF6), fontWeight = FontWeight.Medium, fontSize = 11.sp)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0xFF1976D2).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).clickable { onEscolherGaleria() },
                    color = Color(0xFF1976D2).copy(alpha = 0.05f)
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF1976D2), modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Da Galeria", color = Color(0xFF1976D2), fontWeight = FontWeight.Medium, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChassiPneus(
    pneusSelecionados: Set<Int>,
    onPneuToggle: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Linha central do chassi
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(4.dp)
                .background(Color(0xFF374151), RoundedCornerShape(4.dp))
        )

        // Eixo 1 (2 pneus - dianteiro)
        Text("Eixo Dianteiro", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneus(
            pneus = listOf(1, 2),
            pneusSelecionados = pneusSelecionados,
            onPneuToggle = onPneuToggle
        )

        // Conexão
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        // Eixo 2 (2 pneus)
        Text("Eixo 2", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneus(
            pneus = listOf(3, 4),
            pneusSelecionados = pneusSelecionados,
            onPneuToggle = onPneuToggle
        )

        // Conexão
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        // Eixo 3 (4 pneus - rodado duplo)
        Text("Eixo 3 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuplo(
            pneus = listOf(5, 6, 7, 8),
            pneusSelecionados = pneusSelecionados,
            onPneuToggle = onPneuToggle
        )

        // Conexão
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        // Eixo 4 (4 pneus - rodado duplo)
        Text("Eixo 4 (Tração)", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuplo(
            pneus = listOf(9, 10, 11, 12),
            pneusSelecionados = pneusSelecionados,
            onPneuToggle = onPneuToggle
        )

        // Conexão (carreta)
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(30.dp)
                .background(Color(0xFF9CA3AF))
        )
        Text("── Carreta ──", fontSize = 10.sp, color = AppColors.TextSecondary)

        // Conexão
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        // Eixo 5 (4 pneus)
        Text("Eixo 5", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuplo(
            pneus = listOf(13, 14, 15, 16),
            pneusSelecionados = pneusSelecionados,
            onPneuToggle = onPneuToggle
        )

        // Conexão
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        // Eixo 6 (4 pneus)
        Text("Eixo 6", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuplo(
            pneus = listOf(17, 18, 19, 20),
            pneusSelecionados = pneusSelecionados,
            onPneuToggle = onPneuToggle
        )

        // Conexão
        Box(modifier = Modifier.width(8.dp).height(20.dp).background(Color(0xFF374151)))

        // Eixo 7 (4 pneus)
        Text("Eixo 7", fontSize = 12.sp, color = AppColors.TextSecondary)
        Spacer(Modifier.height(4.dp))
        EixoPneusDuplo(
            pneus = listOf(21, 22, 23, 24),
            pneusSelecionados = pneusSelecionados,
            onPneuToggle = onPneuToggle
        )
    }
}

@Composable
private fun EixoPneus(
    pneus: List<Int>,
    pneusSelecionados: Set<Int>,
    onPneuToggle: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Lado esquerdo
        Box(
            modifier = Modifier
                .width(100.dp)
                .padding(start = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            PneuCheckbox(
                numero = pneus[0],
                selecionado = pneus[0] in pneusSelecionados,
                onClick = { onPneuToggle(pneus[0]) }
            )
        }

        // Eixo central
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .background(Color(0xFF4B5563), RoundedCornerShape(6.dp))
        )

        // Lado direito
        Box(
            modifier = Modifier
                .width(100.dp)
                .padding(end = 16.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            PneuCheckbox(
                numero = pneus[1],
                selecionado = pneus[1] in pneusSelecionados,
                onClick = { onPneuToggle(pneus[1]) }
            )
        }
    }
}

@Composable
private fun EixoPneusDuplo(
    pneus: List<Int>,
    pneusSelecionados: Set<Int>,
    onPneuToggle: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lado esquerdo (2 pneus)
        Row(
            modifier = Modifier.padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PneuCheckbox(
                numero = pneus[0],
                selecionado = pneus[0] in pneusSelecionados,
                onClick = { onPneuToggle(pneus[0]) }
            )
            PneuCheckbox(
                numero = pneus[1],
                selecionado = pneus[1] in pneusSelecionados,
                onClick = { onPneuToggle(pneus[1]) }
            )
        }

        // Eixo central
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .background(Color(0xFF4B5563), RoundedCornerShape(6.dp))
        )

        // Lado direito (2 pneus)
        Row(
            modifier = Modifier.padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PneuCheckbox(
                numero = pneus[2],
                selecionado = pneus[2] in pneusSelecionados,
                onClick = { onPneuToggle(pneus[2]) }
            )
            PneuCheckbox(
                numero = pneus[3],
                selecionado = pneus[3] in pneusSelecionados,
                onClick = { onPneuToggle(pneus[3]) }
            )
        }
    }
}

@Composable
private fun PneuCheckbox(
    numero: Int,
    selecionado: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(32.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selecionado) Color(0xFF3B82F6) else Color(0xFF1F2937)
            )
            .border(
                width = 2.dp,
                color = if (selecionado) Color(0xFF1D4ED8) else Color(0xFF374151),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = numero.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipoPneuSelector(
    pneuNumero: Int,
    tipoSelecionado: String,
    opcoes: List<String>,
    onTipoChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Pneu $pneuNumero:",
            modifier = Modifier.width(70.dp),
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedTextField(
                value = if (tipoSelecionado.isBlank()) "Selecione..." else tipoSelecionado,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(8.dp),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) })
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                opcoes.forEach { opcao ->
                    DropdownMenuItem(
                        text = { Text(opcao) },
                        onClick = {
                            onTipoChange(opcao)
                            expanded = false
                        }
                    )
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