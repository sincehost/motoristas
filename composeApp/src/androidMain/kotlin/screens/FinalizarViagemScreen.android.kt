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
import util.rememberCameraState
import util.rememberSaveableTextField
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun FinalizarViagemScreen(
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

    // Viagem em andamento
    var viagemAtual by remember { mutableStateOf<br.com.lfsystem.app.database.ViagemAtual?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }
    var kmInicioViagem by remember { mutableStateOf("") }
    var kmRotaDestino by remember { mutableStateOf(0.0) } // KM esperado do destino

    // Carrega viagem atual e busca km_inicio + km_rota
    LaunchedEffect(Unit) {
        carregando = true
        viagemAtual = repository.getViagemAtual()
        if (viagemAtual == null) {
            semViagemAberta = true
        } else {
            val viagemId = viagemAtual!!.viagem_id

            // 1. Carregar dados locais salvos na ViagemAtual
            val kmLocal = viagemAtual?.km_inicio ?: ""
            val kmRotaLocal = viagemAtual?.km_rota ?: "0"
            if (kmLocal.isNotEmpty()) kmInicioViagem = kmLocal
            if (kmRotaLocal != "0" && kmRotaLocal.isNotEmpty()) {
                kmRotaDestino = kmRotaLocal.toDoubleOrNull() ?: 0.0
            }

            // 2. Se falta km_inicio OU km_rota, buscar de fontes externas
            val precisaKmInicio = kmInicioViagem.isEmpty()
            val precisaKmRota = kmRotaDestino <= 0.0

            if (precisaKmInicio || precisaKmRota) {
                if (viagemId < 0) {
                    // OFFLINE: viagem criada offline (viagem_id negativo)
                    val idLocal = -viagemId
                    val viagemOffline = repository.getViagensParaSincronizar()
                        .firstOrNull { it.id == idLocal }
                    if (viagemOffline != null && viagemOffline.km_inicio.isNotEmpty()) {
                        kmInicioViagem = viagemOffline.km_inicio
                        repository.salvarViagemAtual(
                            viagemId = viagemId,
                            destino = viagemAtual!!.destino,
                            dataInicio = viagemAtual!!.data_inicio,
                            kmInicio = viagemOffline.km_inicio,
                            kmRota = kmRotaLocal // preserva o que já tem
                        )
                    }
                } else if (viagemId > 0) {
                    // ONLINE: tentar API para buscar km_inicio + km_rota do destino
                    try {
                        val resp = api.ApiClient.detalheViagem(
                            api.ViagemDetalheRequest(viagem_id = viagemId.toInt())
                        )
                        if (resp.status == "ok" && resp.viagem != null) {
                            if (precisaKmInicio) kmInicioViagem = resp.viagem.km_inicio
                            val kmRotaApi = resp.viagem.km_rota.toDoubleOrNull() ?: 0.0
                            if (kmRotaApi > 0.0) kmRotaDestino = kmRotaApi

                            // Salvar TUDO localmente para funcionar offline depois
                            repository.salvarViagemAtual(
                                viagemId = viagemId,
                                destino = viagemAtual!!.destino,
                                dataInicio = viagemAtual!!.data_inicio,
                                kmInicio = if (kmInicioViagem.isNotEmpty()) kmInicioViagem else (viagemAtual?.km_inicio ?: ""),
                                kmRota = if (kmRotaDestino > 0.0) kmRotaDestino.toLong().toString() else "0"
                            )
                        }
                    } catch (e: Exception) {
                        // FALLBACK: API falhou (offline) — buscar da tabela Viagem local
                        if (precisaKmInicio) {
                            val viagensLocais = repository.getAllViagens()
                            val viagemLocal = viagensLocais.firstOrNull { viagem ->
                                viagem.km_inicio.isNotEmpty()
                            }
                            if (viagemLocal != null) {
                                kmInicioViagem = viagemLocal.km_inicio
                                repository.salvarViagemAtual(
                                    viagemId = viagemId,
                                    destino = viagemAtual!!.destino,
                                    dataInicio = viagemAtual!!.data_inicio,
                                    kmInicio = viagemLocal.km_inicio,
                                    kmRota = kmRotaLocal
                                )
                            }
                        }
                        // km_rota offline: já carregou do local se tinha, senão fica 0
                    }
                }
            }
        }
        carregando = false
    }

    // ★ FIX PROCESS-DEATH: todos os campos sobrevivem quando câmera mata o processo
    var kmChegada by rememberSaveable { mutableStateOf("") }
    var dataChegada by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var observacao by rememberSaveable { mutableStateOf("") }
    var teveRetorno by rememberSaveable { mutableStateOf(false) }

    // Campos de retorno (opcionais) - COM TextFieldValue persistido
    var pesoCargaRetorno by rememberSaveableTextField("")
    var valorFreteRetorno by rememberSaveableTextField("")
    var localCarregou by rememberSaveable { mutableStateOf("") }
    var ordemRetorno by rememberSaveable { mutableStateOf("") }
    var cteRetorno by rememberSaveable { mutableStateOf("") }

    // Foto
    val cameraState = rememberCameraState(context, prefix = "FIN")

    // Diálogo de confirmação antes de finalizar (Prioridade 2 - #8)
    var mostrarDialogoConfirmacao by remember { mutableStateOf(false) }
    // Diálogo de aviso de KM suspeito
    var mostrarAvisoKm by remember { mutableStateOf(false) }
    var mensagemAvisoKm by remember { mutableStateOf("") }

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

    // Galeria — GetContent funciona em TODOS os Androids (inclusive Xiaomi/Poco/MIUI)
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

    // Função de validação antes de mostrar diálogo de confirmação
    fun validarEConfirmar() {
        if (viagemAtual == null) { erro = "Nenhuma viagem em andamento"; return }
        if (kmChegada.isEmpty()) { erro = "Informe o KM de chegada"; return }
        if (dataChegada.isEmpty()) { erro = "Informe a data de chegada"; return }
        if (cameraState.base64 == null) { erro = "Tire a foto do painel"; return }

        // ═══════════════════════════════════════════════════
        // VALIDAÇÃO PROFISSIONAL DE KM
        // ═══════════════════════════════════════════════════
        val kmChegadaNum = kmChegada.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val kmInicioStr = kmInicioViagem.ifEmpty { viagemAtual?.km_inicio ?: "" }
        val kmInicioNum = kmInicioStr.toDoubleOrNull()?.toLong()
            ?: kmInicioStr.filter { it.isDigit() }.toLongOrNull()
            ?: 0L
        val kmRotaEsperado = kmRotaDestino.toLong()

        // 1. KM de chegada deve ser > 0
        if (kmChegadaNum <= 0) {
            erro = "KM de chegada deve ser maior que zero"
            return
        }

        // 2. KM de chegada DEVE ser maior que KM de início (BLOQUEIO)
        if (kmInicioNum > 0 && kmChegadaNum <= kmInicioNum) {
            erro = "KM de chegada ($kmChegada) deve ser maior que KM de início (${kmInicioStr.toDoubleOrNull()?.toLong() ?: kmInicioStr})\n\nVerifique se digitou corretamente."
            return
        }

        // 3. Calcular km percorrido e validar contra a rota do destino
        if (kmInicioNum > 0) {
            val kmPercorrido = kmChegadaNum - kmInicioNum

            // 3a. KM percorrido absurdamente alto (mais de 5x a rota) = provavelmente erro de digitação (BLOQUEIO)
            if (kmRotaEsperado > 0 && kmPercorrido > kmRotaEsperado * 5) {
                val kmEsperadoFinal = kmInicioNum + kmRotaEsperado
                erro = "KM de chegada parece incorreto!\n\n" +
                        "• KM Início: $kmInicioNum\n" +
                        "• KM Chegada digitado: $kmChegadaNum\n" +
                        "• KM Percorrido: $kmPercorrido km\n" +
                        "• KM da Rota: $kmRotaEsperado km\n\n" +
                        "O km percorrido é ${kmPercorrido / kmRotaEsperado}x maior que a rota. " +
                        "O KM esperado seria próximo de $kmEsperadoFinal.\n\n" +
                        "Verifique se digitou corretamente."
                return
            }

            // 3b. KM percorrido muito alto sem rota definida (>50.000 km em uma viagem) = provavelmente erro (BLOQUEIO)
            if (kmRotaEsperado <= 0 && kmPercorrido > 50000) {
                erro = "KM de chegada parece incorreto!\n\n" +
                        "• KM Início: $kmInicioNum\n" +
                        "• KM Chegada digitado: $kmChegadaNum\n" +
                        "• KM Percorrido: $kmPercorrido km\n\n" +
                        "Mais de 50.000 km em uma viagem é improvável. Verifique se digitou corretamente."
                return
            }

            // 3c. KM percorrido ultrapassa a rota em mais de 5 km = AVISO (não bloqueia, mas confirma)
            if (kmRotaEsperado > 0 && kmPercorrido > kmRotaEsperado + 5) {
                val excedente = kmPercorrido - kmRotaEsperado
                // Se ultrapassa mais de 3x a rota, é erro de digitação — já bloqueado acima (5x)
                // Entre 1x e 5x, avisa mas permite
                mensagemAvisoKm = "Atenção: KM ultrapassado!\n\n" +
                        "• KM Início: $kmInicioNum\n" +
                        "• KM Chegada: $kmChegadaNum\n" +
                        "• KM Percorrido: $kmPercorrido km\n" +
                        "• KM da Rota: $kmRotaEsperado km\n" +
                        "• Excedente: +$excedente km\n\n" +
                        "Deseja continuar mesmo assim?"
                mostrarAvisoKm = true
                return
            }
        }

        // Validação dos campos de retorno
        if (teveRetorno) {
            if (pesoCargaRetorno.text.isEmpty()) { erro = "Informe o peso da carga de retorno"; return }
            if (valorFreteRetorno.text.isEmpty()) { erro = "Informe o valor do frete de retorno"; return }
            if (localCarregou.isEmpty()) { erro = "Informe o local onde carregou"; return }
            if (ordemRetorno.isEmpty()) { erro = "Informe a ordem de retorno"; return }
            if (cteRetorno.isEmpty()) { erro = "Informe o CTE de retorno"; return }
        }

        erro = null
        // Prioridade 2 - #8: Mostrar diálogo de confirmação
        mostrarDialogoConfirmacao = true
    }

    // Função chamada quando o motorista confirma o aviso de KM e quer prosseguir
    fun prosseguirAposAvisoKm() {
        mostrarAvisoKm = false
        // Revalidar campos de retorno
        if (teveRetorno) {
            if (pesoCargaRetorno.text.isEmpty()) { erro = "Informe o peso da carga de retorno"; return }
            if (valorFreteRetorno.text.isEmpty()) { erro = "Informe o valor do frete de retorno"; return }
            if (localCarregou.isEmpty()) { erro = "Informe o local onde carregou"; return }
            if (ordemRetorno.isEmpty()) { erro = "Informe a ordem de retorno"; return }
            if (cteRetorno.isEmpty()) { erro = "Informe o CTE de retorno"; return }
        }
        erro = null
        mostrarDialogoConfirmacao = true
    }

    // Função finalizar viagem (chamada após confirmação)
    fun finalizarViagem() {

        val dataChegadaAPI = converterDataParaAPI(dataChegada)
        val viagemId = viagemAtual!!.viagem_id

        scope.launch {
            salvando = true
            erro = null
            try {
                // PRIMEIRO: Tentar API se viagem já foi sincronizada (ID > 0)
                var salvouOnline = false
                if (viagemId > 0) {
                    try {
                        // API espera valores no formato brasileiro original
                        // peso: "25.000" | valor: "1.500,00"
                        // A API PHP faz a conversão internamente
                        val pesoRetornoParaAPI = if (teveRetorno) pesoCargaRetorno.text else null
                        val valorRetornoParaAPI = if (teveRetorno) valorFreteRetorno.text else null

                        util.LogWriter.log("━━━ FINALIZAR VIAGEM (DIRETO) ━━━")
                        util.LogWriter.log("  motorista_id: ${motorista?.motorista_id}")
                        util.LogWriter.log("  viagem_id: ${viagemId.toInt()}")
                        util.LogWriter.log("  km_chegada: $kmChegada")
                        util.LogWriter.log("  data_chegada: $dataChegadaAPI")
                        util.LogWriter.log("  teve_retorno: $teveRetorno")
                        util.LogWriter.log("  peso: $pesoRetornoParaAPI")
                        util.LogWriter.log("  valor: $valorRetornoParaAPI")
                        util.LogWriter.log("  foto: ${if (cameraState.base64 != null) "${cameraState.base64!!.length} chars" else "null"}")

                        val response = api.ApiClient.finalizarViagem(
                            api.FinalizarViagemRequest(

                                motorista_id = motorista?.motorista_id ?: "",
                                viagem_id = viagemId.toInt(),
                                km_chegada = kmChegada,
                                data_chegada = dataChegadaAPI,
                                observacao = observacao.ifEmpty { null },
                                teve_retorno = teveRetorno,
                                peso_carga_retorno = pesoRetornoParaAPI,
                                valor_frete_retorno = valorRetornoParaAPI,
                                local_carregou = if (teveRetorno) localCarregou else null,
                                ordem_retorno = if (teveRetorno) ordemRetorno else null,
                                cte_retorno = if (teveRetorno) cteRetorno else null,
                                foto_painel_base64 = cameraState.base64
                            )
                        )

                        util.LogWriter.log("  RESPOSTA: status=${response.status}, msg=${response.mensagem}")

                        if (response.status == "ok") {
                            salvouOnline = true
                        } else {
                            util.LogWriter.log("⚠️ Finalizar viagem API erro: ${response.mensagem}")
                        }
                    } catch (e: Exception) {
                        util.LogWriter.log("⚠️ Finalizar viagem EXCEPTION: ${e::class.simpleName}: ${e.message}")
                    }
                } else {
                    util.LogWriter.log("⚠️ viagem_id <= 0 ($viagemId), salvando offline direto")
                }

                // Se não salvou online, salvar localmente para sincronização
                // Salva no formato brasileiro original — a API converte
                if (!salvouOnline) {
                    repository.salvarFinalizacaoViagem(
                        motoristaId = motorista?.motorista_id ?: "",
                        viagemId = viagemId,
                        dataChegada = dataChegadaAPI,
                        kmChegada = kmChegada,
                        pesocargaRetorno = if (teveRetorno) pesoCargaRetorno.text else null,
                        valorfreteRetorno = if (teveRetorno) valorFreteRetorno.text else null,
                        observacao = observacao.ifEmpty { null },
                        fotoPainelChegada = cameraState.base64,
                        teveRetorno = teveRetorno,
                        localCarregou = if (teveRetorno) localCarregou else null,
                        ordemRetorno = if (teveRetorno) ordemRetorno else null,
                        cteRetorno = if (teveRetorno) cteRetorno else null
                    )
                }

                // Limpa viagem atual local
                repository.limparViagemAtual()

                if (salvouOnline) {
                    sucesso = "Viagem finalizada com sucesso!"
                } else {
                    sucesso = "Viagem salva! Será sincronizada quando houver conexão."
                }
            } catch (e: Exception) {
                erro = "Erro ao salvar: ${e.message}"
            }
            salvando = false
        }
    }

    // Diálogo de aviso de KM suspeito (permite prosseguir)
    if (mostrarAvisoKm) {
        AlertDialog(
            onDismissRequest = { mostrarAvisoKm = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(36.dp)) },
            title = { Text("KM Suspeito", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B)) },
            text = {
                Column {
                    Text(
                        mensagemAvisoKm,
                        fontSize = 14.sp,
                        color = AppColors.TextPrimary,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { prosseguirAposAvisoKm() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Continuar Mesmo Assim", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { mostrarAvisoKm = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Corrigir KM")
                }
            }
        )
    }

    // Prioridade 2 - #8: Diálogo de confirmação antes de finalizar viagem
    if (mostrarDialogoConfirmacao) {
        val kmChNum = kmChegada.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val kmInStr = kmInicioViagem.ifEmpty { viagemAtual?.km_inicio ?: "" }
        val kmInNum = kmInStr.toDoubleOrNull()?.toLong() ?: kmInStr.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val kmPerc = if (kmInNum > 0) kmChNum - kmInNum else 0L
        val kmRotaLong = kmRotaDestino.toLong()

        AlertDialog(
            onDismissRequest = { mostrarDialogoConfirmacao = false },
            icon = { Icon(Icons.Default.Flag, null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp)) },
            title = { Text("Confirmar Finalização", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Deseja finalizar a viagem para ${viagemAtual?.destino ?: ""}?",
                        fontSize = 15.sp,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))

                    // Resumo de KM
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (kmInNum > 0) {
                                Text("KM Início: $kmInNum", fontSize = 13.sp, color = AppColors.TextSecondary)
                            }
                            Text("KM Chegada: $kmChNum", fontSize = 13.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
                            if (kmPerc > 0) {
                                Text("KM Percorrido: $kmPerc km", fontSize = 13.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                            if (kmRotaLong > 0) {
                                Text("KM da Rota: $kmRotaLong km", fontSize = 13.sp, color = AppColors.TextSecondary)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Esta ação não pode ser desfeita.",
                        fontSize = 13.sp,
                        color = AppColors.Error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarDialogoConfirmacao = false
                        finalizarViagem()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirmar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { mostrarDialogoConfirmacao = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Diálogo de erro modal
    if (erro != null) {
        ui.ErroDialog(
            mensagem = erro!!,
            onDismiss = { erro = null }
        )
    }

    // Diálogo de sucesso modal
    if (sucesso != null) {
        ui.SucessoDialog(
            mensagem = sucesso!!,
            onDismiss = {
                sucesso = null
                onSucesso()
            }
        )
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Finalizar Viagem",
                onBackClick = onVoltar
            )
        }
    )
    { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                    Spacer(Modifier.height(16.dp))
                    Text("Carregando...", color = AppColors.TextSecondary)
                }
            }
        } else if (semViagemAberta) {
            // Sem viagem para finalizar
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
                        Icon(Icons.Default.Info, null, tint = AppColors.Orange, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Nenhuma viagem em andamento", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Orange)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Você não possui viagem para finalizar. Inicie uma viagem primeiro.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = onVoltar,
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Voltar ao Dashboard")
                        }
                    }
                }
            }
        } else {
            // Formulário de finalização
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AppColors.Background)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Card da viagem atual
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (ui.isDark()) AppColors.SurfaceVariant else Color(0xFF10B981).copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, null, tint = Color(0xFF10B981), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Finalizando viagem", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Text(viagemAtual?.destino ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.TextPrimary)
                            val dataFormatada = viagemAtual?.data_inicio?.let { d ->
                                val p = d.trim().split(" ").first().split("-")
                                if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else d
                            } ?: ""
                            Text("Iniciada em: $dataFormatada", fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Formulário
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                        // KM Chegada
                        Text("KM de Chegada *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = kmChegada,
                            onValueChange = { kmChegada = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF10B981)) },
                            placeholder = { Text("Ex: 125000", color = Color(0xFF9CA3AF)) })
                        

                        Spacer(Modifier.height(16.dp))

                        // Data Chegada
                        DateInputField(
                            value = dataChegada,
                            onValueChange = { dataChegada = it },
                            label = "Data de Chegada *",
                            primaryColor = Color(0xFF10B981),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        // Observação
                        Text("Observação da Viagem", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = observacao,
                            onValueChange = { observacao = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Notes, null, tint = Color(0xFF10B981)) },
                            placeholder = { Text("Observações (opcional)", color = Color(0xFF9CA3AF)) },
                            maxLines = 4)
                        

                        Spacer(Modifier.height(20.dp))

                        // Teve Frete de Retorno?
                        Text("Teve Frete de Retorno? *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Botão SIM
                            Button(
                                onClick = { teveRetorno = true },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (teveRetorno) Color(0xFF10B981) else Color(0xFFE5E7EB)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = if (teveRetorno) Color.White else AppColors.TextSecondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Sim",
                                    color = if (teveRetorno) Color.White else AppColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            // Botão NÃO
                            Button(
                                onClick = { teveRetorno = false },
                                modifier = Modifier.weight(1f).height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!teveRetorno) Color(0xFF10B981) else Color(0xFFE5E7EB)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    tint = if (!teveRetorno) Color.White else AppColors.TextSecondary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Não",
                                    color = if (!teveRetorno) Color.White else AppColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Campos de retorno (aparecem se teveRetorno = true)
                        if (teveRetorno) {
                            Spacer(Modifier.height(20.dp))

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Dados do Frete de Retorno", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 16.sp)

                                    Spacer(Modifier.height(16.dp))

                                    // Peso Carga Retorno
                                    Text("Peso da Carga *", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = pesoCargaRetorno,
                                        onValueChange = { newValue ->
                                            val formatted = formatarPesoFinalizar(newValue.text)
                                            pesoCargaRetorno = TextFieldValue(
                                                text = formatted,
                                                selection = TextRange(formatted.length)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        placeholder = { Text("Ex: 25.000", color = Color(0xFF9CA3AF)) },
                                        suffix = { Text("kg") })
                                    

                                    Spacer(Modifier.height(12.dp))

                                    // Valor Frete Retorno
                                    Text("Valor do Frete *", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = valorFreteRetorno,
                                        onValueChange = { newValue ->
                                            val formatted = formatarValorFinalizar(newValue.text)
                                            valorFreteRetorno = TextFieldValue(
                                                text = formatted,
                                                selection = TextRange(formatted.length)
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        prefix = { Text("R$ ") },
                                        placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) })
                                    

                                    Spacer(Modifier.height(12.dp))

                                    // Local Carregou
                                    Text("Local Onde Carregou *", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = localCarregou,
                                        onValueChange = { localCarregou = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                        placeholder = { Text("Ex: São Paulo - SP", color = Color(0xFF9CA3AF)) })
                                    

                                    Spacer(Modifier.height(12.dp))

                                    // Ordem Retorno
                                    Text("Ordem de Frete *", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = ordemRetorno,
                                        onValueChange = { ordemRetorno = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                        placeholder = { Text("Número da ordem", color = Color(0xFF9CA3AF)) })
                                    

                                    Spacer(Modifier.height(12.dp))

                                    // CTE Retorno
                                    Text("CTE Frete Retorno *", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, fontSize = 14.sp)
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = cteRetorno,
                                        onValueChange = { cteRetorno = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                        placeholder = { Text("Número do CTE", color = Color(0xFF9CA3AF)) })
                                    
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Foto do Painel
                        Text("Foto Quilometragem (Painel) *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaFinalizar(
                            foto = cameraState.bitmap,
                            onClick = { tirarFoto() },
                            onEscolherGaleria = { galleryLauncher.launch("image/*") },
                            onRemover = { cameraState.clear() }
                        )


                        Spacer(Modifier.height(24.dp))

                        // Botão Finalizar
                        Button(
                            onClick = { validarEConfirmar() },
                            enabled = !salvando,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Flag, null)
                                Spacer(Modifier.width(8.dp))
                                Text("FINALIZAR VIAGEM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
private fun FotoCapturaFinalizar(foto: Bitmap?, onClick: () -> Unit, onEscolherGaleria: () -> Unit, onRemover: () -> Unit) {
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
            // Opção: Tirar Foto
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF10B981).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onClick() },
                color = Color(0xFF10B981).copy(alpha = 0.05f)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF10B981), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Tirar Foto", color = Color(0xFF10B981), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }

            // Opção: Escolher da Galeria
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF1976D2).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .clickable { onEscolherGaleria() },
                color = Color(0xFF1976D2).copy(alpha = 0.05f)
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Color(0xFF1976D2), modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Da Galeria", color = Color(0xFF1976D2), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
    }
}

private fun formatarPesoFinalizar(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
    symbols.decimalSeparator = ','
    symbols.groupingSeparator = '.'
    val formatter = java.text.DecimalFormat("#,##0", symbols)
    return formatter.format(value)
}

private fun formatarValorFinalizar(input: String): String {
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