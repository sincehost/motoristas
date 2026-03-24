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
import util.DateInputField
import util.rememberCameraState
import util.dataAtualFormatada
import util.converterDataParaAPI
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarCombustivelScreen(
    repository: AppRepository,
    abastecimentoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
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

    // Dados do abastecimento
    var destino by remember { mutableStateOf("") }
    var dataViagem by remember { mutableStateOf("") }

    // Equipamentos (placas)
    var equipamentos by remember { mutableStateOf<List<Pair<Long, String>>>(emptyList()) }

    // Campos do formulário
    var placaSelecionada by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var data by rememberSaveable { mutableStateOf(dataAtualFormatada()) }
    var nomePosto by rememberSaveable { mutableStateOf("") }
    var kmPosto by rememberSaveable { mutableStateOf("") }
    var tipoCombustivel by rememberSaveable { mutableStateOf("") }
    var horas by rememberSaveable { mutableStateOf("") }
    var litrosAbastecidos by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorLitro by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var valorTotal by remember { mutableStateOf(TextFieldValue("", selection = TextRange(0))) }
    var tipoPagamento by rememberSaveable { mutableStateOf("") }

    // Fotos
    val cameraStateMarcador = rememberCameraState(context, prefix = "COMB_EDIT_MARC")
    val cameraStateCupom = rememberCameraState(context, prefix = "COMB_EDIT_CUP")

    // Dropdowns
    var placaExpandida by remember { mutableStateOf(false) }
    var combustivelExpandido by remember { mutableStateOf(false) }

    // Camera
    var currentPhotoType by rememberSaveable { mutableStateOf("") }

    // Carrega dados ao iniciar
    LaunchedEffect(Unit) {
        carregando = true

        try {
            // Carrega equipamentos do banco local
            equipamentos = repository.getEquipamentosParaDropdown()

            // Busca dados do abastecimento da API
            val response = api.ApiClient.buscarAbastecimento(
                api.BuscarAbastecimentoRequest(
                    abastecimento_id = abastecimentoId,
                    motorista_id = motorista?.motorista_id ?: ""
                )
            )

            if (response.status == "ok" && response.abastecimento != null) {
                val abast = response.abastecimento

                // Dados da viagem
                destino = abast.destino
                dataViagem = abast.data_viagem

                // Campos do formulário
                placaSelecionada = equipamentos.find { it.second == abast.placa }
                data = formatarDataBRParaExibicao(abast.data_abastecimento)
                nomePosto = abast.nome_posto
                kmPosto = abast.km_posto
                tipoCombustivel = abast.tipo_combustivel
                horas = abast.horas
                litrosAbastecidos = TextFieldValue(formatarDecimalParaExibicao(abast.litros_abastecidos))
                valorLitro = TextFieldValue(formatarDecimalParaExibicao(abast.valor_litro))
                valorTotal = TextFieldValue(formatarDecimalParaExibicao(abast.valor_total))
                tipoPagamento = abast.forma_pagamento.lowercase() // ← garante lowercase

                // Carrega fotos se existirem

                if (!abast.foto_marcador.isNullOrEmpty()) {
                    cameraStateMarcador.loadExisting(abast.foto_marcador)
                    cameraStateMarcador.loadExisting(abast.foto_marcador)
                }
                if (!abast.foto_cupom.isNullOrEmpty()) {
                    cameraStateCupom.loadExisting(abast.foto_cupom)
                    cameraStateCupom.loadExisting(abast.foto_cupom)
                }

                modoOffline = false
            } else {
                erro = "Abastecimento não encontrado"
            }
        } catch (e: Exception) {
            modoOffline = true
            erro = "Erro ao carregar dados: ${e.message}"
        }

        carregando = false
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                val uri = when (currentPhotoType) {
                    "marcador" -> cameraStateMarcador.prepareCapture()
                    else -> cameraStateCupom.prepareCapture()
                }
                cameraLauncher.launch(uri)
            } catch (e: Exception) { }
        }
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

    // Função atualizar
    fun atualizarAbastecimento() {
        // Validação
        if (placaSelecionada == null) { erro = "Selecione uma placa"; return }
        if (nomePosto.isEmpty()) { erro = "Informe o nome do posto"; return }
        if (kmPosto.isEmpty()) { erro = "Informe o KM no posto"; return }
        if (tipoCombustivel.isEmpty()) { erro = "Selecione o tipo de combustível"; return }
        if (tipoCombustivel == "Diesel Aparelho" && horas.isEmpty()) { erro = "Informe as horas"; return }
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
                val response = api.ApiClient.atualizarAbastecimento(
                    api.AtualizarAbastecimentoRequest(
                        abastecimento_id = abastecimentoId,
                        motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemId,
                        placa = placaSelecionada!!.second,
                        data_abastecimento = converterDataParaAPI(data),
                        nome_posto = nomePosto,
                        km_posto = kmPosto,
                        tipo_combustivel = tipoCombustivel,
                        horas = horas,
                        litros_abastecidos = litrosAbastecidos.text.replace(",", "."),
                        valor_litro = valorLitro.text.replace(",", "."),
                        valor_total = valorTotal.text.replace(",", "."),
                        forma_pagamento = tipoPagamento,
                        foto_cupom = cameraStateCupom.base64,
                        foto_marcador = cameraStateMarcador.base64
                    )
                )

                if (response.status == "ok") {
                    sucesso = "Abastecimento atualizado com sucesso!"
                    kotlinx.coroutines.delay(1500)
                    onVoltar()
                } else {
                    erro = response.mensagem ?: "Erro ao atualizar"
                }

            } catch (e: Exception) {
                erro = "Erro ao atualizar: ${e.message}"
            }
            salvando = false
        }
    }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar Combustível",
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
                // Aviso offline
                if (modoOffline) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Orange.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudOff, null, tint = AppColors.Orange)
                            Spacer(Modifier.width(8.dp))
                            Text("Sem conexão. Conecte para editar abastecimento.", color = AppColors.Orange, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                // Card da viagem
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Primary.copy(alpha = 0.1f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Viagem", fontSize = 12.sp, color = AppColors.TextSecondary)
                            Text(destino, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppColors.TextPrimary)
                            Text("Iniciada em: ${formatarDataBR(dataViagem)}", fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {

                        // Data
                        DateInputField(
                            value = data,
                            onValueChange = { data = it },
                            label = "Data *",
                            primaryColor = AppColors.Primary,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(16.dp))

                        // Placa
                        Text("Placa *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
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
                                    DropdownMenuItem(text = { Text(placa) }, onClick = { placaSelecionada = id to placa; placaExpandida = false })
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Foto Quilometragem
                        Text("Foto Quilometragem *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaAbastecimentoEdit(foto = cameraStateMarcador.bitmap, onClick = { tirarFoto("marcador") }, onRemover = { cameraStateMarcador.clear() })

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
                            onValueChange = { kmPosto = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = AppColors.Primary) }
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
                                DropdownMenuItem(text = { Text("Diesel Caminhão") }, onClick = { tipoCombustivel = "Diesel Caminhão"; combustivelExpandido = false })
                                DropdownMenuItem(text = { Text("Diesel Aparelho") }, onClick = { tipoCombustivel = "Diesel Aparelho"; combustivelExpandido = false })
                            }
                        }

                        // Campo Horas (só para Diesel Aparelho)
                        if (tipoCombustivel == "Diesel Aparelho") {
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
                                val formatted = formatarDecimalCombEdit(newValue.text)
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
                                val formatted = formatarValorLitroCombEdit(newValue.text)
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
                                val formatted = formatarMoedaCombEdit(newValue.text)
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
                            BotaoOpcaoCombEdit("CTF", Icons.Default.CreditCard, tipoPagamento == "ctf", { tipoPagamento = "ctf" }, Modifier.weight(1f))
                            BotaoOpcaoCombEdit("Dinheiro", Icons.Default.Money, tipoPagamento == "dinheiro", { tipoPagamento = "dinheiro" }, Modifier.weight(1f))
                        }

                        Spacer(Modifier.height(16.dp))

                        // Foto Cupom Fiscal
                        Text("Foto Cupom Fiscal *", fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        FotoCapturaAbastecimentoEdit(foto = cameraStateCupom.bitmap, onClick = { tirarFoto("cupom") }, onRemover = { cameraStateCupom.clear() })

                        // Mensagens
                        erro?.let {
                            Spacer(Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = 0.1f))) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, null, tint = AppColors.Error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = AppColors.Error)
                                }
                            }
                        }

                        sucesso?.let {
                            Spacer(Modifier.height(16.dp))
                            Card(colors = CardDefaults.cardColors(containerColor = if (ui.isDark()) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f))) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Secondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(it, color = AppColors.Secondary)
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Botão Atualizar
                        Button(
                            onClick = { atualizarAbastecimento() },
                            enabled = !salvando && !modoOffline,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                        ) {
                            if (salvando) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ATUALIZAR ABASTECIMENTO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
private fun FotoCapturaAbastecimentoEdit(foto: Bitmap?, onClick: () -> Unit, onRemover: () -> Unit) {
    if (foto != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Miniatura da foto
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = foto.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Botão remover
                    IconButton(
                        onClick = onRemover,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Surface(
                            color = AppColors.Error,
                            shape = RoundedCornerShape(50),
                            shadowElevation = 4.dp
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remover foto",
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                    }

                    // Badge de visualização
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Foto capturada",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Botão para tirar nova foto
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick() },
                    color = AppColors.Primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = AppColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tirar nova foto",
                            color = AppColors.Primary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, AppColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clickable { onClick() },
            color = AppColors.Primary.copy(alpha = 0.05f)
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Tirar Foto",
                    color = AppColors.Primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Toque para capturar",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BotaoOpcaoCombEdit(texto: String, icone: androidx.compose.ui.graphics.vector.ImageVector, selecionado: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

private fun formatarDecimalCombEdit(input: String): String {
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

private fun formatarValorLitroCombEdit(input: String): String {
    val digits = input.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val value = digits.toLongOrNull() ?: return ""
    if (value == 0L) return ""
    val decimal = value / 100.0
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
    symbols.decimalSeparator = ','
    symbols.groupingSeparator = '.'
    val formatter = java.text.DecimalFormat("#,##0.00", symbols)
    return formatter.format(decimal)
}

private fun formatarMoedaCombEdit(input: String): String {
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

private fun formatarDataBR(data: String): String {
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

private fun formatarDataBRParaExibicao(data: String): String {
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

private fun formatarDecimalParaExibicao(valor: String): String {
    return try {
        val decimal = valor.toDoubleOrNull() ?: return ""
        val symbols = java.text.DecimalFormatSymbols(java.util.Locale("pt", "BR"))
        symbols.decimalSeparator = ','
        symbols.groupingSeparator = '.'
        val formatter = java.text.DecimalFormat("#,##0.00", symbols)
        formatter.format(decimal)
    } catch (e: Exception) {
        valor
    }
}

