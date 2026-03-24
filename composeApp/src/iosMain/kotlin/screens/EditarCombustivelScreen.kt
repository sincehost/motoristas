package screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppColors
import ui.GradientTopBar
import util.dataAtualFormatada
import util.converterDataParaAPI

private val TIPOS_COMBUSTIVEL = listOf("Diesel Caminhão", "Diesel S10", "Diesel Aparelho", "Gasolina", "Etanol")
private val TIPOS_PAGAMENTO = listOf("dinheiro", "cartao", "pix", "vale")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun EditarCombustivelScreen(
    repository: AppRepository,
    abastecimentoId: Int,
    viagemId: Int,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()

    var carregando by remember { mutableStateOf(true) }
    var salvando by remember { mutableStateOf(false) }
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    // Dados da viagem (info)
    var destino by remember { mutableStateOf("") }
    var dataViagem by remember { mutableStateOf("") }

    // Campos do formulário
    var placa by remember { mutableStateOf("") }
    var data by remember { mutableStateOf("") }
    var nomePosto by remember { mutableStateOf("") }
    var kmPosto by remember { mutableStateOf("") }
    var tipoCombustivel by remember { mutableStateOf("Diesel Caminhão") }
    var horas by remember { mutableStateOf("") }
    var litros by remember { mutableStateOf("") }
    var valorLitro by remember { mutableStateOf("") }
    var valorTotal by remember { mutableStateOf("") }
    var tipoPagamento by remember { mutableStateOf("dinheiro") }

    var combustivelExpanded by remember { mutableStateOf(false) }

    // Carrega dados da API
    LaunchedEffect(Unit) {
        carregando = true
        try {
            val response = api.ApiClient.buscarAbastecimento(
                api.BuscarAbastecimentoRequest(abastecimento_id = abastecimentoId, motorista_id = motorista?.motorista_id ?: "")
            )
            if (response.status == "ok" && response.abastecimento != null) {
                val a = response.abastecimento
                destino = a.destino; dataViagem = a.data_viagem; placa = a.placa
                data = a.data_abastecimento; nomePosto = a.nome_posto; kmPosto = a.km_posto
                tipoCombustivel = a.tipo_combustivel; horas = a.horas
                litros = a.litros_abastecidos; valorLitro = a.valor_litro
                valorTotal = a.valor_total; tipoPagamento = a.forma_pagamento.lowercase()
            } else {
                erroMsg = "Abastecimento não encontrado"
            }
        } catch (e: Exception) {
            erroMsg = "Erro ao carregar: ${e.message}"
        }
        carregando = false
    }

    fun salvar() {
        if (nomePosto.isBlank()) { erroMsg = "Informe o posto"; return }
        if (kmPosto.isBlank()) { erroMsg = "Informe o KM"; return }
        if (litros.isBlank()) { erroMsg = "Informe os litros"; return }
        if (valorTotal.isBlank()) { erroMsg = "Informe o valor total"; return }

        scope.launch {
            salvando = true
            try {
                val resp = api.ApiClient.atualizarAbastecimento(
                    api.AtualizarAbastecimentoRequest(
                        abastecimento_id = abastecimentoId, motorista_id = motorista?.motorista_id ?: "",
                        viagem_id = viagemId, placa = placa, data_abastecimento = data,
                        nome_posto = nomePosto, km_posto = kmPosto, tipo_combustivel = tipoCombustivel,
                        horas = horas, litros_abastecidos = litros.replace(",", "."),
                        valor_litro = valorLitro.replace(",", "."), valor_total = valorTotal.replace(",", "."),
                        forma_pagamento = tipoPagamento, foto_cupom = null, foto_marcador = null
                    )
                )
                if (resp.status == "ok") sucessoMsg = "Abastecimento atualizado!" else erroMsg = resp.mensagem ?: "Erro"
            } catch (e: Exception) { erroMsg = "Erro: ${e.message}" }
            salvando = false
        }
    }

    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null; onVoltar() }

    Scaffold(topBar = { GradientTopBar(title = "Editar Combustível", onBackClick = onVoltar) }) { padding ->
        when {
            carregando -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppColors.Primary); Spacer(Modifier.height(16.dp))
                    Text("Carregando dados...", color = AppColors.TextSecondary)
                }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding).background(AppColors.Background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                // Info da viagem
                if (destino.isNotBlank()) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = AppColors.Primary)
                            Spacer(Modifier.width(8.dp))
                            Column { Text(destino, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(dataViagem, fontSize = 12.sp, color = AppColors.TextSecondary) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(placa, {}, readOnly = true, label = { Text("Placa") },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(data, { data = it }, label = { Text("Data") },
                            leadingIcon = { Icon(Icons.Default.CalendarToday, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(nomePosto, { nomePosto = it }, label = { Text("Nome do posto") },
                            leadingIcon = { Icon(Icons.Default.LocalGasStation, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(kmPosto, { kmPosto = it.filter { c -> c.isDigit() } }, label = { Text("KM no posto") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        // Tipo combustível
                        ExposedDropdownMenuBox(expanded = combustivelExpanded, onExpandedChange = { combustivelExpanded = it }) {
                            OutlinedTextField(tipoCombustivel, {}, readOnly = true, label = { Text("Tipo combustível") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(combustivelExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp))
                            ExposedDropdownMenu(combustivelExpanded, { combustivelExpanded = false }) {
                                TIPOS_COMBUSTIVEL.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { tipoCombustivel = t; combustivelExpanded = false }) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        if (tipoCombustivel == "Diesel Aparelho") {
                            OutlinedTextField(horas, { horas = it }, label = { Text("Horas") },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(12.dp))
                        }

                        OutlinedTextField(litros, { litros = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                            label = { Text("Litros") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(valorLitro, { valorLitro = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                            label = { Text("Valor por litro (R$)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(valorTotal, { valorTotal = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                            label = { Text("Valor total (R$)") }, leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                        Spacer(Modifier.height(12.dp))

                        // Pagamento
                        Text("Forma de pagamento", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TIPOS_PAGAMENTO.forEach { tp ->
                                FilterChip(selected = tipoPagamento == tp, onClick = { tipoPagamento = tp },
                                    label = { Text(tp.replaceFirstChar { it.uppercase() }, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AppColors.Primary, selectedLabelColor = Color.White))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)) {
                    if (salvando) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp)); Text("Salvar Alterações", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
