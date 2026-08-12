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
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI
import util.rememberCameraState
import util.rememberSaveableTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun OutrasDespesasScreen(
    repository: AppRepository,
    onVoltar: () -> Unit,
    onSucesso: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Estados de UI (não precisam sobreviver — são recriados)
    var salvando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var sucesso by remember { mutableStateOf<String?>(null) }

    // Viagem atual
    val viagemAtual = remember { repository.getViagemAtual() }

    // Tipos de despesa (catálogo da empresa, cadastrado no admin)
    val tiposDespesa = remember { repository.getAllTiposDespesa() }

    // ★ FIX: Campos do formulário com rememberSaveable — sobrevivem à morte do processo
    var tipoDespesa by rememberSaveable { mutableStateOf("") }
    var tipoDespesaExpandido by remember { mutableStateOf(false) }
    var descricao by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveableTextField("")
    var dataDespesa by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var localDespesa by rememberSaveable { mutableStateOf("") }

    // ★ FIX: CameraState sobrevive à morte do processo
    // Antes: remember{} perdia URI/File quando câmera matava o app
    // Agora: salva path no onSaveInstanceState, recarrega bitmap ao voltar
    val cameraState = rememberCameraState(context, prefix = "DESP")

    // ★ FIX: cameraLauncher usa CameraState — 3 linhas em vez de 15
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success || cameraState.checkPhotoExistsAfterCapture()) {
            cameraState.onPhotoTaken()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraLauncher.launch(cameraState.prepareCapture())
        }
    }

    // Photo Picker — não requer READ_MEDIA_IMAGES
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cameraState.onGalleryPicked(uri)
        }
    }

    fun tirarFoto() {
        try {
            val uri = cameraState.prepareCapture()
            when {
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                    cameraLauncher.launch(uri)
                }
                else -> {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            erro = "Não foi possível abrir a câmera"
        }
    }

    fun salvarDespesa() {
        // Validações
        if (tipoDespesa.isEmpty()) { erro = "Selecione o tipo de despesa"; return }
        if (valor.text.isEmpty()) { erro = "Informe o valor"; return }
        if (dataDespesa.isEmpty()) { erro = "Informe a data"; return }
        if (viagemAtual == null) { erro = "Nenhuma viagem em andamento"; return }

        val dataAPI = converterDataParaAPI(dataDespesa)
        val viagemId = viagemAtual.viagem_id

        scope.launch {
            salvando = true
            erro = null
            try {
                repository.salvarOutraDespesa(
                    motoristaId = motorista?.motorista_id ?: "",
                    viagemId = viagemId,
                    tipo = tipoDespesa,
                    descricao = descricao.ifEmpty { tipoDespesa },
                    valor = valor.text,
                    data = dataAPI,
                    local = localDespesa.ifEmpty { null },
                    fotoComprovante = cameraState.base64
                )

                sucesso = "Despesa registrada com sucesso!"
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
        ui.SucessoDialog(mensagem = sucesso!!, onDismiss = { sucesso = null; onSucesso() })
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Outras Despesas",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        if (viagemAtual == null) {
            // Sem viagem
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).background(AppColors.Background).padding(16.dp),
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
                        Text("Inicie uma viagem primeiro para registrar despesas.", color = AppColors.TextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = onVoltar, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary), shape = RoundedCornerShape(12.dp)) {
                            Text("Voltar ao Dashboard")
                        }
                    }
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
                // Card de contexto da viagem
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFF6F00).copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Receipt, null, tint = Color(0xFFFF6F00), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Despesa da viagem", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Text(viagemAtual.destino, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
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

                        // Tipo de Despesa
                        Text("Tipo de Despesa *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))

                        ExposedDropdownMenuBox(expanded = tipoDespesaExpandido, onExpandedChange = { tipoDespesaExpandido = it }) {
                            OutlinedTextField(
                                value = tipoDespesa.ifEmpty { "Selecione" },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tipoDespesaExpandido) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                                leadingIcon = { Icon(Icons.Default.Receipt, null, tint = Color(0xFFFF6F00)) }
                            )
                            ExposedDropdownMenu(expanded = tipoDespesaExpandido, onDismissRequest = { tipoDespesaExpandido = false }) {
                                tiposDespesa.forEach { t ->
                                    DropdownMenuItem(text = { Text(t.nome) }, onClick = { tipoDespesa = t.nome; tipoDespesaExpandido = false })
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Valor
                        Text("Valor *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = valor,
                            onValueChange = { newValue ->
                                val formatted = formatarValorDespesa(newValue.text)
                                valor = TextFieldValue(text = formatted, selection = TextRange(formatted.length))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            prefix = { Text("R$ ") },
                            placeholder = { Text("0,00", color = Color(0xFF9CA3AF)) },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = Color(0xFFFF6F00)) })
                        

                        Spacer(Modifier.height(16.dp))

                        // Data
                        DateInputField(
                            value = dataDespesa,
                            onValueChange = { dataDespesa = it },
                            label = "Data *",
                            primaryColor = Color(0xFFFF6F00),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        // Local (opcional)
                        Text("Local", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = localDespesa,
                            onValueChange = { localDespesa = it },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Place, null, tint = Color(0xFFFF6F00)) },
                            placeholder = { Text("Ex: Posto BR Km 250 (opcional)", color = Color(0xFF9CA3AF)) })
                        

                        Spacer(Modifier.height(16.dp))

                        // Descrição (opcional)
                        Text("Descrição", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = descricao,
                            onValueChange = { descricao = it },
                            modifier = Modifier.fillMaxWidth().height(80.dp),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Notes, null, tint = Color(0xFFFF6F00)) },
                            placeholder = { Text("Detalhes adicionais (opcional)", color = Color(0xFF9CA3AF)) },
                            maxLines = 3)
                        

                        Spacer(Modifier.height(16.dp))

                        // ★ Foto do Comprovante — agora usa cameraState
                        Text("Foto do Comprovante", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))

                        if (cameraState.hasPhoto && cameraState.bitmap != null) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Image(
                                    bitmap = cameraState.bitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { cameraState.clear() },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                ) {
                                    Surface(color = AppColors.Error, shape = RoundedCornerShape(50)) {
                                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, Color(0xFFFF6F00).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable { tirarFoto() },
                                    color = Color(0xFFFF6F00).copy(alpha = 0.05f)
                                ) {
                                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFFFF6F00), modifier = Modifier.size(32.dp))
                                        Spacer(Modifier.height(6.dp))
                                        Text("Tirar Foto", color = Color(0xFFFF6F00), fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f).height(100.dp).clip(RoundedCornerShape(12.dp))
                                        .border(2.dp, Color(0xFF1976D2).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable { galleryLauncher.launch("image/*") },
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

                        Spacer(Modifier.height(24.dp))

                        // Botão Salvar
                        Button(
                            onClick = { salvarDespesa() },
                            enabled = !salvando,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("REGISTRAR DESPESA", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun formatarValorDespesa(input: String): String {
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
