package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import api.*
import database.AppRepository
import kotlinx.coroutines.launch
import platform.Foundation.*
import ui.AppColors
import ui.GradientTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun RelatorioViagensScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
) {
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()
    var erroMsg by remember { mutableStateOf<String?>(null) }
    var sucessoMsg by remember { mutableStateOf<String?>(null) }

    val meses = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )

    // Obter mês e ano atual usando NSCalendar (iOS)
    val calendar = NSCalendar.currentCalendar
    val components = calendar.components(
        NSCalendarUnitMonth or NSCalendarUnitYear,
        NSDate()
    )
    val mesAtual = (components.month - 1).toInt() // NSCalendar month é 1-indexed
    val anoAtual = components.year.toInt()

    var mesSelecionado by remember { mutableStateOf(mesAtual) }
    var anoSelecionado by remember { mutableStateOf(anoAtual) }

    var carregando by remember { mutableStateOf(false) }
    var relatorio by remember { mutableStateOf<RelatorioViagensResponse?>(null) }

    var expandedMes by remember { mutableStateOf(false) }
    var expandedAno by remember { mutableStateOf(false) }

    // Função para mostrar mensagens
    fun mostrarMensagem(mensagem: String, isErro: Boolean = false) {
        if (isErro) {
            erroMsg = mensagem
        } else {
            sucessoMsg = mensagem
        }
    }

    // Função para carregar relatório
    fun carregarRelatorio() {
        scope.launch {
            carregando = true
            try {
                val mesStr = (mesSelecionado + 1).toString().padStart(2, '0')
                val response = ApiClient.getRelatorioViagens(
                    motoristaId = motorista?.motorista_id ?: "",
                    mes = mesStr,
                    ano = anoSelecionado.toString()
                )
                if (response.status == "ok") {
                    relatorio = response
                    mostrarMensagem("✓ Relatório gerado com sucesso!")
                } else {
                    mostrarMensagem(response.mensagem ?: "Erro ao carregar relatório", isErro = true)
                }
            } catch (e: Exception) {
                mostrarMensagem("Sem conexão com internet", isErro = true)
            }
            carregando = false
        }
    }

    // Diálogos modais
    if (erroMsg != null) ui.ErroDialog(erroMsg!!) { erroMsg = null }
    if (sucessoMsg != null) ui.SucessoDialog(sucessoMsg!!) { sucessoMsg = null }

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Relatório de Viagens",
                onBackClick = onVoltar
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Card de filtro
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Selecione o período",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppColors.TextPrimary
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Seletor de Mês
                        ExposedDropdownMenuBox(
                            expanded = expandedMes,
                            onExpandedChange = { expandedMes = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = meses[mesSelecionado],
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Mês") },
                                modifier = Modifier.menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMes)
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMes,
                                onDismissRequest = { expandedMes = false }
                            ) {
                                meses.forEachIndexed { index, mes ->
                                    DropdownMenuItem(
                                        text = { Text(mes) },
                                        onClick = {
                                            mesSelecionado = index
                                            expandedMes = false
                                        }
                                    )
                                }
                            }
                        }

                        // Seletor de Ano
                        ExposedDropdownMenuBox(
                            expanded = expandedAno,
                            onExpandedChange = { expandedAno = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = anoSelecionado.toString(),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Ano") },
                                modifier = Modifier.menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAno)
                                }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAno,
                                onDismissRequest = { expandedAno = false }
                            ) {
                                (2020..anoAtual).forEach { ano ->
                                    DropdownMenuItem(
                                        text = { Text(ano.toString()) },
                                        onClick = {
                                            anoSelecionado = ano
                                            expandedAno = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { carregarRelatorio() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !carregando,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (carregando) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Gerar Relatório")
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Relatório
            relatorio?.let { rel ->
                Spacer(Modifier.height(8.dp))

                // Header do relatório
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Resumo - ${meses[mesSelecionado]}/$anoSelecionado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF10B981)
                        )
                        if (rel.motorista_nome.isNotBlank()) {
                            Text(
                                "Motorista: ${rel.motorista_nome}",
                                color = AppColors.TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Estatísticas
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        RelatorioItem("Total de Viagens:", "${rel.total_viagens}")
                        RelatorioItem("KM Rodados:", "${formatNumber(rel.total_km_rodados)} km")

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            "Combustível",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                        Spacer(Modifier.height(8.dp))
                        RelatorioItem("Litros Diesel:", "${formatNumber(rel.total_litros_diesel)} L")
                        RelatorioItem("Valor Diesel:", "R$ ${formatMoney(rel.total_valor_diesel)}")
                        RelatorioItemBadge(
                            "Média:",
                            "${formatNumber(rel.media_km_litro)} km/L",
                            Color(0xFF10B981)
                        )

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text("ARLA", fontWeight = FontWeight.Bold, color = Color(0xFF06B6D4))
                        Spacer(Modifier.height(8.dp))
                        RelatorioItem("Litros ARLA:", "${formatNumber(rel.total_litros_arla)} L")
                        RelatorioItem("Valor ARLA:", "R$ ${formatMoney(rel.total_valor_arla)}")
                        RelatorioItemBadge(
                            "Média ARLA:",
                            "${formatNumber(rel.media_arla)} km/L",
                            Color(0xFF06B6D4)
                        )

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        Text(
                            "Faturamento",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B5CF6)
                        )
                        Spacer(Modifier.height(8.dp))
                        RelatorioItem("Frete:", "R$ ${formatMoney(rel.total_valor_frete)}")
                        RelatorioItem(
                            "Frete Retorno:",
                            "R$ ${formatMoney(rel.total_valor_frete_retorno)}"
                        )
                        RelatorioItem("Descargas:", "R$ ${formatMoney(rel.total_valor_descarga)}")

                        val totalFrete = rel.total_valor_frete + rel.total_valor_frete_retorno
                        val imposto = totalFrete * 0.10
                        val saldo = totalFrete - rel.total_valor_arla - rel.total_valor_diesel - rel.total_valor_descarga - imposto

                        Divider(modifier = Modifier.padding(vertical = 12.dp))

                        RelatorioItem("Total Fretes:", "R$ ${formatMoney(totalFrete)}")
                        RelatorioItem("Imposto:", "R$ ${formatMoney(imposto)}")

                        Spacer(Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (saldo >= 0)
                                    Color(0xFF10B981).copy(alpha = 0.1f)
                                else
                                    Color(0xFFEF4444).copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "SALDO DO PERÍODO",
                                    fontWeight = FontWeight.Bold,
                                    color = if (saldo >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                                Text(
                                    "R$ ${formatMoney(saldo)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (saldo >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }

            // Se não tem relatório e não está carregando, mostra mensagem inicial
            if (relatorio == null && !carregando) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = null,
                            tint = AppColors.TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Selecione o mês e ano\ne clique em Gerar Relatório",
                            textAlign = TextAlign.Center,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatorioItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = AppColors.TextSecondary)
        Text(value, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
    }
}

@Composable
private fun RelatorioItemBadge(label: String, value: String, badgeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.TextSecondary)
        Surface(
            color = badgeColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                value,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatNumber(value: Double): String {
    // Formatar número com 2 casas decimais e separadores brasileiros
    val inteiro = value.toInt()
    val decimal = ((value - inteiro) * 100).toInt()
    val inteiroFormatado = inteiro.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$inteiroFormatado,${decimal.toString().padStart(2, '0')}"
}

private fun formatMoney(value: Double): String {
    return formatNumber(value)
}