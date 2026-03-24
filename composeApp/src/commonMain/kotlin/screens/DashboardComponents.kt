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
import ui.AppColors
import ui.isDark

// ===============================
// CARD DE STATUS DE SINCRONIZAÇÃO
// ===============================
@Composable
fun SyncStatusCard(status: SyncStatus) {
    val isDarkMode = isDark()
    val (backgroundColor, iconColor, icon, title) = when (status.state) {
        SyncState.SYNCING -> Tuple4(
            if (isDarkMode) Color(0xFF1A2940) else Color(0xFFE3F2FD),
            if (isDarkMode) Color(0xFF5B8DEF) else Color(0xFF1976D2),
            Icons.Default.Sync, "Sincronizando..."
        )
        SyncState.SUCCESS -> Tuple4(
            if (isDarkMode) Color(0xFF1A3320) else Color(0xFFE8F5E9),
            if (isDarkMode) Color(0xFF66D944) else Color(0xFF4CAF50),
            Icons.Default.CheckCircle, "Sincronização concluída!"
        )
        SyncState.ERROR -> Tuple4(
            if (isDarkMode) Color(0xFF3D1A1A) else Color(0xFFFFEBEE),
            if (isDarkMode) Color(0xFFFF7070) else Color(0xFFD32F2F),
            Icons.Default.Error, "Erro na sincronização"
        )
        SyncState.NO_INTERNET -> Tuple4(
            if (isDarkMode) Color(0xFF3D2E1A) else Color(0xFFFFF3E0),
            if (isDarkMode) Color(0xFFFFB74D) else Color(0xFFFF6F00),
            Icons.Default.CloudOff, "Sem conexão com a internet"
        )
        else -> return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = iconColor)
                    if (status.message.isNotEmpty()) {
                        Text(status.message, fontSize = 12.sp, color = AppColors.TextSecondary)
                    }
                }
            }

            if (status.state == SyncState.SYNCING && status.total > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { if (status.total > 0) status.progress.toFloat() / status.total.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = iconColor,
                        trackColor = iconColor.copy(alpha = 0.2f)
                    )
                    Text("${status.progress} de ${status.total} itens", fontSize = 11.sp, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

// ===============================
// CARD DE VIAGEM EM ANDAMENTO
// ===============================
@Composable
fun ViagemStatusCard(
    viagemInfo: String,
    viagemId: Long,
    modoOffline: Boolean,
    temInternet: Boolean,
    kmInicio: String = "",
    dataInicio: String = ""
) {
    val isDarkMode = isDark()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDarkMode) AppColors.SurfaceVariant else AppColors.Secondary.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalShipping, null,
                        tint = AppColors.Secondary, modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Viagem em andamento", fontSize = 12.sp, color = AppColors.TextSecondary)

                            if (modoOffline && !temInternet) {
                                ConnectivityBadge("Offline", Color(0xFFFF9800).copy(alpha = 0.2f), Color(0xFFFF6F00), Icons.Default.CloudOff)
                            } else if (modoOffline && temInternet) {
                                ConnectivityBadge("Reconectado", Color(0xFF4CAF50).copy(alpha = 0.2f), Color(0xFF2E7D32), Icons.Default.Wifi)
                            }
                        }
                        Text(viagemInfo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Secondary)
                    }
                }
                Text("ID: $viagemId", fontSize = 11.sp, color = AppColors.TextSecondary)
            }

            // Informações extras da viagem
            if (kmInicio.isNotBlank() || dataInicio.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (dataInicio.isNotBlank()) {
                        val dataFormatada = formatarDataBR(dataInicio)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp), tint = AppColors.TextSecondary)
                            Text(dataFormatada, fontSize = 11.sp, color = AppColors.TextSecondary)
                        }
                    }
                    if (kmInicio.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp), tint = AppColors.TextSecondary)
                            Text("KM saída: $kmInicio", fontSize = 11.sp, color = AppColors.TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ===============================
// BADGE DE CONECTIVIDADE
// ===============================
@Composable
private fun ConnectivityBadge(text: String, backgroundColor: Color, textColor: Color, icon: ImageVector) {
    Surface(color = backgroundColor, shape = RoundedCornerShape(4.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(10.dp), tint = textColor)
            Text(text, fontSize = 9.sp, color = textColor, fontWeight = FontWeight.Bold)
        }
    }
}

// ===============================
// CARD DE VERIFICANDO VIAGEM
// ===============================
@Composable
fun VerificandoViagemCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFF57C00))
            Text("Verificando viagens...", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ===============================
// CARD DE NENHUMA VIAGEM (com ilustração)
// ===============================
@Composable
fun SemViagemCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ui.TruckIdleIllustration()
            Spacer(Modifier.height(12.dp))
            Text(
                "Nenhuma viagem em andamento",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Inicie uma viagem na barra inferior",
                fontSize = 12.sp,
                color = Color(0xFFBDBDBD)
            )
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
            Icon(Icons.Default.ChevronRight, null, tint = AppColors.TextSecondary)
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
