package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private class FinalizarViagemCameraDelegate(
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
actual fun FinalizarViagemScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Estados
    var salvando by remember { mutableStateOf(false) }
    var carregando by remember { mutableStateOf(true) }

    // Viagem em andamento
    var viagemAtual by remember { mutableStateOf<br.com.lfsystem.app.database.ViagemAtual?>(null) }
    var semViagemAberta by remember { mutableStateOf(false) }
    var kmInicioViagem by remember { mutableStateOf("") }

    // Carrega viagem atual e busca km_inicio
    LaunchedEffect(Unit) {
        carregando = true
        viagemAtual = repository.getViagemAtual()
        if (viagemAtual == null) {
            semViagemAberta = true
        } else {
            // Se km_inicio já está salvo na ViagemAtual local, usar
            val kmLocal = viagemAtual?.km_inicio ?: ""
            if (kmLocal.isNotEmpty()) {
                kmInicioViagem = kmLocal
            } else {
                val viagemId = viagemAtual!!.viagem_id
                if (viagemId < 0) {
                    // Viagem offline — buscar da tabela Viagem local
                    val idLocal = -viagemId
                    val viagemOffline = repository.getViagensParaSincronizar()
                        .firstOrNull { it.id == idLocal }
                    if (viagemOffline != null && viagemOffline.km_inicio.isNotEmpty()) {
                        kmInicioViagem = viagemOffline.km_inicio
                        repository.salvarViagemAtual(
                            viagemId = viagemId,
                            destino = viagemAtual!!.destino,
                            dataInicio = viagemAtual!!.data_inicio,
                            kmInicio = viagemOffline.km_inicio
                        )
                    }
                } else if (viagemId > 0) {
                    try {
                        val resp = api.ApiClient.detalheViagem(
                            api.ViagemDetalheRequest(viagem_id = viagemId.toInt())
                        )
                        if (resp.status == "ok" && resp.viagem != null) {
                            kmInicioViagem = resp.viagem.km_inicio
                            repository.salvarViagemAtual(
                                viagemId = viagemId,
                                destino = viagemAtual!!.destino,
                                dataInicio = viagemAtual!!.data_inicio,
                                kmInicio = resp.viagem.km_inicio
                            )
                        }
                    } catch (e: Exception) {
                        // Offline — tentar da tabela Viagem local
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
                                kmInicio = viagemLocal.km_inicio
                            )
                        }
                    }
                }
            }
        }
        carregando = false
    }

    // Campos do formulário
    var kmChegada by remember { mutableStateOf("") }
    var dataChegada by remember { mutableStateOf(dataAtualFormatada()) }
    var observacao by remember { mutableStateOf("") }
    var teveRetorno by remember { mutableStateOf(false) }

    // Campos de retorno (opcionais) - COM TextFieldValue
    var pesoCargaRetorno by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorFreteRetorno by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var localCarregou by remember { mutableStateOf("") }
    var ordemRetorno by remember { mutableStateOf("") }
    var cteRetorno by remember { mutableStateOf("") }

    // Foto
    var fotoPainel by remember { mutableStateOf<ImageBitmap?>(null) }
    var fotoPainelBase64 by remember { mutableStateOf<String?>(null) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Delegate persistente - criado uma vez e mantido
    val cameraDelegate = remember {
        FinalizarViagemCameraDelegate(
            onFotoCaptured = { base64, bitmap ->
                fotoPainelBase64 = base64
                fotoPainel = bitmap
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

    // Função finalizar viagem
    fun finalizarViagem() {
        focusManager.clearFocus()

        if (viagemAtual == null) {
            mostrarMensagem("Nenhuma viagem em andamento", isErro = true)
            return
        }
        if (kmChegada.isEmpty()) {
            mostrarMensagem("Informe o KM de chegada", isErro = true)
            return
        }
        if (dataChegada.isEmpty()) {
            mostrarMensagem("Informe a data de chegada", isErro = true)
            return
        }
        if (fotoPainelBase64 == null) {
            mostrarMensagem("Tire a foto do painel", isErro = true)
            return
        }

        // Validação de KM (chegada deve ser maior que início)
        val kmChegadaNum = kmChegada.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val kmInicioStr = kmInicioViagem.ifEmpty { viagemAtual?.km_inicio ?: "" }
        val kmInicioNum = kmInicioStr.filter { it.isDigit() }.toLongOrNull() ?: 0L

        if (kmChegadaNum <= 0) {
            mostrarMensagem("KM de chegada deve ser maior que zero", isErro = true)
            return
        }

        if (kmInicioNum > 0 && kmChegadaNum <= kmInicioNum) {
            mostrarMensagem("KM de chegada ($kmChegada) deve ser maior que KM de início ($kmInicioStr)", isErro = true)
            return
        }

        if (teveRetorno) {
            if (pesoCargaRetorno.text.isEmpty()) {
                mostrarMensagem("Informe o peso da carga de retorno", isErro = true)
                return
            }
            if (valorFreteRetorno.text.isEmpty()) {
                mostrarMensagem("Informe o valor do frete de retorno", isErro = true)
                return
            }
            if (localCarregou.isEmpty()) {
                mostrarMensagem("Informe o local onde carregou", isErro = true)
                return
            }
            if (ordemRetorno.isEmpty()) {
                mostrarMensagem("Informe a ordem de retorno", isErro = true)
                return
            }
            if (cteRetorno.isEmpty()) {
                mostrarMensagem("Informe o CTE de retorno", isErro = true)
                return
            }
        }

        val dataChegadaAPI = converterDataParaAPI(dataChegada)
        val viagemId = viagemAtual!!.viagem_id

        scope.launch {
            salvando = true
            try {
                // PRIMEIRO: Tentar API se viagem já foi sincronizada (ID > 0)
                var salvouOnline = false
                if (viagemId > 0) {
                    try {
                        val response = api.ApiClient.finalizarViagem(
                            api.FinalizarViagemRequest(

                                motorista_id = motorista?.motorista_id ?: "",
                                viagem_id = viagemId.toInt(),
                                km_chegada = kmChegada,
                                data_chegada = dataChegadaAPI,
                                observacao = observacao.ifEmpty { null },
                                teve_retorno = teveRetorno,
                                peso_carga_retorno = if (teveRetorno) pesoCargaRetorno.text else null,
                                valor_frete_retorno = if (teveRetorno) valorFreteRetorno.text else null,
                                local_carregou = if (teveRetorno) localCarregou else null,
                                ordem_retorno = if (teveRetorno) ordemRetorno else null,
                                cte_retorno = if (teveRetorno) cteRetorno else null,
                                foto_painel_base64 = fotoPainelBase64
                            )
                        )

                        if (response.status == "ok") {
                            salvouOnline = true
                        }
                    } catch (e: Exception) {
                        // Sem conexão - salvar localmente
                    }
                }

                // Se não salvou online, salvar localmente para sincronização
                if (!salvouOnline) {
                    repository.salvarFinalizacaoViagem(
                        motoristaId = motorista?.motorista_id ?: "",
                        viagemId = viagemId,
                        dataChegada = dataChegadaAPI,
                        kmChegada = kmChegada,
                        pesocargaRetorno = if (teveRetorno) pesoCargaRetorno.text else null,
                        valorfreteRetorno = if (teveRetorno) valorFreteRetorno.text else null,
                        observacao = observacao.ifEmpty { null },
                        fotoPainelChegada = fotoPainelBase64
                    )
                }

                // Limpa viagem atual local
                repository.limparViagemAtual()

                if (salvouOnline) {
                    sucessoMsg = "Viagem finalizada com sucesso!"
                } else {
                    sucessoMsg = "Viagem salva! Será sincronizada quando houver conexão."
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro ao salvar: ${e.message}", isErro = true)
            }
            salvando = false
        }
    }

    // Diálogos modais de erro e sucesso
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onSucesso() }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Finalizar Viagem",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        if (carregando) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
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
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Info,
                            null,
                            tint = AppColors.Orange,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Nenhuma viagem em andamento",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AppColors.Orange
                        )
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
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    }
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Card da viagem atual
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Finalizando viagem", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Text(
                                viagemAtual?.destino ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = AppColors.TextPrimary
                            )
                            val dataFormatada = viagemAtual?.data_inicio?.let { d ->
                                val p = d.trim().split(" ").first().split("-")
                                if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}/${p[0]}" else d
                            } ?: ""
                            Text(
                                "Iniciada em: $dataFormatada",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary
                            )
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
                        Text(
                            "KM de Chegada *",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = kmChegada,
                            onValueChange = { kmChegada = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = {
                                Icon(Icons.Default.Speed, null, tint = Color(0xFF10B981))
                            },
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
                        Text(
                            "Observação da Viagem",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = observacao,
                            onValueChange = { observacao = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Notes, null, tint = Color(0xFF10B981))
                            },
                            placeholder = { Text("Observações (opcional)", color = Color(0xFF9CA3AF)) },
                            maxLines = 4)
                        

                        Spacer(Modifier.height(20.dp))

                        // Teve Frete de Retorno?
                        Text(
                            "Teve Frete de Retorno? *",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Botão SIM
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    teveRetorno = true
                                },
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
                                onClick = {
                                    focusManager.clearFocus()
                                    teveRetorno = false
                                },
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
                                    Text(
                                        "Dados do Frete de Retorno",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        fontSize = 16.sp
                                    )

                                    Spacer(Modifier.height(16.dp))

                                    // Peso Carga Retorno
                                    Text(
                                        "Peso da Carga *",
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp
                                    )
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
                                    Text(
                                        "Valor do Frete *",
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp
                                    )
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
                                    Text(
                                        "Local Onde Carregou *",
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = localCarregou,
                                        onValueChange = { localCarregou = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                        placeholder = { Text("Ex: São Paulo - SP", color = Color(0xFF9CA3AF)) })
                                    

                                    Spacer(Modifier.height(12.dp))

                                    // Ordem Retorno
                                    Text(
                                        "Ordem de Frete *",
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = ordemRetorno,
                                        onValueChange = { ordemRetorno = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                        placeholder = { Text("Número da ordem", color = Color(0xFF9CA3AF)) })
                                    

                                    Spacer(Modifier.height(12.dp))

                                    // CTE Retorno
                                    Text(
                                        "CTE Frete Retorno *",
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp
                                    )
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
                        Text(
                            "Foto Quilometragem (Painel) *",
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaFinalizar(
                            foto = fotoPainel,
                            onClick = { abrirCamera() },
                            onRemover = {
                                fotoPainel = null
                                fotoPainelBase64 = null
                                // Foto removida silenciosamente
                            }
                        )

                        Spacer(Modifier.height(24.dp))

                        // Botão Finalizar
                        Button(
                            onClick = { finalizarViagem() },
                            enabled = !salvando,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(
                                    Modifier.size(24.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Flag, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "FINALIZAR VIAGEM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
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
private fun FotoCapturaFinalizar(
    foto: ImageBitmap?,
    onClick: () -> Unit,
    onRemover: () -> Unit
) {
    if (foto != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = foto,
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
                    .size(36.dp)
                    .background(AppColors.Error, RoundedCornerShape(50))
            ) {
                Icon(
                    Icons.Default.Close,
                    "Remover",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
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
                Icon(
                    Icons.Default.CameraAlt,
                    null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tirar Foto do Painel",
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatarPesoFinalizar(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    return value.toString().reversed().chunked(3).joinToString(".").reversed()
}

private fun formatarValorFinalizar(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val reais = value / 100
    val centavos = value % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}