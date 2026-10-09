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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
            // Botões empilhados (um embaixo do outro) em vez de lado a lado —
            // mesmo padrão do aviso "Nenhuma viagem em andamento".
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            mostrarAvisoIniciarViagem = false
                            onNavigate(Screen.INICIAR_VIAGEM)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Iniciar Viagem", fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { mostrarAvisoIniciarViagem = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Entendido")
                    }
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

    // Barra flutuante construída na mão (Row + itens clicáveis), não com
    // NavigationBar/NavigationBarItem do Material — esse componente tem um
    // tamanho interno próprio pro slot do ícone que cortava o texto, não
    // importa a altura travada no container. Aqui cada item controla o
    // próprio tamanho, sem surpresa.
    val bottomBarHeight = 76.dp
    // Raio fixo (não mais metade da altura = pílula total) — um pouco menos
    // arredondado, a pedido.
    val bottomBarShape = RoundedCornerShape(28.dp)
    val barColor = if (isDark()) Color(0xFF1A1A2E) else AppColors.Primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // navigationBarsPadding() primeiro: em celular com os 3 botões na
            // tela (não gesto), isso evita a barra ficar colada/por baixo
            // deles. O +10dp depois é só a folga visual de "flutuando".
            .navigationBarsPadding()
            .padding(bottom = 10.dp)
            .height(bottomBarHeight)
            .shadow(elevation = 16.dp, shape = bottomBarShape, clip = false)
            .clip(bottomBarShape)
            .background(barColor),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Iniciar Viagem
        BottomBarItem(
            icon = Icons.Default.LocalShipping,
            label = "Iniciar",
            selected = telaAtual == Screen.INICIAR_VIAGEM,
            onClick = {
                scope.launch {
                    onMessage("")
                    onNavigate(Screen.INICIAR_VIAGEM)
                }
            }
        )

        // 2. Despesas
        BottomBarItem(
            icon = Icons.Default.Receipt,
            label = "Despesas",
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
            }
        )

        // 3. Manutenção
        BottomBarItem(
            icon = Icons.Default.Build,
            label = "Manut.",
            selected = telaAtual == Screen.MANUTENCAO,
            onClick = {
                scope.launch {
                    onMessage("")
                    onNavigate(Screen.MANUTENCAO)
                }
            }
        )

        // 4. Finalizar Viagem
        BottomBarItem(
            icon = Icons.Default.Flag,
            label = "Finalizar",
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
            }
        )
    }
}

@Composable
private fun RowScope.BottomBarItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, maxLines = 1, fontWeight = FontWeight.Medium, color = contentColor)
    }
}

// ===============================
// MENU DE DESPESAS (overlay ancorado embaixo, SEM Popup — ver AppAlertDialog)
// ===============================
@Composable
fun DespesasMenuOverlay(
    onDismiss: () -> Unit,
    onNavigate: (Screen) -> Unit,
    rotaContinua: Boolean = false
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
                if (rotaContinua) {
                    DespesaMenuItem("Adicionar Frete", Icons.Default.LocalShipping, Color(0xFFF59E0B)) {
                        onDismiss(); onNavigate(Screen.ADICIONAR_FRETE)
                    }
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

