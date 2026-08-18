package screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import database.AppRepository
import kotlinx.coroutines.launch
import ui.AppAlertDialog
import ui.AppColors
import ui.isDark

// ===============================
// BOTTOM NAVIGATION BAR
// ===============================
@Composable
fun BottomNavigationBar(
    repository: AppRepository,
    viagemAtualId: Long?,
    telaAtual: Screen? = null,
    onNavigate: (Screen) -> Unit,
    onMessage: (String) -> Unit,
    onAbrirMenuDespesas: () -> Unit
) {
    val scope = rememberCoroutineScope()

    // ★ Estado dos popups de aviso
    var mostrarAvisoIniciarViagem by remember { mutableStateOf(false) }
    var mostrarAvisoSemViagem by remember { mutableStateOf(false) }

    // ★ Popup: Inicie uma viagem primeiro
    if (mostrarAvisoIniciarViagem) {
        AppAlertDialog(
            onDismissRequest = { mostrarAvisoIniciarViagem = false },
            containerColor = AppColors.Surface,
            icon = {
                Icon(
                    Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    "Viagem necessária",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "Para registrar despesas, você precisa iniciar uma viagem primeiro.\n\nClique em \"Iniciar\" na barra inferior para começar.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        mostrarAvisoIniciarViagem = false
                        onNavigate(Screen.INICIAR_VIAGEM)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Iniciar Viagem", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { mostrarAvisoIniciarViagem = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Entendido")
                }
            }
        )
    }

    // ★ Popup: Nenhuma viagem em andamento
    if (mostrarAvisoSemViagem) {
        AppAlertDialog(
            onDismissRequest = { mostrarAvisoSemViagem = false },
            containerColor = AppColors.Surface,
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFFF6F00),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    "Nenhuma viagem em andamento",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "Não há viagem ativa para finalizar.\n\nInicie uma nova viagem antes de tentar finalizá-la.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            // Botões empilhados (um embaixo do outro) em vez de lado a lado —
            // fica mais organizado com os dois textos de tamanhos diferentes.
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            mostrarAvisoSemViagem = false
                            onNavigate(Screen.INICIAR_VIAGEM)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Iniciar Viagem", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { mostrarAvisoSemViagem = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Entendido")
                    }
                }
            }
        )
    }

    NavigationBar(
        containerColor = if (isDark()) Color(0xFF1A1A2E) else AppColors.Primary,
        tonalElevation = 8.dp,
        modifier = Modifier.height(88.dp)
    ) {
        // 1. Iniciar Viagem
        NavigationBarItem(
            selected = telaAtual == Screen.INICIAR_VIAGEM,
            onClick = {
                scope.launch {
                    onMessage("")
                    onNavigate(Screen.INICIAR_VIAGEM)
                }
            },
            icon = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Iniciar", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                }
            },
            colors = bottomNavColors()
        )

        // 2. Despesas
        NavigationBarItem(
            selected = telaAtual == Screen.ADICIONAR_COMBUSTIVEL ||
                    telaAtual == Screen.ADICIONAR_ARLA ||
                    telaAtual == Screen.ADICIONAR_DESCARGA ||
                    telaAtual == Screen.OUTRAS_DESPESAS,
            onClick = {
                scope.launch {
                    onMessage("")
                    if (viagemAtualId != null) {
                        onAbrirMenuDespesas()
                    } else {
                        // ★ Popup em vez de mensagem pequena
                        mostrarAvisoIniciarViagem = true
                    }
                }
            },
            icon = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Despesas", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                }
            },
            colors = bottomNavColors()
        )

        // 3. Manutenção
        NavigationBarItem(
            selected = telaAtual == Screen.MANUTENCAO,
            onClick = {
                scope.launch {
                    onMessage("")
                    onNavigate(Screen.MANUTENCAO)
                }
            },
            icon = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Manut.", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                }
            },
            colors = bottomNavColors()
        )

        // 4. Finalizar Viagem
        NavigationBarItem(
            selected = telaAtual == Screen.FINALIZAR_VIAGEM,
            onClick = {
                scope.launch {
                    onMessage("")
                    if (viagemAtualId != null) {
                        onNavigate(Screen.FINALIZAR_VIAGEM)
                    } else {
                        // ★ Popup em vez de mensagem pequena
                        mostrarAvisoSemViagem = true
                    }
                }
            },
            icon = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Finalizar", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                }
            },
            colors = bottomNavColors()
        )
    }
}

// ===============================
// MENU DE DESPESAS (overlay ancorado embaixo, SEM Popup — ver AppAlertDialog)
// ===============================
@Composable
fun DespesasMenuOverlay(
    onDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1000f)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 96.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                DespesaMenuItem("Abastecimento", Icons.Default.LocalGasStation, AppColors.Primary) {
                    onDismiss(); onNavigate(Screen.ADICIONAR_COMBUSTIVEL)
                }
                DespesaMenuItem("ARLA 32", Icons.Default.WaterDrop, Color(0xFF06B6D4)) {
                    onDismiss(); onNavigate(Screen.ADICIONAR_ARLA)
                }
                DespesaMenuItem("Descarga", Icons.Default.Inventory, Color(0xFF8B5CF6)) {
                    onDismiss(); onNavigate(Screen.ADICIONAR_DESCARGA)
                }
                HorizontalDivider()
                DespesaMenuItem("Outras Despesas", Icons.Default.MoreHoriz, Color(0xFFFF6F00)) {
                    onDismiss(); onNavigate(Screen.OUTRAS_DESPESAS)
                }
            }
        }
    }
}

@Composable
private fun DespesaMenuItem(texto: String, icone: androidx.compose.ui.graphics.vector.ImageVector, cor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icone, null, tint = cor)
        Spacer(Modifier.width(16.dp))
        Text(texto, fontWeight = FontWeight.Medium)
    }
}

// ===============================
// CORES PADRÃO DA BOTTOM NAV
// ===============================
@Composable
private fun bottomNavColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.White,
    selectedTextColor = Color.White,
    unselectedIconColor = Color.White.copy(alpha = 0.7f),
    unselectedTextColor = Color.White.copy(alpha = 0.7f),
    indicatorColor = Color.White.copy(alpha = 0.2f)
)
