package screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.core.content.FileProvider
import api.*
import database.AppRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import ui.AppColors
import ui.GradientTopBar
import java.io.File
import java.io.FileOutputStream
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun RelatorioViagensScreen(
    repository: AppRepository,
    onVoltar: () -> Unit
) {
    val context = LocalContext.current
    val motorista = remember { repository.getMotoristaLogado() }
    val scope = rememberCoroutineScope()

    val meses = listOf(
        "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
    )
    
    val calendar = Calendar.getInstance()
    var mesSelecionado by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var anoSelecionado by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    
    var carregando by remember { mutableStateOf(false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var relatorio by remember { mutableStateOf<RelatorioViagensResponse?>(null) }
    
    var expandedMes by remember { mutableStateOf(false) }
    var expandedAno by remember { mutableStateOf(false) }

    // Função para carregar relatório
    fun carregarRelatorio() {
        scope.launch {
            carregando = true
            erro = null
            try {
                val mesStr = String.format("%02d", mesSelecionado + 1)
                val response = ApiClient.getRelatorioViagens(

                    motoristaId = motorista?.motorista_id ?: "",
                    mes = mesStr,
                    ano = anoSelecionado.toString()
                )
                if (response.status == "ok") {
                    relatorio = response
                } else {
                    erro = response.mensagem ?: "Erro ao carregar relatório"
                }
            } catch (e: Exception) {
                erro = "Sem conexão com internet"
            }
            carregando = false
        }
    }

    // Função para exportar PDF
    fun exportarPdf(rel: RelatorioViagensResponse) {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas: Canvas = page.canvas
            val paint = Paint().apply { isAntiAlias = true }

            var y = 50f
            val left = 40f
            val right = 555f

            // Título
            paint.textSize = 20f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor("#07275A")
            canvas.drawText("Relatório de Viagens", left, y, paint); y += 28f
            paint.textSize = 14f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.parseColor("#666666")
            canvas.drawText("${meses[mesSelecionado]} / $anoSelecionado", left, y, paint); y += 18f
            if (rel.motorista_nome.isNotBlank()) { canvas.drawText("Motorista: ${rel.motorista_nome}", left, y, paint); y += 18f }
            y += 10f

            // Linha separadora
            paint.color = android.graphics.Color.parseColor("#CCCCCC"); paint.strokeWidth = 1f
            canvas.drawLine(left, y, right, y, paint); y += 20f

            fun drawItem(label: String, value: String) {
                paint.textSize = 12f; paint.isFakeBoldText = false; paint.color = android.graphics.Color.parseColor("#555555")
                canvas.drawText(label, left, y, paint)
                paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor("#1A202C")
                canvas.drawText(value, 300f, y, paint); y += 20f
            }

            fun drawSection(title: String, color: String) {
                y += 8f; paint.textSize = 14f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor(color)
                canvas.drawText(title, left, y, paint); y += 22f
            }

            drawSection("Viagens", "#07275A")
            drawItem("Total de Viagens:", "${rel.total_viagens}")
            drawItem("KM Rodados:", "${formatNumber(rel.total_km_rodados)} km")

            drawSection("Combustível", "#F59E0B")
            drawItem("Litros Diesel:", "${formatNumber(rel.total_litros_diesel)} L")
            drawItem("Valor Diesel:", "R$ ${formatMoney(rel.total_valor_diesel)}")
            drawItem("Média:", "${formatNumber(rel.media_km_litro)} km/L")

            drawSection("ARLA", "#06B6D4")
            drawItem("Litros ARLA:", "${formatNumber(rel.total_litros_arla)} L")
            drawItem("Valor ARLA:", "R$ ${formatMoney(rel.total_valor_arla)}")
            drawItem("Média ARLA:", "${formatNumber(rel.media_arla)} km/L")

            drawSection("Faturamento", "#8B5CF6")
            drawItem("Frete:", "R$ ${formatMoney(rel.total_valor_frete)}")
            drawItem("Frete Retorno:", "R$ ${formatMoney(rel.total_valor_frete_retorno)}")
            drawItem("Descargas:", "R$ ${formatMoney(rel.total_valor_descarga)}")
            if (rel.total_outras_despesas > 0) {
                drawItem("Outras Despesas:", "R$ ${formatMoney(rel.total_outras_despesas)}")
            }

            val totalFrete = rel.total_valor_frete + rel.total_valor_frete_retorno
            val imposto = totalFrete * 0.10
            val saldo = totalFrete - rel.total_valor_arla - rel.total_valor_diesel - rel.total_valor_descarga - rel.total_outras_despesas - imposto

            y += 5f
            paint.color = android.graphics.Color.parseColor("#CCCCCC"); canvas.drawLine(left, y, right, y, paint); y += 20f
            drawItem("Total Fretes:", "R$ ${formatMoney(totalFrete)}")
            drawItem("Imposto (10%):", "R$ ${formatMoney(imposto)}")

            y += 10f
            val saldoColor = if (saldo >= 0) "#10B981" else "#EF4444"
            paint.textSize = 16f; paint.isFakeBoldText = true; paint.color = android.graphics.Color.parseColor(saldoColor)
            canvas.drawText("SALDO: R$ ${formatMoney(saldo)}", left, y, paint)

            document.finishPage(page)

            val fileName = "relatorio_${meses[mesSelecionado].lowercase()}_$anoSelecionado.pdf"
            val file = File(context.cacheDir, fileName)
            document.writeTo(FileOutputStream(file))
            document.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Abrir relatório PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Erro ao gerar PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            GradientTopBar(
                title = "Relatório de Viagens",
                onBackClick = onVoltar
            )
        }
    )
    { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                if (relatorio != null) {
                    isRefreshing = true
                    carregarRelatorio()
                    scope.launch {
                        delay(500)
                        isRefreshing = false
                    }
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMes) }
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
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAno) }
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAno,
                                onDismissRequest = { expandedAno = false }
                            ) {
                                (2020..calendar.get(Calendar.YEAR)).forEach { ano ->
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
            
            // Erro
            erro?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Error.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, null, tint = AppColors.Error)
                        Spacer(Modifier.width(12.dp))
                        Text(msg, color = AppColors.Error)
                    }
                }
            }
            
            // Relatório
            relatorio?.let { rel ->
                Spacer(Modifier.height(8.dp))
                
                // Header do relatório
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.1f))
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
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        Text("Combustível", fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        Spacer(Modifier.height(8.dp))
                        RelatorioItem("Litros Diesel:", "${formatNumber(rel.total_litros_diesel)} L")
                        RelatorioItem("Valor Diesel:", "R$ ${formatMoney(rel.total_valor_diesel)}")
                        RelatorioItemBadge("Média:", "${formatNumber(rel.media_km_litro)} km/L", Color(0xFF10B981))
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        Text("ARLA", fontWeight = FontWeight.Bold, color = Color(0xFF06B6D4))
                        Spacer(Modifier.height(8.dp))
                        RelatorioItem("Litros ARLA:", "${formatNumber(rel.total_litros_arla)} L")
                        RelatorioItem("Valor ARLA:", "R$ ${formatMoney(rel.total_valor_arla)}")
                        RelatorioItemBadge("Média ARLA:", "${formatNumber(rel.media_arla)} km/L", Color(0xFF06B6D4))
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        Text("Faturamento", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        Spacer(Modifier.height(8.dp))
                        RelatorioItem("Frete:", "R$ ${formatMoney(rel.total_valor_frete)}")
                        RelatorioItem("Frete Retorno:", "R$ ${formatMoney(rel.total_valor_frete_retorno)}")
                        RelatorioItem("Descargas:", "R$ ${formatMoney(rel.total_valor_descarga)}")
                        if (rel.total_outras_despesas > 0) {
                            RelatorioItem("Outras Despesas:", "R$ ${formatMoney(rel.total_outras_despesas)}")
                        }
                        
                        val totalFrete = rel.total_valor_frete + rel.total_valor_frete_retorno
                        val imposto = totalFrete * 0.10
                        val saldo = totalFrete - rel.total_valor_arla - rel.total_valor_diesel - rel.total_valor_descarga - rel.total_outras_despesas - imposto
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        RelatorioItem("Total Fretes:", "R$ ${formatMoney(totalFrete)}")
                        RelatorioItem("Imposto:", "R$ ${formatMoney(imposto)}")
                        
                        Spacer(Modifier.height(8.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (saldo >= 0) Color(0xFF10B981).copy(alpha = 0.1f) 
                                                else Color(0xFFEF4444).copy(alpha = 0.1f)
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
                
                // Botão Exportar PDF
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { exportarPdf(rel) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar PDF", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(32.dp))
            }
            
            // Se não tem relatório e não está carregando, mostra mensagem inicial
            if (relatorio == null && !carregando && erro == null) {
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
        } // PullToRefreshBox
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
    return String.format("%,.2f", value).replace(",", "X").replace(".", ",").replace("X", ".")
}

private fun formatMoney(value: Double): String {
    return formatNumber(value)
}
