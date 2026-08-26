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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import api.AtualizarArlaRequest
import database.AppRepository
import kotlinx.coroutines.launch
import platform.UIKit.*
import platform.darwin.NSObject
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI
import util.converterDataParaExibicao
import util.formatarKmInput
import util.normalizarKmParaEnvio
import util.formatarKmExibicao
import util.mensagemErroAmigavel
import kotlin.math.roundToLong

// Delegate da câmera nativa iOS - fora do @Composable
private class EditarArlaCameraDelegate(
    private val onFotoCaptured: (String) -> Unit,
    private val onMessage: (String, Boolean) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image != null) {
            val base64 = CameraHelper.imageToBase64(image)
            if (base64 != null) {
                onFotoCaptured(base64)
            } else {
                onMessage("Erro ao converter imagem para base64", true)
            }
        } else {
            onMessage("Nenhuma imagem selecionada", true)
        }
        picker.dismissViewControllerAnimated(true, null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        // Sem mensagem: cancelar a foto é ação normal do motorista, não precisa
        // de aviso — e "Captura cancelada" ia pelo caminho de SUCESSO (sucessoMsg),
        // cujo dismiss chama onSucesso()/onVoltar() e navegava pra fora da tela.
        picker.dismissViewControllerAnimated(true, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarArlaScreen(
    repository: AppRepository,
    arlaId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }

    var carregando by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    var modoOffline by remember { mutableStateOf(false) }

    var data by remember { mutableStateOf(dataAtualFormatada()) }
    var valor by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var litros by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var posto by remember { mutableStateOf("") }
    var kmPosto by remember { mutableStateOf(TextFieldValue("")) }

    var fotoBase64 by remember { mutableStateOf<String?>(null) }
    val fotoBitmap = remember(fotoBase64) { fotoBase64?.let { CameraHelper.base64ToImageBitmap(it) } }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var mostrarPermissaoCameraNegada by remember { mutableStateOf(false) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Delegate persistente da câmera
    val cameraDelegate = remember {
        EditarArlaCameraDelegate(
            onFotoCaptured = { base64 -> fotoBase64 = base64 },
            onMessage = { msg, erro -> mostrarMensagem(msg, erro) }
        )
    }

    // Função para abrir a câmera nativa iOS
    fun abrirCamera() {
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (viewController == null) {
            mostrarMensagem("Não foi possível abrir a câmera", isErro = true)
            return
        }
        if (CameraHelper.cameraSemPermissao()) {
            mostrarPermissaoCameraNegada = true
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

    LaunchedEffect(arlaId) {
        carregando = true
        try {
            val response = ApiClient.buscarArla(
                api.BuscarArlaRequest(
                    arla_id = arlaId,
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )
            if (response.status == "ok" && response.arla != null) {
                val arla = response.arla
                data = converterDataParaExibicao(arla.data_arla)
                val valorFormatado = formatarValor((util.parseDoubleComLog(arla.valor, "arla.valor") * 100).roundToLong().toString())
                valor = TextFieldValue(valorFormatado, selection = TextRange(0, valorFormatado.length))
                // Servidor devolve decimal puro ("15.50", ponto) — precisa
                // virar a máscara BR ("15,50") igual todo campo de valor/
                // litros do app, senão reenviar sem editar manda formato
                // errado de volta pro endpoint (que lê "," como decimal e
                // "." como separador de milhar, inflando o valor ~100x).
                val litrosFormatado = formatarValor((util.parseDoubleComLog(arla.litros, "arla.litros") * 100).roundToLong().toString())
                litros = TextFieldValue(litrosFormatado, selection = TextRange(0, litrosFormatado.length))
                posto = arla.posto
                val kmPostoTexto = arla.km_posto.toDoubleOrNull()?.let { formatarKmExibicao(it) } ?: arla.km_posto
                kmPosto = TextFieldValue(kmPostoTexto, selection = TextRange(0, kmPostoTexto.length))
                fotoBase64 = arla.foto
                modoOffline = false
            } else {
                mostrarMensagem(response.mensagem ?: "ARLA não encontrado", isErro = true)
            }
        } catch (e: Exception) {
            modoOffline = true
            mostrarMensagem("Erro ao carregar dados: ${mensagemErroAmigavel(e.message)}", isErro = true)
        }
        carregando = false
    }

    // Diálogos modais
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar ARLA",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF06B6D4))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            focusManager.clearFocus()
                        })
                    }
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                // Aviso offline — Android já tinha, iOS não.
                if (modoOffline) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text("Sem conexão. Conecte para editar ARLA.", color = AppColors.Orange, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Informações do ARLA",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )

                        Spacer(Modifier.height(20.dp))

                        DateInputField(
                            value = data,
                            onValueChange = { data = it },
                            label = "Data",
                            primaryColor = Color(0xFF06B6D4),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valor,
                            onValueChange = { newValue ->
                                val digits = newValue.text.filter { c -> c.isDigit() }.take(7)
                                val formatted = formatarValor(digits)
                                valor = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                            },
                            label = { Text("Valor (R\$) *") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = litros,
                            onValueChange = { newValue ->
                                val digits = newValue.text.filter { c -> c.isDigit() }.take(7)
                                val formatted = formatarValor(digits)
                                litros = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                            },
                            label = { Text("Litros *") },
                            leadingIcon = { Icon(Icons.Default.Opacity, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = posto,
                            onValueChange = { posto = it },
                            label = { Text("Nome do Posto *") },
                            leadingIcon = { Icon(Icons.Default.Store, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = kmPosto,
                            onValueChange = { newValue ->
                                val formatted = formatarKmInput(newValue.text)
                                kmPosto = TextFieldValue(
                                    text = formatted,
                                    selection = TextRange(formatted.length)
                                )
                            },
                            label = { Text("KM do Posto *") },
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF06B6D4)) },
                            placeholder = { Text("Ex.: 115670.5") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        Text(
                            "Digite como aparece no painel. Ex.: 115670.5",
                            fontSize = 12.sp,
                            color = AppColors.Primary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Foto do Comprovante", fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))

                        if (fotoBitmap != null) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = fotoBitmap,
                                    contentDescription = "Foto do comprovante",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { fotoBase64 = null },
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
                                IconButton(
                                    onClick = { abrirCamera() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(Color(0xFF06B6D4), RoundedCornerShape(50))
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
                                    .border(2.dp, Color(0xFF06B6D4).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .background(Color(0xFF06B6D4).copy(alpha = 0.05f))
                                    .clickable { abrirCamera() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.CameraAlt,
                                        "Câmera",
                                        modifier = Modifier.size(48.dp),
                                        tint = Color(0xFF06B6D4)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Tirar Foto",
                                        color = Color(0xFF06B6D4),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    if (data.isBlank()) { mostrarMensagem("Informe a data", isErro = true); return@launch }
                                    if (valor.text.isBlank()) { mostrarMensagem("Informe o valor", isErro = true); return@launch }
                                    if (litros.text.isBlank()) { mostrarMensagem("Informe os litros", isErro = true); return@launch }
                                    if (posto.isBlank()) { mostrarMensagem("Informe o posto", isErro = true); return@launch }
                                    if (kmPosto.text.isBlank()) { mostrarMensagem("Informe o KM do posto", isErro = true); return@launch }

                                    salvando = true
                                    try {
                                        val response = ApiClient.atualizarArla(
                                            AtualizarArlaRequest(
                                                arla_id = arlaId,
                                                motorista_id = motorista?.motorista_id ?: "",
                                                data = converterDataParaAPI(data),
                                                valor = valor.text,
                                                litros = litros.text,
                                                posto = posto,
                                                km_posto = normalizarKmParaEnvio(kmPosto.text),
                                                foto = fotoBase64
                                            )
                                        )
                                        if (response.status == "ok") {
                                            sucessoMsg = "ARLA atualizado com sucesso!"
                                        } else {
                                            mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                                        }
                                    } catch (e: Exception) {
                                        mostrarMensagem("Erro: ${mensagemErroAmigavel(e.message)}", isErro = true)
                                    }
                                    salvando = false
                                }
                            },
                            enabled = !salvando && !modoOffline,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("SALVAR ALTERAÇÕES", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
    }
}

private fun formatarValor(digits: String): String {
    if (digits.isEmpty()) return ""
    val valor = digits.toLongOrNull() ?: return ""
    val reais = valor / 100
    val centavos = valor % 100
    val reaisFormatado = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$reaisFormatado,${centavos.toString().padStart(2, '0')}"
}
