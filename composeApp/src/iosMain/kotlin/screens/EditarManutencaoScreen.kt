package screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.ApiClient
import api.AtualizarManutencaoRequest
import api.DetalheManutencaoRequest
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.DateInputField
import util.dataAtualFormatada
import util.converterDataParaAPI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarManutencaoScreen(
    repository: AppRepository,
    manutencaoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    var carregando by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    
    var dataManutencao by remember { mutableStateOf(dataAtualFormatada()) }
    var placa by remember { mutableStateOf("") }
    var servico by remember { mutableStateOf("") }
    var descricaoServico by remember { mutableStateOf("") }
    var localManutencao by remember { mutableStateOf("") }
    var valor by remember { mutableStateOf("") }
    var kmTrocaOleo by remember { mutableStateOf("") }
    var kmTrocaPneu by remember { mutableStateOf("") }
    var pneus by remember { mutableStateOf("") }
    var tiposPneu by remember { mutableStateOf("") }
    var fotoComprovante1Base64 by remember { mutableStateOf<String?>(null) }
    var fotoComprovante2Base64 by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    LaunchedEffect(manutencaoId) {
        carregando = true
        try {
            val response = ApiClient.detalheManutencao(
                DetalheManutencaoRequest(

                    manutencao_id = manutencaoId
                )
            )
            if (response.status == "ok" && response.manutencao != null) {
                val manutencao = response.manutencao
                dataManutencao = manutencao.data_manutencao
                placa = manutencao.placa
                servico = manutencao.servico
                descricaoServico = manutencao.descricao_servico ?: ""
                localManutencao = manutencao.local_manutencao ?: ""
                valor = manutencao.valor.toString()
                kmTrocaOleo = manutencao.km_troca_oleo ?: ""
                kmTrocaPneu = manutencao.km_troca_pneu ?: ""
                pneus = manutencao.pneus ?: ""
                tiposPneu = manutencao.tipos_pneu ?: ""
                fotoComprovante1Base64 = manutencao.foto_comprovante1
                fotoComprovante2Base64 = manutencao.foto_comprovante2
            } else {
                mostrarMensagem("Erro ao carregar dados", isErro = true)
            }
        } catch (e: Exception) {
            mostrarMensagem("Erro: ${e.message}", isErro = true)
        }
        carregando = false
    }

    // Diálogos modais
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Editar Manutenção",
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
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState).padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Informações da Manutenção", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        Spacer(Modifier.height(20.dp))

                        DateInputField(
                            value = dataManutencao,
                            onValueChange = { dataManutencao = it },
                            label = "Data da Manutenção",
                            primaryColor = Color(0xFF06B6D4),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = placa,
                            onValueChange = { placa = it },
                            label = { Text("Placa *") },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = false
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = servico,
                            onValueChange = { servico = it },
                            label = { Text("Serviço *") },
                            leadingIcon = { Icon(Icons.Default.Build, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = descricaoServico,
                            onValueChange = { descricaoServico = it },
                            label = { Text("Descrição do Serviço") },
                            leadingIcon = { Icon(Icons.Default.Description, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            minLines = 3,
                            maxLines = 5
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = localManutencao,
                            onValueChange = { localManutencao = it },
                            label = { Text("Local da Manutenção") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = valor,
                            onValueChange = {
                                val digits = it.filter { c -> c.isDigit() }.take(7)
                                valor = formatarValor(digits)
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
                            value = kmTrocaOleo,
                            onValueChange = { kmTrocaOleo = it.filter { c -> c.isDigit() } },
                            label = { Text("KM Troca de Óleo") },
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = kmTrocaPneu,
                            onValueChange = { kmTrocaPneu = it.filter { c -> c.isDigit() } },
                            label = { Text("KM Troca de Pneu") },
                            leadingIcon = { Icon(Icons.Default.Speed, null, tint = Color(0xFF06B6D4)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ui.darkTextFieldColors(), shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        Spacer(Modifier.height(16.dp))

                        Text("Fotos dos Comprovantes", fontWeight = FontWeight.Medium, color = AppColors.TextSecondary, fontSize = 12.sp)
                        Text("(Funcionalidade de câmera em desenvolvimento para iOS)", fontSize = 10.sp, color = Color.Gray)

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    if (dataManutencao.isBlank()) { mostrarMensagem("Informe a data", isErro = true); return@launch }
                                    if (servico.isBlank()) { mostrarMensagem("Informe o serviço", isErro = true); return@launch }
                                    if (valor.isBlank()) { mostrarMensagem("Informe o valor", isErro = true); return@launch }

                                    salvando = true
                                    try {
                                        val response = ApiClient.atualizarManutencao(
                                            AtualizarManutencaoRequest(
                                                manutencao_id = manutencaoId,
                                                motorista_id = motorista?.motorista_id ?: "",
                                                viagem_id = viagemId,
                                                data_manutencao = converterDataParaAPI(dataManutencao),
                                                placa = placa,
                                                servico = servico,
                                                descricao_servico = descricaoServico.ifBlank { null },
                                                local_manutencao = localManutencao.ifBlank { null },
                                                valor = valor,
                                                km_troca_oleo = kmTrocaOleo.ifBlank { null },
                                                km_troca_pneu = kmTrocaPneu.ifBlank { null },
                                                pneus = pneus.ifBlank { null }?.split(",")?.mapNotNull { it.trim().toIntOrNull() },
                                                tipos_pneu = tiposPneu.ifBlank { null }?.split(";")?.mapNotNull {
                                                    val parts = it.split(":")
                                                    if (parts.size == 2) parts[0].trim().toIntOrNull()?.let { k -> k to parts[1].trim() } else null
                                                }?.toMap(),
                                                foto_comprovante1 = fotoComprovante1Base64,
                                                foto_comprovante2 = fotoComprovante2Base64
                                            )
                                        )
                                        if (response.status == "ok") {
                                            sucessoMsg = "Manutenção atualizada com sucesso!"
                                        } else {
                                            mostrarMensagem(response.mensagem ?: "Erro ao salvar", isErro = true)
                                        }
                                    } catch (e: Exception) {
                                        mostrarMensagem("Erro: ${e.message}", isErro = true)
                                    }
                                    salvando = false
                                }
                            },
                            enabled = !salvando,
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
