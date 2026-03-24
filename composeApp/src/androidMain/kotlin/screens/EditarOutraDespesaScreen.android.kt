package screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import api.AtualizarOutraDespesaRequest
import api.ApiClient
import api.OutraDespesaItem
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.DateInputField
import util.rememberCameraState
import util.rememberSaveableTextField
import util.converterDataParaAPI

private val TIPOS_OD = listOf(
    "Pedágio"    to Icons.Default.Toll,
    "Refeição"   to Icons.Default.Restaurant,
    "Hospedagem" to Icons.Default.Hotel,
    "Lavagem"    to Icons.Default.LocalCarWash,
    "Outros"     to Icons.Default.MoreHoriz
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarOutraDespesaScreen(
    repository: AppRepository,
    item: OutraDespesaItem,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var salvando  by remember { mutableStateOf(false) }
    var erro      by remember { mutableStateOf<String?>(null) }
    var sucesso   by remember { mutableStateOf<String?>(null) }

    // Pré-popula com os dados que já vieram do card — sem nenhuma chamada de API
    var tipoDespesa  by rememberSaveable { mutableStateOf(item.tipo) }
    var descricao    by rememberSaveable { mutableStateOf(item.descricao) }
    var dataDespesa  by rememberSaveable { mutableStateOf(odFormatarDataParaForm(item.data)) }
    var localDespesa by rememberSaveable { mutableStateOf(item.local) }

    val valorInicial = odFormatarDecimal(item.valor.toString())
    var valor by rememberSaveableTextField(valorInicial)

    val cameraState = rememberCameraState(context, prefix = "OD_EDIT")

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success || cameraState.checkPhotoExistsAfterCapture()) cameraState.onPhotoTaken()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runCatching { cameraLauncher.launch(cameraState.prepareCapture()) }
    }

    fun tirarFoto() {
        runCatching {
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ->
                    cameraLauncher.launch(cameraState.prepareCapture())
                else -> permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    fun salvar() {
        if (tipoDespesa.isEmpty()) { erro = "Selecione o tipo de despesa"; return }
        if (valor.text.isEmpty())  { erro = "Informe o valor"; return }
        if (dataDespesa.isEmpty()) { erro = "Informe a data"; return }

        scope.launch {
            salvando = true
            erro = null
            try {
                val valorAPI = valor.text.replace(".", "").replace(",", ".")
                val response = ApiClient.atualizarOutraDespesa(
                    AtualizarOutraDespesaRequest(
                        despesa_id      = item.id,
                        motorista_id    = motorista?.motorista_id ?: "",
                        viagem_id       = viagemId,
                        tipo            = tipoDespesa,
                        descricao       = descricao.ifEmpty { tipoDespesa },
                        valor           = valorAPI,
                        data            = converterDataParaAPI(dataDespesa),
                        local           = localDespesa.ifEmpty { null },
                        foto_comprovante = cameraState.base64
                    )
                )
                if (response.status == "ok") {
                    sucesso = "Despesa atualizada com sucesso!"
                    kotlinx.coroutines.delay(1500)
                    onVoltar()
                } else {
                    erro = response.mensagem ?: "Erro ao atualizar"
                }
            } catch (e: Exception) {
                erro = "Sem conexão. Verifique sua internet."
            }
            salvando = false
        }
    }

    Scaffold(
        topBar = { GradientTopBar(title = "Editar Despesa", onBackClick = onVoltar) }
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
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                    // ── Tipo ─────────────────────────────────────
                    Text("Tipo de Despesa *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TIPOS_OD.take(3).forEach { (tipo, icone) ->
                                TipoBotaoOD(
                                    tipo = tipo, icone = icone,
                                    selecionado = tipoDespesa == tipo,
                                    onClick = { tipoDespesa = tipo },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TIPOS_OD.drop(3).forEach { (tipo, icone) ->
                                TipoBotaoOD(
                                    tipo = tipo, icone = icone,
                                    selecionado = tipoDespesa == tipo,
                                    onClick = { tipoDespesa = tipo },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Valor ─────────────────────────────────────
                    Text("Valor *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = valor,
                        onValueChange = { nv: TextFieldValue ->
                            val f = odFormatarMoeda(nv.text)
                            valor = TextFieldValue(f, selection = TextRange(f.length))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ui.darkTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = AppColors.Primary) },
                        prefix = { Text("R$ ") },
                        placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) }
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Data ──────────────────────────────────────
                    DateInputField(
                        value = dataDespesa,
                        onValueChange = { dataDespesa = it },
                        label = "Data *",
                        primaryColor = AppColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Descrição ─────────────────────────────────
                    Text("Descrição", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = descricao,
                        onValueChange = { descricao = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ui.darkTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Description, null, tint = AppColors.Primary) },
                        placeholder = { Text("Detalhe opcional", color = Color(0xFF9CA3AF)) }
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Local ─────────────────────────────────────
                    Text("Local", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = localDespesa,
                        onValueChange = { localDespesa = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ui.darkTextFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = AppColors.Primary) },
                        placeholder = { Text("Cidade ou estabelecimento", color = Color(0xFF9CA3AF)) }
                    )

                    Spacer(Modifier.height(16.dp))

                    // ── Foto comprovante ──────────────────────────
                    Text("Foto do Comprovante", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Spacer(Modifier.height(8.dp))

                    if (cameraState.hasPhoto && cameraState.bitmap != null) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))) {
                            Image(
                                bitmap = cameraState.bitmap!!.asImageBitmap(),
                                contentDescription = "Comprovante",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Row(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { cameraState.clear() },
                                    modifier = Modifier.size(36.dp).background(AppColors.Error, RoundedCornerShape(50))) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { tirarFoto() },
                                    modifier = Modifier.size(36.dp).background(AppColors.Primary, RoundedCornerShape(50))) {
                                    Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth().height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, AppColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .clickable { tirarFoto() },
                            color = AppColors.Primary.copy(alpha = 0.04f)
                        ) {
                            Column(Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.CameraAlt, null, tint = AppColors.Primary, modifier = Modifier.size(36.dp))
                                Spacer(Modifier.height(6.dp))
                                Text("Tirar Foto", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                                Text("Toque para capturar", color = AppColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }

                    // ── Mensagens ─────────────────────────────────
                    erro?.let {
                        Spacer(Modifier.height(14.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(10.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Error, null, tint = AppColors.Error)
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = AppColors.Error, fontSize = 14.sp)
                            }
                        }
                    }
                    sucesso?.let {
                        Spacer(Modifier.height(14.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = if (ui.isDark()) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(10.dp)) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = AppColors.Secondary)
                                Spacer(Modifier.width(8.dp))
                                Text(it, color = AppColors.Secondary, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    Button(
                        onClick = { salvar() },
                        enabled = !salvando,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) {
                        if (salvando) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("SALVAR ALTERAÇÕES", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun TipoBotaoOD(
    tipo: String,
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    selecionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .border(
                width = if (selecionado) 2.dp else 1.dp,
                color = if (selecionado) AppColors.Primary else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        color = if (selecionado) AppColors.Primary.copy(alpha = 0.08f) else Color.White
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icone, null,
                tint = if (selecionado) AppColors.Primary else AppColors.TextSecondary,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(tipo, fontSize = 10.sp,
                fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Normal,
                color = if (selecionado) AppColors.Primary else AppColors.TextSecondary)
        }
    }
}

private fun odFormatarMoeda(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    val decimal = value / 100.0
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
    symbols.decimalSeparator = ','
    symbols.groupingSeparator = '.'
    return java.text.DecimalFormat("#,##0.00", symbols).format(decimal)
}

private fun odFormatarDecimal(valor: String): String {
    return try {
        val d = valor.toDoubleOrNull() ?: return ""
        val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
        symbols.decimalSeparator = ','
        symbols.groupingSeparator = '.'
        java.text.DecimalFormat("#,##0.00", symbols).format(d)
    } catch (e: Exception) { valor }
}

private fun odFormatarDataParaForm(data: String): String {
    return try {
        val p = data.split("-")
        if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else data
    } catch (e: Exception) { data }
}
