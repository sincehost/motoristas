package screens

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
import database.AppRepository
import kotlinx.coroutines.launch
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
    onMessage: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var mostrarMenuDespesas by remember { mutableStateOf(false) }

    // ★ Estado dos popups de aviso
    var mostrarAvisoIniciarViagem by remember { mutableStateOf(false) }
    var mostrarAvisoSemViagem by remember { mutableStateOf(false) }

    // ★ Popup: Inicie uma viagem primeiro
    if (mostrarAvisoIniciarViagem) {
        AlertDialog(
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
        AlertDialog(
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
            confirmButton = {
                Button(
                    onClick = {
                        mostrarAvisoSemViagem = false
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
                    onClick = { mostrarAvisoSemViagem = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Entendido")
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
                        mostrarMenuDespesas = !mostrarMenuDespesas
                    } else {
                        // ★ Popup em vez de mensagem pequena
                        mostrarAvisoIniciarViagem = true
                    }
                }
            },
            icon = {
                Box {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("Despesas", fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                    }

                    DespesasDropdownMenu(
                        expanded = mostrarMenuDespesas,
                        onDismiss = { mostrarMenuDespesas = false },
                        onNavigate = { screen ->
                            mostrarMenuDespesas = false
                            onNavigate(screen)
                        }
                    )
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
// MENU DROPDOWN DE DESPESAS
// ===============================
@Composable
private fun DespesasDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Diesel", fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Default.LocalGasStation, null, tint = AppColors.Primary) },
            onClick = { onNavigate(Screen.ADICIONAR_COMBUSTIVEL) }
        )
        DropdownMenuItem(
            text = { Text("ARLA 32", fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Default.WaterDrop, null, tint = Color(0xFF06B6D4)) },
            onClick = { onNavigate(Screen.ADICIONAR_ARLA) }
        )
        DropdownMenuItem(
            text = { Text("Descarga", fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Default.Inventory, null, tint = Color(0xFF8B5CF6)) },
            onClick = { onNavigate(Screen.ADICIONAR_DESCARGA) }
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Outras Despesas", fontWeight = FontWeight.Medium) },
            leadingIcon = { Icon(Icons.Default.MoreHoriz, null, tint = Color(0xFFFF6F00)) },
            onClick = { onNavigate(Screen.OUTRAS_DESPESAS) }
        )
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
