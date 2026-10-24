package screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import database.LancamentoPendente
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import ui.AppColors

// =====================================================================
// ESTADO DE ENVIO NO PAINEL — chip no topo, botão de sincronizar com anel,
// aviso de sem internet / internet de volta, ícones dos lançamentos no
// card da viagem, aviso de concluído e a tela "Envios".
//
// Antes: etiqueta minúscula "Offline" no card, cards coloridos aparecendo
// e sumindo no topo ("Sincronizando…", "Abastecimentos: 2/3") e um card
// laranja "Sem conexão" que parecia erro. Sem internet não é erro: o app
// guarda tudo e envia sozinho depois — a tela agora diz isso com calma.
// =====================================================================

private val VerdeOk = Color(0xFF059669)
private val VerdeOkClaro = Color(0xFFD1FAE5)
private val Ambar = Color(0xFFD97706)
private val AmbarClaro = Color(0xFFFEF3C7)
private val Ardosia = Color(0xFF1F2937)
private val LaranjaContador = Color(0xFFF59E0B)

/** Ícone de cada tipo de lançamento (mesmos da tela de Despesas). */
fun iconeDoLancamento(tipo: String): ImageVector = when (tipo) {
    "Abastecimento" -> ui.Lucide.Fuel
    "ARLA 32" -> ui.Lucide.Droplet
    "Descarga" -> ui.Lucide.Package
    "Frete", "Viagem" -> ui.Lucide.Truck
    "Outra despesa" -> ui.Lucide.Receipt
    "Manutenção" -> ui.Lucide.Wrench
    "Finalização de viagem" -> ui.Lucide.Flag
    else -> ui.Lucide.ClipboardCheck
}

/** epoch ms → "14:32" no fuso do celular. */
fun horaDoDia(epochMs: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}

// ===============================
// CHIP DE ESTADO (canto de cima, à direita, do card da viagem / sem viagem)
// ===============================
@Composable
fun ChipEstadoEnvio(temInternet: Boolean, sincronizando: Boolean, pendentes: Int) {
    val (texto, corPonto) = when {
        !temInternet -> "Sem internet" to Color(0xFF6B7280)
        sincronizando -> "Enviando…" to Color(0xFF2563EB)
        pendentes > 0 -> (if (pendentes == 1) "1 para enviar" else "$pendentes para enviar") to Ambar
        else -> "Tudo enviado" to VerdeOk
    }
    Row(
        modifier = Modifier.background(corPonto.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(11.dp).background(corPonto.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(7.dp).background(corPonto, CircleShape)) }
        Text(texto, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, maxLines = 1)
    }
}

// ===============================
// BOTÃO DE SINCRONIZAR — anel com o andamento real + contador laranja
// ===============================
@Composable
fun BotaoSincronizar(
    pendentes: Int,
    sincronizando: Boolean,
    progresso: Float,
    onClick: () -> Unit
) {
    val anel by animateFloatAsState(if (sincronizando) progresso.coerceIn(0f, 1f) else 0f, tween(450), label = "anel")
    val giro = rememberInfiniteTransition(label = "giro")
    val angulo by giro.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "angulo")
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                enabled = !sincronizando,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, color = Color.White),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(36.dp)) {
            val traco = 3.dp.toPx()
            val tam = Size(size.width - traco, size.height - traco)
            val ini = Offset(traco / 2, traco / 2)
            drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, ini, tam, style = Stroke(traco))
            if (anel > 0f) drawArc(Color.White, -90f, 360f * anel, false, ini, tam, style = Stroke(traco, cap = StrokeCap.Round))
        }
        Icon(
            ui.Lucide.RefreshCw, contentDescription = "Sincronizar", tint = Color.White,
            modifier = Modifier.size(18.dp).rotate(if (sincronizando) angulo else 0f)
        )
        AnimatedVisibility(
            visible = pendentes > 0 && !sincronizando,
            enter = scaleIn(), exit = scaleOut(),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            // Altura fixa: sem ela a altura de linha do Text (24 sp do tema)
            // esticava a bolinha e ela ficava oval. 1 dígito = círculo.
            Box(
                modifier = Modifier
                    .padding(top = 1.dp, end = 0.dp)
                    .height(18.dp)
                    .widthIn(min = 18.dp)
                    .background(LaranjaContador, RoundedCornerShape(50))
                    .border(2.dp, Color.White, RoundedCornerShape(50))
                    .padding(horizontal = if (pendentes > 9) 4.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (pendentes > 99) "99+" else pendentes.toString(),
                    fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1
                )
            }
        }
    }
}

// ===============================
// AVISO DE CONEXÃO (sem internet / voltou)
// ===============================
@Composable
fun AvisoConexao(semInternet: Boolean, reconectado: Boolean) {
    AnimatedVisibility(
        visible = semInternet || reconectado,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val fundo by animateColorAsState(if (semInternet) Ardosia else VerdeOk, label = "fundoAviso")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(fundo, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(32.dp).background(Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (semInternet) Icons.Default.CloudOff else Icons.Default.Wifi, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    if (semInternet) "Você está sem internet" else "Conectado de novo",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White
                )
                Text(
                    if (semInternet) "Pode continuar lançando. Tudo fica salvo no celular." else "Enviando o que ficou guardado…",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), lineHeight = 17.sp
                )
            }
        }
    }
}

// ===============================
// RODAPÉ DE ENVIOS (dentro do card da viagem / sem viagem)
// ===============================
@Composable
fun RodapeEnvios(
    pendentes: List<LancamentoPendente>,
    sincronizando: Boolean,
    enviadosNestaRodada: Int,
    ultimoEnvio: Long,
    falhouAgora: Boolean,
    onVer: () -> Unit
) {
    // Nada guardado e nenhum envio ainda: o chip "Tudo enviado" já diz tudo.
    if (pendentes.isEmpty() && ultimoEnvio <= 0) return
    Column {
        HorizontalDivider(color = AppColors.Divider, modifier = Modifier.padding(top = 10.dp, bottom = 10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    enabled = pendentes.isNotEmpty(),
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onVer
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (pendentes.isEmpty()) {
                Icon(Icons.Default.CloudDone, null, tint = VerdeOk, modifier = Modifier.size(18.dp))
                // "Tudo enviado" já está no chip do canto do card.
                Text(
                    "Último envio às ${horaDoDia(ultimoEnvio)}",
                    fontSize = 13.sp, color = AppColors.TextSecondary
                )
            } else {
                // Até 5 ícones; o resto vira "+N".
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    pendentes.take(5).forEachIndexed { i, p ->
                        IconeLancamento(p, enviado = sincronizando && i < enviadosNestaRodada)
                    }
                    if (pendentes.size > 5) {
                        Box(Modifier.size(26.dp).background(AppColors.SurfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Text("+${pendentes.size - 5}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
                        }
                    }
                }
                Text(
                    when {
                        sincronizando -> "Enviando ${(enviadosNestaRodada + 1).coerceAtMost(pendentes.size)} de ${pendentes.size}…"
                        falhouAgora -> "Não deu para enviar agora. O app tenta de novo sozinho."
                        pendentes.size == 1 -> "1 guardado no celular"
                        else -> "${pendentes.size} guardados no celular"
                    },
                    fontSize = 13.sp, color = AppColors.TextSecondary, modifier = Modifier.weight(1f),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (!sincronizando) {
                    Text("Ver", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                }
            }
        }
    }
}

/** Ícone do lançamento: âmbar enquanto espera, ✓ verde com um pulinho quando sobe. */
@Composable
private fun IconeLancamento(p: LancamentoPendente, enviado: Boolean) {
    val pulo = remember { Animatable(1f) }
    LaunchedEffect(enviado) {
        if (enviado) {
            pulo.animateTo(1.15f, tween(150))
            pulo.animateTo(1f, tween(150))
        }
    }
    val escala = pulo.value
    val (fundo, cor) = when {
        enviado -> VerdeOkClaro to VerdeOk
        p.recusado -> Color(0xFFFDEBDD) to Color(0xFFC2410C)
        else -> AmbarClaro to Ambar
    }
    Box(
        Modifier.size(26.dp).scale(escala).background(fundo, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (enviado) Icons.Default.Check else iconeDoLancamento(p.tipo), contentDescription = p.tipo, tint = cor, modifier = Modifier.size(14.dp))
    }
}

// ===============================
// AVISO DE CONCLUÍDO (sobe por 3 s e some)
// ===============================
@Composable
fun AvisoEnviados(quantidade: Int?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = quantidade != null,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
        modifier = modifier
    ) {
        val n = quantidade ?: 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ardosia, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(28.dp).background(VerdeOk, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
            Column {
                Text(if (n == 1) "1 lançamento enviado" else "$n lançamentos enviados", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Tudo certo com o escritório", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

// ===============================
// TELA "ENVIOS" (folha que sobe de baixo, sem Popup — ver AppDropdown)
// ===============================
@Composable
fun EnviosOverlay(
    pendentes: List<LancamentoPendente>,
    temInternet: Boolean,
    semInternetDesde: Long?,
    ultimoEnvio: Long,
    sincronizando: Boolean,
    onTentarAgora: () -> Unit,
    onCorrigir: () -> Unit,
    onFechar: () -> Unit
) {
    PlatformBackHandler(enabled = true, onBack = onFechar)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFechar),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .background(AppColors.Surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).background(AppColors.Divider, RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(12.dp))
            Text("Envios", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Text(
                listOfNotNull(
                    if (!temInternet) (semInternetDesde?.let { "Sem internet desde ${horaDoDia(it)}" } ?: "Sem internet") else null,
                    if (ultimoEnvio > 0) "último envio às ${horaDoDia(ultimoEnvio)}" else null
                ).joinToString(" · ").ifEmpty { "Lançamentos guardados no celular" },
                fontSize = 13.sp, color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                pendentes.forEach { p ->
                    HorizontalDivider(color = AppColors.Divider)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.size(36.dp).background(AppColors.SurfaceVariant, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Icon(iconeDoLancamento(p.tipo), null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(p.tipo, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                            val detalhe = listOfNotNull(
                                p.descricao.takeIf { it.isNotBlank() },
                                p.valor.takeIf { it.isNotBlank() }?.let { "R$ $it" },
                                dataCurta(p.data)
                            ).joinToString(" · ")
                            if (detalhe.isNotEmpty()) Text(detalhe, fontSize = 12.sp, color = AppColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val (txt, fundo, cor) = if (p.recusado) Triple("Corrigir", Color(0xFFFDEBDD), Color(0xFFC2410C))
                            else Triple("Guardado", AmbarClaro, Ambar)
                        Text(
                            txt, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cor,
                            modifier = Modifier.background(fundo, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (pendentes.any { it.recusado }) {
                OutlinedButton(
                    onClick = onCorrigir,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Corrigir os recusados", fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onTentarAgora,
                enabled = !sincronizando,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) { Text(if (sincronizando) "Enviando…" else "Tentar enviar agora", fontWeight = FontWeight.Bold) }
        }
    }
}

/** "2026-10-20" (ou com hora) → "20/10". Outro formato: null. */
private fun dataCurta(data: String): String? {
    val p = data.trim().split(" ").first().split("-")
    return if (p.size == 3 && p[0].length == 4) "${p[2]}/${p[1]}" else null
}
