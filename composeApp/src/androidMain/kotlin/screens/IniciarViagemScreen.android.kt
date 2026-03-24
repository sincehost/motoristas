package screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
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
import api.ApiClient
import api.ViagemRequest
import database.AppRepository
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI
import androidx.compose.runtime.saveable.rememberSaveable
import util.rememberCameraState
import util.rememberSaveableTextField
import util.ImageCompressor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun IniciarViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val destinos = remember { repository.getAllDestinos() }
    val equipamentos = remember { repository.getAllEquipamentos() }

    // Estados de verificação de viagem em andamento
    var carregando by remember { mutableStateOf(true) }
    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }

    // Verifica se já tem viagem em andamento (LOCAL primeiro, depois API)
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
                // Salva localmente para funcionar offline depois
                // Preserva km_inicio local se API retornar vazio
                val kmInicioExistente = repository.getViagemAtual()?.km_inicio ?: ""
                val kmInicioParaSalvar = if (viagem.km_inicio.isNotEmpty()) viagem.km_inicio else kmInicioExistente
                repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data, kmInicioParaSalvar)
            }
        } catch (e: Exception) {
            // Sem internet e sem viagem local - permite iniciar
        }
        carregando = false
    }

    var numerobd by rememberSaveable { mutableStateOf("") }
    var cte by rememberSaveable { mutableStateOf("") }
    // ★ FIX PROCESS-DEATH: todos os campos do formulário usam rememberSaveable
    // para sobreviver quando o Android mata o processo ao abrir a câmera
    var mostrarCampos2 by rememberSaveable { mutableStateOf(false) }
    var numerobd2 by rememberSaveable { mutableStateOf("") }
    var cte2 by rememberSaveable { mutableStateOf("") }

    var destinoExpandido by remember { mutableStateOf(false) }
    // destinoSelecionado: salva id e nome como strings separadas
    var destinoSelecionadoId by rememberSaveable { mutableStateOf(-1L) }
    var destinoSelecionadoNome by rememberSaveable { mutableStateOf("") }
    val destinoSelecionado: Pair<Long, String>? =
        if (destinoSelecionadoId >= 0) Pair(destinoSelecionadoId, destinoSelecionadoNome) else null

    var placaExpandida by remember { mutableStateOf(false) }
    var placaSelecionada by rememberSaveable { mutableStateOf("") }

    var dataViagem by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var kmInicio by rememberSaveable { mutableStateOf("") }
    var pesoCarga by rememberSaveableTextField("")
    var valorFrete by rememberSaveableTextField("0,00")
    val cameraState = rememberCameraState(context, prefix = "VIAG")

    var loading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Mensagens modais
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success || cameraState.checkPhotoExistsAfterCapture()) {
            cameraState.onPhotoTaken()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = cameraState.prepareCapture()
            cameraLauncher.launch(uri)
        }
    }

    fun abrirCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                val uri = cameraState.prepareCapture()
                cameraLauncher.launch(uri)
            }
            else -> permissionLauncher.launch(Manifest.permission.CAMERA)
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
                title = "Iniciar Viagem",
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
                        data.visuals.message.contains("salva", ignoreCase = true) ||
                        data.visuals.message.contains("capturada", ignoreCase = true))
                        AppColors.Secondary else AppColors.Error,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    actionColor = Color.White
                )
            }
        }
    ) { padding ->
        // Carregando
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Verificando viagens...", color = AppColors.TextSecondary)
                }
            }
        }
        // Já tem viagem em andamento - BLOQUEIA
        else if (viagemEmAndamento != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppColors.Background)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Warning, null, tint = AppColors.Orange, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Viagem em andamento", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppColors.Orange)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Você já possui uma viagem em andamento. Finalize-a antes de iniciar uma nova.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))

                        // Card com info da viagem atual
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary, modifier = Modifier.size(40.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Destino:", fontSize = 12.sp, color = AppColors.TextSecondary)
                                    Text(viagemEmAndamento!!.destino, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Iniciada em: ${formatarData(viagemEmAndamento!!.data)}",
                                        fontSize = 12.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = onVoltar,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Voltar ao Início", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        // Pode iniciar viagem
        else {
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = numerobd,
                                onValueChange = { numerobd = it },
                                label = { Text("Ordem de Frete *") },
                                leadingIcon = { Icon(Icons.Default.Tag, null, tint = AppColors.Primary) },
                                modifier = Modifier.weight(1f),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { mostrarCampos2 = !mostrarCampos2 }) {
                                Icon(
                                    if (mostrarCampos2) Icons.Default.RemoveCircle else Icons.Default.AddCircle,
                                    "Adicionar segundo",
                                    tint = AppColors.Primary
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = cte,
                            onValueChange = { cte = it },
                            label = { Text("Nº do CTE *") },
                            leadingIcon = { Icon(Icons.Default.Description, null, tint = AppColors.Primary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        if (mostrarCampos2) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = numerobd2,
                                onValueChange = { numerobd2 = it },
                                label = { Text("Ordem de Frete 2") },
                                leadingIcon = { Icon(Icons.Default.Tag, null, tint = AppColors.TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = cte2,
                                onValueChange = { cte2 = it },
                                label = { Text("Nº do CTE 2") },
                                leadingIcon = { Icon(Icons.Default.Description, null, tint = AppColors.TextSecondary) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        ExposedDropdownMenuBox(expanded = destinoExpandido, onExpandedChange = { destinoExpandido = it }) {
                            OutlinedTextField(
                                value = destinoSelecionado?.second ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Rota *") },
                                leadingIcon = { Icon(Icons.Default.Route, null, tint = AppColors.Primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinoExpandido) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = destinoExpandido, onDismissRequest = { destinoExpandido = false }) {
                                destinos.forEach { destino ->
                                    DropdownMenuItem(
                                        text = { Text(destino.nome) },
                                        onClick = {
                                            destinoSelecionadoId = destino.servidor_id
                                            destinoSelecionadoNome = destino.nome
                                            destinoExpandido = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        ExposedDropdownMenuBox(expanded = placaExpandida, onExpandedChange = { placaExpandida = it }) {
                            OutlinedTextField(
                                value = placaSelecionada,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Placa do Cavalo *") },
                                leadingIcon = { Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = placaExpandida) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(expanded = placaExpandida, onDismissRequest = { placaExpandida = false }) {
                                equipamentos.forEach { equip ->
                                    DropdownMenuItem(
                                        text = { Text(equip.placa) },
                                        onClick = {
                                            placaSelecionada = equip.placa
                                            placaExpandida = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        DateInputField(
                            value = dataViagem,
                            onValueChange = { dataViagem = it },
                            label = "Data da Viagem *",
                            primaryColor = AppColors.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = kmInicio,
                            onValueChange = { kmInicio = it.filter { c -> c.isDigit() } },
                            label = { Text("KM de Início *") },
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = AppColors.Primary) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = pesoCarga,
                            onValueChange = { newValue ->
                                val digits = newValue.text.filter { c -> c.isDigit() }.take(5)
                                val formatted = formatarPeso(digits)

                                // Atualiza com cursor sempre no final
                                pesoCarga = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            label = { Text("Peso da Carga (kg) *") },
                            leadingIcon = { Icon(Icons.Default.Scale, null, tint = AppColors.Primary) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valorFrete,
                            onValueChange = { newValue ->
                                // Remove tudo exceto dígitos
                                val digits = newValue.text.filter { c -> c.isDigit() }.take(9)
                                val formatted = formatarValor(digits)

                                // Atualiza com cursor sempre no final
                                valorFrete = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            label = { Text("Valor do Frete (R\$)") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = AppColors.Primary) },
                            placeholder = { Text("0,00") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Foto do Painel de Saída *", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))

                        if (cameraState.hasPhoto) {
                            Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))) {
                                Image(
                                    bitmap = cameraState.bitmap!!.asImageBitmap(),
                                    contentDescription = "Foto",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        cameraState.clear()
                                        
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(36.dp).background(Color.Red, RoundedCornerShape(50))
                                ) {
                                    Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = { abrirCamera() },
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(36.dp).background(AppColors.Primary, RoundedCornerShape(50))
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
                                    .border(2.dp, AppColors.Primary, RoundedCornerShape(12.dp))
                                    .background(AppColors.Primary.copy(alpha = 0.05f))
                                    .clickable { abrirCamera() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CameraAlt, "Tirar foto", modifier = Modifier.size(48.dp), tint = AppColors.Primary)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Toque para tirar foto", color = AppColors.Primary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    if (numerobd.isBlank()) { mostrarMensagem("Informe a Ordem de Frete", isErro = true); return@launch }
                                    if (cte.isBlank()) { mostrarMensagem("Informe o Nº do CTE", isErro = true); return@launch }
                                    if (destinoSelecionado == null) { mostrarMensagem("Selecione uma Rota", isErro = true); return@launch }
                                    if (placaSelecionada.isBlank()) { mostrarMensagem("Selecione a Placa", isErro = true); return@launch }
                                    if (dataViagem.isBlank()) { mostrarMensagem("Informe a Data da Viagem", isErro = true); return@launch }
                                    if (kmInicio.isBlank()) { mostrarMensagem("Informe o KM de Início", isErro = true); return@launch }
                                    if (pesoCarga.text.isBlank()) { mostrarMensagem("Informe o Peso da Carga", isErro = true); return@launch }
                                    
                                    // Validação do valor do frete - não pode ser vazio, 0,00 ou apenas zeros
                                    val freteDigits = valorFrete.text.replace(".", "").replace(",", "").replace(" ", "")
                                    if (valorFrete.text.isBlank() || freteDigits.toLongOrNull() == 0L || freteDigits.isEmpty()) {
                                        mostrarMensagem("O valor do frete não pode ser R\$ 0,00. Informe um valor válido.", isErro = true)
                                        return@launch
                                    }
                                    
                                    if (cameraState.base64 == null) { mostrarMensagem("Tire a foto do painel de saída", isErro = true); return@launch }

                                    loading = true

                                    // Data de criação no formato ISO8601
                                    val dataCriacao = java.text.SimpleDateFormat(
                                        "yyyy-MM-dd'T'HH:mm:ss'Z'",
                                        java.util.Locale.getDefault()
                                    ).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                    }.format(java.util.Date())

                                    val dataViagemAPI = converterDataParaAPI(dataViagem)

                                    // Converte valor do frete para decimal para API
                                    val valorFreteParaAPI = run {
                                        val valorLimpo = valorFrete.text.replace(".", "").replace(",", "")
                                        val valorInt = valorLimpo.toIntOrNull() ?: 0
                                        val reais = valorInt / 100
                                        val centavos = valorInt % 100
                                        "${reais}.${centavos.toString().padStart(2, '0')}"
                                    }

                                    // PRIMEIRO: Tentar API
                                    try {
                                        val response = ApiClient.inserirViagem(
                                            ViagemRequest(

                                                motorista_id = motorista?.motorista_id ?: "",
                                                numerobd = numerobd,
                                                cte = cte,
                                                numerobd2 = numerobd2.ifBlank { null },
                                                cte2 = cte2.ifBlank { null },
                                                destino_id = destinoSelecionado!!.first.toInt(),
                                                data_viagem = dataViagemAPI,
                                                km_inicio = kmInicio,
                                                placa = placaSelecionada,
                                                pesocarga = pesoCarga.text,
                                                valorfrete = valorFreteParaAPI,
                                                foto_painel_saida = cameraState.base64
                                            )
                                        )

                                        if (response.status == "ok") {
                                            // API salvou com sucesso
                                            val viagemIdReal = response.viagem_id ?: 0
                                            repository.salvarViagemAtual(
                                                viagemId = viagemIdReal.toLong(),
                                                destino = destinoSelecionado!!.second,
                                                dataInicio = dataViagemAPI,
                                                kmInicio = kmInicio
                                            )
                                            mostrarMensagem("Viagem registrada com sucesso!")
                                        } else {
                                            // API retornou erro de VALIDAÇÃO (dados inválidos, motorista não encontrado, etc.)
                                            // NÃO salvar localmente — registros com erro de validação nunca serão aceitos pelo servidor
                                            loading = false
                                            
                                            // Traduzir mensagens técnicas do servidor para mensagens amigáveis
                                            val mensagemOriginal = response.mensagem ?: ""
                                            val mensagemAmigavel = when {
                                                mensagemOriginal.contains("valorfrete", ignoreCase = true) && mensagemOriginal.contains("null", ignoreCase = true) ->
                                                    "O valor do frete não pode ser vazio. Informe um valor válido."
                                                mensagemOriginal.contains("pesocarga", ignoreCase = true) && mensagemOriginal.contains("null", ignoreCase = true) ->
                                                    "O peso da carga não pode ser vazio. Informe um valor válido."
                                                mensagemOriginal.contains("cannot be null", ignoreCase = true) || mensagemOriginal.contains("SQLSTATE", ignoreCase = true) ->
                                                    "Alguns campos obrigatórios estão vazios. Verifique todos os campos e tente novamente."
                                                mensagemOriginal.contains("motorista", ignoreCase = true) ->
                                                    "Motorista não encontrado. Faça login novamente."
                                                mensagemOriginal.isNotEmpty() -> mensagemOriginal
                                                else -> "Dados inválidos. Verifique as informações e tente novamente."
                                            }
                                            
                                            mostrarMensagem(mensagemAmigavel, isErro = true)
                                            return@launch
                                        }
                                    } catch (e: Exception) {
                                        // Exceção de REDE (sem internet, timeout, servidor indisponível)
                                        // Salvar localmente para sincronização posterior
                                        val semConexao = e.message?.let {
                                            it.contains("Unable to resolve host", ignoreCase = true) ||
                                            it.contains("No address associated with hostname", ignoreCase = true) ||
                                            it.contains("Network is unreachable", ignoreCase = true) ||
                                            it.contains("Connection refused", ignoreCase = true) ||
                                            it.contains("timeout", ignoreCase = true) ||
                                            it.contains("ConnectException", ignoreCase = true) ||
                                            it.contains("SocketTimeoutException", ignoreCase = true)
                                        } ?: true

                                        if (!semConexao) {
                                            // Erro técnico inesperado — não salvar offline
                                            loading = false
                                            mostrarMensagem("Erro inesperado: ${e.message}", isErro = true)
                                            return@launch
                                        }

                                        repository.salvarViagem(
                                            numerobd = numerobd,
                                            cte = cte,
                                            numerobd2 = numerobd2.ifBlank { null },
                                            cte2 = cte2.ifBlank { null },
                                            destinoId = destinoSelecionado!!.first,
                                            destinoNome = destinoSelecionado!!.second,
                                            placa = placaSelecionada,
                                            dataViagem = dataViagemAPI,
                                            kmInicio = kmInicio,
                                            pesoCarga = pesoCarga.text,
                                            valorFrete = valorFreteParaAPI,
                                            fotoPainelSaida = cameraState.base64,
                                            dataCriacao = dataCriacao
                                        )
                                        val viagens = repository.getViagensParaSincronizar()
                                        val idLocal = viagens.lastOrNull()?.id ?: java.lang.System.currentTimeMillis()
                                        repository.salvarViagemAtual(
                                            viagemId = -idLocal,
                                            destino = destinoSelecionado!!.second,
                                            dataInicio = dataViagemAPI,
                                            kmInicio = kmInicio
                                        )
                                        mostrarMensagem("Sem internet. Viagem salva e será sincronizada automaticamente quando conectar.")
                                    }

                                    loading = false
                                }
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("SALVAR VIAGEM", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

// ============================================
// FUNÇÕES DE FORMATAÇÃO
// ============================================

private fun formatarData(data: String): String {
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

private fun formatarPeso(digits: String): String {
    if (digits.isEmpty()) return ""

    // Formata com ponto como separador de milhar
    // Exemplo: "12345" → "12.345"
    return digits.reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
}

/**
 * Formata valor monetário com comportamento de calculadora
 *
 * Comportamento:
 * - "2" → "0,02"
 * - "25" → "0,25"
 * - "250" → "2,50"
 * - "2500" → "25,00"
 * - "25000" → "250,00"
 * - "123456" → "1.234,56"
 */
private fun formatarValor(digits: String): String {
    if (digits.isEmpty()) return "0,00"

    val numero = digits.toLongOrNull() ?: return "0,00"

    // Divide por 100 para obter reais e centavos
    val reais = numero / 100
    val centavos = numero % 100

    // Formata reais com ponto como separador de milhar
    val reaisFormatado = if (reais == 0L) {
        "0"
    } else {
        reais.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
    }

    // Formata centavos sempre com 2 dígitos
    val centavosFormatado = centavos.toString().padStart(2, '0')

    // Retorna no formato: 0,02 ou 25,00 ou 1.234,56
    return "$reaisFormatado,$centavosFormatado"
}