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
import api.OutraDespesaItem
import database.AppRepository
import kotlinx.coroutines.launch
import platform.UIKit.*
import platform.darwin.NSObject
import ui.AppColors
import ui.GradientTopBar
import util.CameraHelper
import util.converterDataParaAPI
import util.converterDataParaExibicao

// Delegate da câmera nativa iOS - fora do @Composable
private class EditarOutraDespesaCameraDelegate(
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
        onMessage("Captura cancelada", false)
        picker.dismissViewControllerAnimated(true, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarOutraDespesaScreen(
    repository: AppRepository,
    item: OutraDespesaItem,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    var carregando by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }

    var tipo by remember { mutableStateOf(item.tipo) }
    var descricao by remember { mutableStateOf(item.descricao) }
    // Seleciona o texto inteiro ao carregar (não só cursor no final) — assim
    // o primeiro dígito que o motorista digitar substitui o valor inteiro,
    // em vez de ser acrescentado aos dígitos que já tavam lá. Sem isso,
    // editar "140,00" digitando um dígito a mais lia TODOS os dígitos
    // visíveis (14000) como se fossem centavos novos, inflando o valor.
    val valorInicial = formatarDecimalParaExibicaoOD(item.valor.toString())
    var valor by remember { mutableStateOf(TextFieldValue(valorInicial, selection = TextRange(0, valorInicial.length))) }
    var data by remember { mutableStateOf(converterDataParaExibicao(item.data)) }
    var local by remember { mutableStateOf(item.local) }

    var fotoBase64 by remember { mutableStateOf<String?>(null) }
    val fotoBitmap = remember(fotoBase64) { fotoBase64?.let { CameraHelper.base64ToImageBitmap(it) } }

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
        EditarOutraDespesaCameraDelegate(
            onFotoCaptured = { base64 -> fotoBase64 = base64 },
            onMessage = { msg, erro -> mostrarMensagem(msg, erro) }
        )
    }

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

    // Busca os dados completos (com foto) — o item recebido da lista não
    // tem a foto, só o "buscar" dedicado devolve ela em base64.
    LaunchedEffect(item.id) {
        carregando = true
        try {
            val response = api.ApiClient.buscarOutraDespesa(
                api.BuscarOutraDespesaRequest(
                    despesa_id = item.id,
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )
            if (response.status == "ok" && response.despesa != null) {
                val despesa = response.despesa
                tipo = despesa.tipo
                descricao = despesa.descricao
                // Servidor devolve decimal puro ("140.00", ponto) — converte
                // pra máscara BR ("140,00", vírgula) igual todo campo de
                // valor do app. Sem isso, se o motorista não editasse o
                // campo, o "140.00" (com ponto) seria reenviado como está e
                // o servidor removeria o ponto achando que era separador de
                // milhar, inflando o valor 100x (140.00 -> 14000).
                val valorTexto = formatarDecimalParaExibicaoOD(despesa.valor)
                valor = TextFieldValue(valorTexto, selection = TextRange(0, valorTexto.length))
                data = converterDataParaExibicao(despesa.data)
                local = despesa.local
                fotoBase64 = despesa.foto
            }
            // Se a busca falhar, mantém os dados básicos já recebidos da
            // lista (item) — só a foto fica indisponível nesse caso.
        } catch (e: Exception) {
            // Offline — mantém os dados básicos já recebidos da lista
        }
        carregando = false
    }

    fun salvar() {
        if (tipo.isEmpty()) { mostrarMensagem("Selecione o tipo de despesa", isErro = true); return }
        if (valor.text.isBlank()) { mostrarMensagem("Informe o valor", isErro = true); return }
        if (data.isEmpty()) { mostrarMensagem("Informe a data", isErro = true); return }
        scope.launch {
            salvando = true
            try {
                val resp = api.ApiClient.atualizarOutraDespesa(
                    api.AtualizarOutraDespesaRequest(
                        despesa_id = item.id,
                        motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemId,
                        tipo = tipo,
                        descricao = descricao.ifEmpty { tipo },
                        // Envia mascarado em BR ("10.000,00") direto — é o
                        // servidor que converte pra decimal. Convertendo
                        // aqui antes o servidor reprocessava um valor sem
                        // vírgula, inflando o valor (removia o "." como se
                        // fosse separador de milhar).
                        valor = valor.text,
                        data = converterDataParaAPI(data),
                        local = local.ifEmpty { null },
                        foto_comprovante = fotoBase64
                    )
                )
                if (resp.status == "ok") {
                    sucessoMsg = "Despesa atualizada com sucesso!"
                } else {
                    mostrarMensagem(resp.mensagem ?: "Erro ao atualizar", isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem("Erro: ${e.message}", isErro = true)
            }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }
    if (mostrarPermissaoCameraNegada) ui.CameraPermissaoNegadaDialog { mostrarPermissaoCameraNegada = false }

    Scaffold(topBar = { GradientTopBar(title = "Editar Despesa", onBackClick = onVoltar) }) { padding ->
        if (carregando) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }.verticalScroll(scrollState).padding(16.dp)) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(tipo, { tipo = it }, label = { Text("Tipo") }, modifier = Modifier.fillMaxWidth(), colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(descricao, { descricao = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth(), colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = valor,
                            onValueChange = { newValue ->
                                val filtered = newValue.text.filter { c -> c.isDigit() || c == '.' || c == ',' }
                                valor = TextFieldValue(text = filtered, selection = TextRange(filtered.length))
                            },
                            label = { Text("Valor (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = AppColors.Primary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(data, { data = it }, label = { Text("Data") }, leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = AppColors.Primary) },
                            modifier = Modifier.fillMaxWidth(), colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(local, { local = it }, label = { Text("Local") }, leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = AppColors.Primary) },
                            modifier = Modifier.fillMaxWidth(), colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp))

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
                                    Icon(Icons.Default.Close, "Remover", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                IconButton(
                                    onClick = { abrirCamera() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .background(AppColors.Primary, RoundedCornerShape(50))
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
                                    .border(2.dp, AppColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .background(AppColors.Primary.copy(alpha = 0.05f))
                                    .clickable { abrirCamera() },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CameraAlt, "Câmera", modifier = Modifier.size(48.dp), tint = AppColors.Primary)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Tirar Foto", color = AppColors.Primary, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Salvar Alterações", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Converte um decimal puro vindo do servidor ("140.00") pra máscara BR
 * ("140,00") igual todo campo de valor do app. Usa aritmética inteira
 * (centavos) em vez de string do Double, evitando artefato de ponto
 * flutuante.
 */
private fun formatarDecimalParaExibicaoOD(valorServidor: String?): String {
    if (valorServidor.isNullOrBlank()) return ""
    val numero = valorServidor.replace(",", ".").toDoubleOrNull() ?: return valorServidor
    val centavosTotal = kotlin.math.round(numero * 100).toLong()
    val negativo = centavosTotal < 0
    val abs = kotlin.math.abs(centavosTotal)
    val inteiro = abs / 100
    val centavos = abs % 100
    val inteiroFormatado = inteiro.toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (negativo) "-" else "") + "$inteiroFormatado,${centavos.toString().padStart(2, '0')}"
}
