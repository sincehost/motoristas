package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import api.ViagemRequest
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

// Classe delegate FORA do @Composable
private class CameraDelegate(
    private val onFotoCaptured: (String, ImageBitmap) -> Unit,
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

                val imageData = UIImageJPEGRepresentation(image, 0.7)
                if (imageData != null) {
                    val length = imageData.length.toInt()
                    val bytes = ByteArray(length)
                    bytes.usePinned { pinned ->
                        imageData.getBytes(pinned.addressOf(0), length.toULong())
                    }

                    val skiaImage = SkiaImage.makeFromEncoded(bytes)
                    if (skiaImage != null) {
                        val bitmap = skiaImage.toComposeImageBitmap()
                        onFotoCaptured(base64, bitmap)
                        onMessage("✓ Foto capturada com sucesso!", false)
                    } else {
                        onMessage("Erro ao processar imagem", true)
                    }
                } else {
                    onMessage("Erro ao comprimir imagem", true)
                }
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
actual fun IniciarViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val destinos = remember { repository.getAllDestinos() }
    val equipamentos = remember { repository.getAllEquipamentos() }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

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
                val kmInicioExistente = repository.getViagemAtual()?.km_inicio ?: ""
                val kmInicioParaSalvar = if (viagem.km_inicio.isNotEmpty()) viagem.km_inicio else kmInicioExistente
                repository.salvarViagemAtual(viagem.id.toLong(), viagem.destino, viagem.data, kmInicioParaSalvar)
            }
        } catch (e: Exception) {
            // Sem internet e sem viagem local - permite iniciar
        }
        carregando = false
    }

    var numerobd by remember { mutableStateOf("") }
    var cte by remember { mutableStateOf("") }
    var mostrarCampos2 by remember { mutableStateOf(false) }
    var numerobd2 by remember { mutableStateOf("") }
    var cte2 by remember { mutableStateOf("") }

    var destinoExpandido by remember { mutableStateOf(false) }
    var destinoSelecionado by remember { mutableStateOf<Pair<Long, String>?>(null) }

    var placaExpandida by remember { mutableStateOf(false) }
    var placaSelecionada by remember { mutableStateOf<String?>(null) }

    var dataViagem by remember { mutableStateOf(dataAtualFormatada()) }
    var kmInicio by remember { mutableStateOf("") }
    var pesoCarga by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorFrete by remember { mutableStateOf(TextFieldValue("0,00", selection = TextRange(4))) }

    var fotoBase64 by remember { mutableStateOf<String?>(null) }
    var fotoImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    var loading by remember { mutableStateOf(false) }

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

    // Delegate persistente - criado uma vez e mantido
    val cameraDelegate = remember {
        CameraDelegate(
            onFotoCaptured = { base64, bitmap ->
                fotoBase64 = base64
                fotoImageBitmap = bitmap
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
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
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            null,
                            tint = AppColors.Orange,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Viagem em andamento",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = AppColors.Orange
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Você já possui uma viagem em andamento. Finalize-a antes de iniciar uma nova.",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))

                        // Card com info da viagem atual
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    null,
                                    tint = AppColors.Primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Destino:", fontSize = 12.sp, color = AppColors.TextSecondary)
                                    Text(
                                        viagemEmAndamento!!.destino,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
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
                            Text("Voltar ao Dashboard", fontWeight = FontWeight.Bold)
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
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    }
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = numerobd,
                                onValueChange = { numerobd = it },
                                label = { Text("Ordem de Frete *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Tag, null, tint = AppColors.Primary)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { }
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { mostrarCampos2 = !mostrarCampos2 }) {
                                Icon(
                                    if (mostrarCampos2) Icons.Default.RemoveCircle
                                    else Icons.Default.AddCircle,
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
                            leadingIcon = {
                                Icon(Icons.Default.Description, null, tint = AppColors.Primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { }
                            )
                        )

                        if (mostrarCampos2) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = numerobd2,
                                onValueChange = { numerobd2 = it },
                                label = { Text("Ordem de Frete 2") },
                                leadingIcon = {
                                    Icon(Icons.Default.Tag, null, tint = AppColors.TextSecondary)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { }
                                )
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = cte2,
                                onValueChange = { cte2 = it },
                                label = { Text("Nº do CTE 2") },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, null, tint = AppColors.TextSecondary)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { }
                                )
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = destinoExpandido,
                            onExpandedChange = {
                                focusManager.clearFocus()
                                destinoExpandido = it
                            }
                        ) {
                            OutlinedTextField(
                                value = destinoSelecionado?.second ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Rota *") },
                                leadingIcon = {
                                    Icon(Icons.Default.Route, null, tint = AppColors.Primary)
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinoExpandido)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = destinoExpandido,
                                onDismissRequest = { destinoExpandido = false }
                            ) {
                                destinos.forEach { destino ->
                                    DropdownMenuItem(
                                        text = { Text(destino.nome) },
                                        onClick = {
                                            destinoSelecionado = Pair(destino.servidor_id, destino.nome)
                                            destinoExpandido = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        ExposedDropdownMenuBox(
                            expanded = placaExpandida,
                            onExpandedChange = {
                                focusManager.clearFocus()
                                placaExpandida = it
                            }
                        ) {
                            OutlinedTextField(
                                value = placaSelecionada ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Placa do Cavalo *") },
                                leadingIcon = {
                                    Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary)
                                },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = placaExpandida)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = placaExpandida,
                                onDismissRequest = { placaExpandida = false }
                            ) {
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
                            leadingIcon = {
                                Icon(Icons.Default.Speed, null, tint = AppColors.Primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { }
                            ),
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
                            leadingIcon = {
                                Icon(Icons.Default.Scale, null, tint = AppColors.Primary)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { }
                            ),
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
                            leadingIcon = {
                                Icon(Icons.Default.AttachMoney, null, tint = AppColors.Primary)
                            },
                            placeholder = { Text("0,00") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            singleLine = true
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "Foto do Painel de Saída *",
                            fontWeight = FontWeight.Medium,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))

                        if (fotoImageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Image(
                                    bitmap = fotoImageBitmap!!,
                                    contentDescription = "Foto",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = {
                                        fotoImageBitmap = null
                                        fotoBase64 = null
                                        mostrarMensagem("Foto removida")
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(Color.Red, RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        "Remover",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { abrirCamera() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(AppColors.Primary, RoundedCornerShape(50))
                                ) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        "Nova foto",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
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
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        "Tirar foto",
                                        modifier = Modifier.size(48.dp),
                                        tint = AppColors.Primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Tirar Foto",
                                        color = AppColors.Primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                scope.launch {
                                    if (numerobd.isBlank()) {
                                        mostrarMensagem("Informe a Ordem de Frete", isErro = true)
                                        return@launch
                                    }
                                    if (cte.isBlank()) {
                                        mostrarMensagem("Informe o Nº do CTE", isErro = true)
                                        return@launch
                                    }
                                    if (destinoSelecionado == null) {
                                        mostrarMensagem("Selecione uma Rota", isErro = true)
                                        return@launch
                                    }
                                    if (placaSelecionada == null) {
                                        mostrarMensagem("Selecione a Placa", isErro = true)
                                        return@launch
                                    }
                                    if (dataViagem.isBlank()) {
                                        mostrarMensagem("Informe a Data da Viagem", isErro = true)
                                        return@launch
                                    }
                                    if (kmInicio.isBlank()) {
                                        mostrarMensagem("Informe o KM de Início", isErro = true)
                                        return@launch
                                    }
                                    if (pesoCarga.text.isBlank()) {
                                        mostrarMensagem("Informe o Peso da Carga", isErro = true)
                                        return@launch
                                    }
                                    if (fotoBase64 == null) {
                                        mostrarMensagem("Tire a foto do painel de saída", isErro = true)
                                        return@launch
                                    }

                                    loading = true

                                    // Data de criação no formato ISO8601
                                    val isoFormatter = NSISO8601DateFormatter()
                                    val dataCriacao = isoFormatter.stringFromDate(NSDate())

                                    val dataViagemAPI = converterDataParaAPI(dataViagem)

                                    // Converte valor do frete para decimal para API
                                    val valorFreteParaAPI = if (valorFrete.text.isNotBlank() && valorFrete.text != "0,00") {
                                        // Remove pontos e vírgula, mantém apenas dígitos
                                        val valorLimpo = valorFrete.text.replace(".", "").replace(",", "")
                                        val valorInt = valorLimpo.toIntOrNull() ?: 0
                                        val reais = valorInt / 100
                                        val centavos = valorInt % 100
                                        "${reais}.${centavos.toString().padStart(2, '0')}"
                                    } else {
                                        null
                                    }

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
                                                placa = placaSelecionada ?: "",
                                                pesocarga = pesoCarga.text,
                                                valorfrete = valorFreteParaAPI,
                                                foto_painel_saida = fotoBase64
                                            )
                                        )

                                        if (response.status == "ok") {
                                            val viagemIdReal = response.viagem_id ?: 0
                                            repository.salvarViagemAtual(
                                                viagemId = viagemIdReal.toLong(),
                                                destino = destinoSelecionado!!.second,
                                                dataInicio = dataViagemAPI,
                                                kmInicio = kmInicio
                                            )
                                            mostrarMensagem("✓ Viagem registrada com sucesso!")
                                            kotlinx.coroutines.delay(1500)
                                            onSucesso()
                                        } else {
                                            repository.salvarViagem(
                                                numerobd = numerobd,
                                                cte = cte,
                                                numerobd2 = numerobd2.ifBlank { null },
                                                cte2 = cte2.ifBlank { null },
                                                destinoId = destinoSelecionado!!.first,
                                                destinoNome = destinoSelecionado!!.second,
                                                placa = placaSelecionada ?: "",
                                                dataViagem = dataViagemAPI,
                                                kmInicio = kmInicio,
                                                pesoCarga = pesoCarga.text,
                                                valorFrete = valorFreteParaAPI,
                                                fotoPainelSaida = fotoBase64,
                                                dataCriacao = dataCriacao
                                            )
                                            val viagens = repository.getViagensParaSincronizar()
                                            val currentEpochMillis = (NSDate().timeIntervalSince1970 * 1000).toLong()
                                            val idLocal = viagens.lastOrNull()?.id ?: currentEpochMillis
                                            repository.salvarViagemAtual(
                                                viagemId = -idLocal,
                                                destino = destinoSelecionado!!.second,
                                                dataInicio = dataViagemAPI,
                                                kmInicio = kmInicio
                                            )
                                            mostrarMensagem("✓ Viagem salva. Sincronize depois.")
                                            kotlinx.coroutines.delay(1500)
                                            onSucesso()
                                        }
                                    } catch (e: Exception) {
                                        repository.salvarViagem(
                                            numerobd = numerobd,
                                            cte = cte,
                                            numerobd2 = numerobd2.ifBlank { null },
                                            cte2 = cte2.ifBlank { null },
                                            destinoId = destinoSelecionado!!.first,
                                            destinoNome = destinoSelecionado!!.second,
                                            placa = placaSelecionada ?: "",
                                            dataViagem = dataViagemAPI,
                                            kmInicio = kmInicio,
                                            pesoCarga = pesoCarga.text,
                                            valorFrete = valorFreteParaAPI,
                                            fotoPainelSaida = fotoBase64,
                                            dataCriacao = dataCriacao
                                        )
                                        val viagens = repository.getViagensParaSincronizar()
                                        val currentEpochMillis = (NSDate().timeIntervalSince1970 * 1000).toLong()
                                        val idLocal = viagens.lastOrNull()?.id ?: currentEpochMillis
                                        repository.salvarViagemAtual(
                                            viagemId = -idLocal,
                                            destino = destinoSelecionado!!.second,
                                            dataInicio = dataViagemAPI,
                                            kmInicio = kmInicio
                                        )
                                        mostrarMensagem("✓ Viagem salva. Sincronize quando tiver internet.")
                                        kotlinx.coroutines.delay(1500)
                                        onSucesso()
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
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