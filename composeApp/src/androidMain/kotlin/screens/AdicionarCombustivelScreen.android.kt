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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.DateInputField
import util.rememberCameraState
import util.rememberSaveableTextField
import util.dataAtualFormatada
import util.converterDataParaAPI
import util.formatarKmInput
import util.normalizarKmParaEnvio
import util.formatarKmExibicao
import util.analisarTextoCupom
import util.mensagemErroAmigavel
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

    // Viagem em andamento (pega automaticamente)
    var viagemEmAndamento by remember { mutableStateOf<api.ViagemAberta?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }

    // Equipamentos (placas)
    var equipamentos by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }

    // Formas de pagamento (catálogo da empresa, cadastrado no admin)
    val formasPagamento = remember { repository.getAllFormasPagamento() }

    // Tipos de combustível (catálogo da empresa, cadastrado no admin)
    val tiposCombustivel = remember { repository.getAllTiposCombustivel() }

    // ★ FIX PROCESS-DEATH: campos que perdiam valor ao abrir câmera
    // Pair<Long,String> não é diretamente rememberSaveable, separamos em dois campos
    var placaSelecionadaId by rememberSaveable { mutableStateOf(-1L) }
    var placaSelecionadaNome by rememberSaveable { mutableStateOf("") }
    val placaSelecionada: Pair<Long, String>? =
        if (placaSelecionadaId >= 0) Pair(placaSelecionadaId, placaSelecionadaNome) else null

    // Resolve o veículo (id local) da viagem — prefere veiculo_id (mais
    // preciso), cai pra casar por placa se a viagem for antiga/offline e não
    // tiver esse id ainda. Só fica sem nada se nenhum dos dois resolver, caso
    // em que a tela mostra o seletor manual como fallback.
    fun aplicarViagem(viagem: api.ViagemAberta, listaEquip: List<Pair<Long, String>>) {
        viagemEmAndamento = viagem
        val vid = viagem.veiculo_id
        if (vid != null) {
            placaSelecionadaId = vid.toLong()
            placaSelecionadaNome = viagem.placa
        } else if (viagem.placa.isNotBlank()) {
            listaEquip.find { it.second == viagem.placa }?.let {
                placaSelecionadaId = it.first
                placaSelecionadaNome = it.second
            }
        }
    }

    // Carrega dados ao iniciar (LOCAL primeiro, depois API)
    LaunchedEffect(Unit) {
        carregando = true

        // Carrega equipamentos do banco local
        equipamentos = repository.getEquipamentosParaDropdown()

        // 1. Verifica viagem LOCALMENTE primeiro
        val viagemLocal = repository.getViagemAtual()
        if (viagemLocal != null) {
            aplicarViagem(
                api.ViagemAberta(
                    id = viagemLocal.viagem_id.toInt(),
                    destino = viagemLocal.destino,
                    data = viagemLocal.data_inicio,
                    km_inicio = viagemLocal.km_inicio,
                    placa = viagemLocal.placa,
                    veiculo_id = viagemLocal.veiculo_id?.toInt(),
                    implemento1_placa = viagemLocal.implemento1_placa,
                    implemento2_placa = viagemLocal.implemento2_placa
                ),
                equipamentos
            )
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
                // Salva localmente para funcionar offline depois
                repository.salvarViagemAtualComComposicao(
                    viagemId = viagem.id.toLong(),
                    destino = viagem.destino,
                    dataInicio = viagem.data,
                    placa = viagem.placa,
                    veiculoId = viagem.veiculo_id?.toLong(),
                    implemento1Placa = viagem.implemento1_placa,
                    implemento2Placa = viagem.implemento2_placa
                )
                modoOffline = false

                // Atualiza equipamentos da API
                val syncResp = api.ApiClient.syncDados(motorista?.motorista_id ?: "")
                if (syncResp.status == "ok") {
                    repository.salvarEquipamentos(syncResp.equipamentos)
                    repository.salvarConfiguracaoEmpresa(syncResp.configuracoes.tipo_operacao)
                    equipamentos = repository.getEquipamentosParaDropdown()
                }
                aplicarViagem(viagem, equipamentos)
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
    var data by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var nomePosto by rememberSaveable { mutableStateOf("") }
    var kmPosto by rememberSaveableTextField("")
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

    // Scanear Nota — OCR + QR/código de barras 100% on-device (ML Kit).
    // Reaproveita a mesma foto do cupom fiscal: essa foto vale como comprovante
    // E como fonte da leitura, o motorista não precisa tirar duas fotos.
    var escaneandoNota by remember { mutableStateOf(false) }
    var aguardandoScan by remember { mutableStateOf(false) }
    var resultadoScan by remember { mutableStateOf<String?>(null) }

    fun executarScanCupom(bitmap: Bitmap) {
        escaneandoNota = true
        resultadoScan = null
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val scanner = BarcodeScanning.getClient()

        var textoOcr: String? = null
        var codigoLido: String? = null
        var pendentes = 2

        fun finalizarSePronto() {
            pendentes--
            if (pendentes > 0) return
            escaneandoNota = false
            val texto = textoOcr ?: ""
            if (texto.isBlank() && codigoLido == null) {
                resultadoScan = "Não conseguimos ler o cupom automaticamente. Preencha os campos manualmente."
                return
            }
            val resultado = analisarTextoCupom(texto, qrCodeText = codigoLido, barcodeText = codigoLido)
            var achouAlgo = false
            resultado.valorDetectado?.let { valorTotal = decimalParaTextFieldValueComb(it); achouAlgo = true }
            resultado.litrosDetectado?.let { litrosAbastecidos = decimalParaTextFieldValueComb(it); achouAlgo = true }
            resultado.dataDetectada?.let { data = it; achouAlgo = true }
            resultado.postoDetectado?.let { if (nomePosto.isBlank()) { nomePosto = it; achouAlgo = true } }

            // Se achou valor total e litros, calcula o valor do litro (evita depender
            // do OCR pegar esse número específico, que costuma vir bem pequeno no cupom).
            val litrosNum = resultado.litrosDetectado?.toDoubleOrNull()
            val valorNum = resultado.valorDetectado?.toDoubleOrNull()
            if (litrosNum != null && litrosNum > 0 && valorNum != null) {
                valorLitro = decimalParaTextFieldValueComb((valorNum / litrosNum).toString())
            }

            resultadoScan = if (achouAlgo) "Cupom lido! Confira os dados antes de salvar."
                            else "Não conseguimos identificar os dados automaticamente. Preencha manualmente."
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText -> textoOcr = visionText.text; finalizarSePronto() }
            .addOnFailureListener { finalizarSePronto() }

        scanner.process(image)
            .addOnSuccessListener { barcodes -> codigoLido = barcodes.firstOrNull()?.rawValue; finalizarSePronto() }
            .addOnFailureListener { finalizarSePronto() }
    }

    // Dispara o scan assim que a foto do cupom (câmera ou galeria) ficar pronta,
    // mas só quando veio do botão "Scanear Nota" — a foto normal do cupom não
    // aciona OCR sozinha.
    LaunchedEffect(cameraStateCupom.bitmap) {
        val bmp = cameraStateCupom.bitmap
        if (aguardandoScan && bmp != null) {
            aguardandoScan = false
            executarScanCupom(bmp)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val currentState = if (currentPhotoType == "marcador") cameraStateMarcador else cameraStateCupom
        if (success || currentState.checkPhotoExistsAfterCapture()) {
            when (currentPhotoType) {
                "marcador" -> cameraStateMarcador.onPhotoTaken()
                "cupom" -> cameraStateCupom.onPhotoTaken()
            }
        }
    }

    var mostrarPermissaoNegada by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val uri = when (currentPhotoType) {
                    "marcador" -> cameraStateMarcador.prepareCapture()
                    else -> cameraStateCupom.prepareCapture()
                }
                cameraLauncher.launch(uri)
            } catch (e: Exception) { }
        } else {
            mostrarPermissaoNegada = true
        }
    }
    if (mostrarPermissaoNegada) {
        ui.CameraPermissaoNegadaDialog(onDismiss = { mostrarPermissaoNegada = false })
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
        if (kmPosto.text.isEmpty()) { erro = "Informe o KM no posto"; return }
        if (tipoCombustivel.isEmpty()) { erro = "Selecione o tipo de combustível"; return }
        if (tiposCombustivel.find { it.nome == tipoCombustivel }?.requer_horas == 1L && horas.isEmpty()) { erro = "Informe as horas"; return }
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
                    kmPosto = normalizarKmParaEnvio(kmPosto.text),
                    foto = cameraStateCupom.base64,
                    fotoMarcador = cameraStateMarcador.base64,
                    tipoPagamento = tipoPagamento,
                    tipoCombustivel = tipoCombustivel,
                    horas = horas.ifEmpty { null },
                    valorLitro = valorLitro.text
                )

                sucesso = "Abastecimento salvo! Sincronize quando tiver internet."

            } catch (e: Exception) {
                erro = "Erro ao salvar: ${mensagemErroAmigavel(e.message)}"
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

                            // Scanear Nota — OCR + QR/código de barras, preenche os campos abaixo
                            Button(
                                onClick = {
                                    aguardandoScan = true
                                    tirarFoto("cupom")
                                },
                                enabled = !escaneandoNota,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                            ) {
                                if (escaneandoNota) {
                                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Lendo cupom...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.DocumentScanner, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("SCANEAR NOTA", fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                "Tire uma foto do cupom fiscal e a gente tenta preencher os campos abaixo. Sempre confira antes de salvar.",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            resultadoScan?.let { msg ->
                                Spacer(Modifier.height(8.dp))
                                Card(colors = CardDefaults.cardColors(containerColor = AppColors.Primary.copy(alpha = 0.1f))) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Info, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(msg, fontSize = 13.sp, color = AppColors.TextPrimary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))

                            // Data
                            DateInputField(
                                value = data,
                                onValueChange = { data = it },
                                label = "Data *",
                                primaryColor = AppColors.Primary,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(16.dp))

                            // Placa — quando a viagem já sabe o veículo, só mostra (não
                            // pergunta de novo); o seletor manual é só um fallback pra
                            // viagens antigas onde isso não foi resolvido.
                            Text("Veículo *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            if (viagemEmAndamento?.placa?.isNotBlank() == true) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = AppColors.Background)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary)
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(viagemEmAndamento!!.placa, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                                            if (!viagemEmAndamento!!.implemento1_placa.isNullOrBlank()) {
                                                Text(
                                                    "+ ${listOfNotNull(viagemEmAndamento!!.implemento1_placa, viagemEmAndamento!!.implemento2_placa).joinToString(" + ")}",
                                                    fontSize = 12.sp, color = AppColors.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
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
                                onValueChange = { newValue ->
                                    val formatted = formatarKmInput(newValue.text)
                                    kmPosto = TextFieldValue(
                                        text = formatted,
                                        selection = TextRange(formatted.length)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                placeholder = { Text("Ex.: 115670.5") },
                                leadingIcon = { Icon(Icons.Default.Speed, null, tint = AppColors.Primary) }
                            )
                            Text(
                                "Digite como aparece no painel. Ex.: 115670.5",
                                fontSize = 12.sp,
                                color = AppColors.Primary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
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
                                    tiposCombustivel.forEach { tc ->
                                        DropdownMenuItem(text = { Text(tc.nome) }, onClick = { tipoCombustivel = tc.nome; combustivelExpandido = false })
                                    }
                                }
                            }

                            // Campo Horas (só para tipos que exigem horas)
                            if (tiposCombustivel.find { it.nome == tipoCombustivel }?.requer_horas == 1L) {
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
                            

                            Spacer(Modifier.height(16.dp))

                            // Forma de Pagamento
                            Text("Forma de Pagamento *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                formasPagamento.forEach { fp ->
                                    BotaoOpcaoComb(fp.nome, Icons.Default.CreditCard, tipoPagamento == fp.codigo, { tipoPagamento = fp.codigo }, Modifier.weight(1f))
                                }
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

/** Converte um decimal puro ("123.45", vindo do scan) pro TextFieldValue já formatado em BR (mesmo padrão de formatarMoedaComb/formatarDecimalComb). */
private fun decimalParaTextFieldValueComb(valorDecimal: String): TextFieldValue {
    val valor = valorDecimal.toDoubleOrNull() ?: return TextFieldValue("")
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
    symbols.decimalSeparator = ','
    symbols.groupingSeparator = '.'
    val formatter = java.text.DecimalFormat("#,##0.00", symbols)
    val formatted = formatter.format(valor)
    return TextFieldValue(formatted, TextRange(formatted.length))
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