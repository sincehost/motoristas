package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sync.SyncState
import sync.SyncStatus
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import ui.AppColors
import ui.isDark
import util.formatarKmExibicao

// ===============================
// CARD "ENVIO COM PROBLEMA" — lançamentos que o servidor recusou (erro de
// dado, não de conexão). Só aparece quando há algo pro motorista corrigir;
// some sozinho quando ele corrige ou descarta na tela de Pendências.
// ===============================
@Composable
fun EnvioComProblemaCard(itens: List<database.LancamentoRecusado>, onVerECorrigir: () -> Unit) {
    if (itens.isEmpty()) return
    val isDarkMode = isDark()
    val laranja = Color(0xFFC2410C)
    val laranjaTexto = if (isDarkMode) Color(0xFFFDBA8C) else Color(0xFF9A3412)
    val laranjaFundo = if (isDarkMode) Color(0xFF3D2A1E) else Color(0xFFFDEBDD)
    val borda = if (isDarkMode) Color(0xFF5A3A28) else Color(0xFFF3D3BF)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borda),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("PRÓXIMA AÇÃO", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = AppColors.TextSecondary)
                Text(
                    "Precisa corrigir",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = laranjaTexto,
                    modifier = Modifier.background(laranjaFundo, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier.size(52.dp).background(laranjaFundo, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(ui.Lucide.TriangleAlert, null, tint = laranja, modifier = Modifier.size(28.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (itens.size == 1) "1 lançamento não foi enviado" else "${itens.size} lançamentos não foram enviados",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, lineHeight = 23.sp
                    )
                    Text(
                        "O servidor recusou. Corrija para não perder os dados.",
                        fontSize = 13.sp, color = AppColors.TextSecondary, lineHeight = 18.sp
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itens.take(3).forEach { item ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .background(if (isDarkMode) AppColors.SurfaceVariant else AppColors.Background, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val data = formatarDiaMes(item.data)
                        Text(
                            if (data.isNotEmpty()) "${item.tipo} · $data" else item.tipo,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary
                        )
                        Text(item.erro, fontSize = 12.sp, color = laranjaTexto, maxLines = 2)
                    }
                }
                if (itens.size > 3) {
                    Text("+ ${itens.size - 3} outro(s)", fontSize = 12.sp, color = AppColors.TextSecondary)
                }
            }
            Button(
                onClick = onVerECorrigir,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = laranja, contentColor = Color.White)
            ) {
                Text("Ver e corrigir", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(ui.Lucide.ArrowRight, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** "2026-10-20" (ou "2026-10-20 08:00:00") → "20/10". Outro formato: vazio. */
private fun formatarDiaMes(data: String): String {
    val p = data.trim().split(" ").first().split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}" else ""
}

// ===============================
// CARD DE ALERTA — PLANO DE MANUTENÇÃO (itens vencidos/vencendo por data,
// cadastrados pelo admin — mesmo alerta que aparece no painel).
// ===============================
@Composable
fun PlanoManutencaoAlertCard(itens: List<api.PlanoManutencaoItem>, onRegistrar: (() -> Unit)? = null) {
    if (itens.isEmpty()) return
    val isDarkMode = isDark()
    val backgroundColor = if (isDarkMode) Color(0xFF3D2E1A) else Color(0xFFFFF3E0)
    val iconColor = if (isDarkMode) Color(0xFFFFB74D) else Color(0xFFFF6F00)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null, tint = iconColor, modifier = Modifier.size(24.dp))
                Text(
                    "Manutenção programada (${itens.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor
                )
            }
            itens.take(4).forEach { item ->
                val prazo = if (item.vencido) "Vencido" else "Vence em ${item.dias} dia(s)"
                Text(
                    "${item.placa} — ${item.item} ($prazo)",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
            }
            if (itens.size > 4) {
                Text("+ ${itens.size - 4} outro(s) item(ns)", fontSize = 12.sp, color = AppColors.TextSecondary)
            }
            // Antes o aviso só informava — o motorista tinha que achar a
            // tela de manutenção sozinho.
            if (onRegistrar != null) {
                OutlinedButton(
                    onClick = onRegistrar,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, iconColor)
                ) {
                    Text("Registrar manutenção", color = iconColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ===============================
// CARD DE ALERTA — CNH VENCENDO/VENCIDA (mesmo alerta de 30 dias que já
// existe pro admin, mostrado aqui pro próprio motorista).
// ===============================
@Composable
fun CnhAlertCard(status: api.CnhStatusResponse) {
    val isDarkMode = isDark()
    val backgroundColor = if (status.vencido) {
        if (isDarkMode) Color(0xFF3D1A1A) else Color(0xFFFFEBEE)
    } else {
        if (isDarkMode) Color(0xFF3D2E1A) else Color(0xFFFFF3E0)
    }
    val iconColor = if (status.vencido) {
        if (isDarkMode) Color(0xFFFF7070) else Color(0xFFD32F2F)
    } else {
        if (isDarkMode) Color(0xFFFFB74D) else Color(0xFFFF6F00)
    }
    val titulo = if (status.vencido) "CNH vencida" else "CNH vencendo em ${status.dias} dia(s)"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Badge, null, tint = iconColor, modifier = Modifier.size(24.dp))
            Column {
                Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = iconColor)
                status.data_vencimento?.let {
                    Text("Validade: ${formatarDataBR(it)}", fontSize = 13.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

// ===============================
// CARD DE VIAGEM EM ANDAMENTO — destino em destaque, placa e há quantos
// dias a viagem está aberta. Antes mostrava "ID: 1234", número que não diz
// nada ao motorista, e tudo em letra de 11 sp.
// ===============================
@Composable
fun ViagemStatusCard(
    viagemInfo: String,
    kmInicio: String = "",
    dataInicio: String = "",
    placa: String = "",
    /** Chip de estado do envio no canto de cima (DashboardEnvios.ChipEstadoEnvio). */
    selo: @Composable () -> Unit = {},
    /** Linha de envios embaixo (DashboardEnvios.RodapeEnvios). */
    rodape: @Composable () -> Unit = {}
) {
    val isDarkMode = isDark()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Sem etiqueta "Offline" aqui: o aviso de sem internet fica no topo
            // do painel e os lançamentos guardados aparecem no rodapé.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VIAGEM EM ANDAMENTO", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f)
                )
                selo()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(ui.Lucide.Truck, null, tint = AppColors.Secondary, modifier = Modifier.size(28.dp))
                Text(viagemInfo, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary, lineHeight = 24.sp)
            }
            val detalhes = listOfNotNull(
                placa.takeIf { it.isNotBlank() },
                textoDiasDeViagem(dataInicio),
                kmInicio.takeIf { it.isNotBlank() }?.let { km ->
                    "KM inicial ${km.toDoubleOrNull()?.let { formatarKmExibicao(it) } ?: km}"
                }
            )
            if (detalhes.isNotEmpty()) {
                Text(detalhes.joinToString("  ·  "), fontSize = 14.sp, color = AppColors.TextSecondary)
            }
            rodape()
        }
    }
}

/** "Iniciada hoje" / "2º dia de viagem" a partir da data de início (yyyy-MM-dd). */
private fun textoDiasDeViagem(dataInicio: String): String? {
    val inicio = try {
        kotlinx.datetime.LocalDate.parse(dataInicio.trim().take(10))
    } catch (_: Exception) {
        return dataInicio.takeIf { it.isNotBlank() }?.let { "Desde ${formatarDataBR(it)}" }
    }
    val hoje = kotlinx.datetime.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
    val dias = inicio.daysUntil(hoje)
    return if (dias <= 0) "Iniciada hoje" else "${dias + 1}º dia de viagem"
}

// ===============================
// CARD "CHECKLIST DE SAÍDA PENDENTE" — discreto, fica até o checklist da
// viagem em andamento ser feito (o motorista escolheu "Depois").
// ===============================
@Composable
fun ChecklistPendenteCard(onFazer: () -> Unit) {
    val ciano = Color(0xFF0097A7)
    Card(
        onClick = onFazer,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, ciano.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(40.dp).background(ciano.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(ui.Lucide.ClipboardCheck, null, tint = ciano, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Checklist de saída pendente", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                Text("35 itens · uns 2 minutos", fontSize = 13.sp, color = AppColors.TextSecondary)
            }
            Text("Fazer", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
        }
    }
}

// ===============================
// BOTÃO "INICIAR VIAGEM" — grande, logo abaixo do card, só quando não há
// viagem aberta. Com viagem, Despesas e Finalizar ficam só na barra de baixo:
// botões grandes com nomes diferentes dos de baixo ("Abastecer" x
// "Despesas") confundiam o motorista.
// ===============================
@Composable
fun BotaoIniciarViagem(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
    ) {
        Icon(ui.Lucide.Truck, null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text("Iniciar viagem", fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ===============================
// CARD DE VERIFICANDO VIAGEM
// ===============================
@Composable
fun VerificandoViagemCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Warning.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFF57C00))
            Text("Verificando viagens...", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
        }
    }
}

// ===============================
// CARD DE NENHUMA VIAGEM (com ilustração)
// ===============================
@Composable
fun SemViagemCard(selo: @Composable () -> Unit = {}, rodape: @Composable () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) { selo() }
            ui.TruckIdleIllustration()
            Spacer(Modifier.height(12.dp))
            Text(
                "Nenhuma viagem em andamento",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Quando sair, toque em Iniciar viagem aqui embaixo.",
                fontSize = 14.sp,
                color = AppColors.TextSecondary
            )
            rodape()
        }
    }
}

// ===============================
// ★ FIX 3.2.4: CARD DE MENSAGEM — SEM EMOJIS
// Usa TipoMensagem enum em vez de checar prefixo "✓" ou "⚠"
// ===============================
@Composable
fun MensagemCard(mensagem: String, tipo: TipoMensagem = TipoMensagem.INFO) {
    val (cor, icone) = when (tipo) {
        TipoMensagem.SUCESSO -> AppColors.Secondary to Icons.Default.CheckCircle
        TipoMensagem.ERRO -> AppColors.Error to Icons.Default.Error
        TipoMensagem.AVISO -> Color(0xFFFF6F00) to Icons.Default.Warning
        TipoMensagem.INFO -> Color(0xFF1976D2) to Icons.Default.Info
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cor.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icone, null, tint = cor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(mensagem, fontSize = 13.sp, color = cor)
        }
    }
}

// ===============================
// BOTÃO DE MENU DO DASHBOARD
// ===============================
@Composable
fun MenuButton(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(24.dp), tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Icon(ui.Lucide.ChevronRight, null, tint = AppColors.TextSecondary)
        }
    }
}

/**
 * Converte data do formato API (2026-03-21) para formato brasileiro (21/03/2026).
 * Se já estiver no formato BR ou não reconhecer, retorna como está.
 */
private fun formatarDataBR(data: String): String {
    // Formato API: yyyy-MM-dd ou yyyy-MM-dd HH:mm:ss
    val partes = data.trim().split(" ").first().split("-")
    return if (partes.size == 3 && partes[0].length == 4) {
        "${partes[2]}/${partes[1]}/${partes[0]}"
    } else {
        data // Já está em outro formato, retorna como está
    }
}
