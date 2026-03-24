package screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AppColors

/**
 * Tela de configuracao para Xiaomi/MIUI.
 *
 * Aparece APENAS UMA VEZ apos o primeiro login em dispositivos Xiaomi/POCO/Redmi.
 * Guia o motorista passo-a-passo para:
 * 1. Ativar AutoStart (permite o app manter servicos em background)
 * 2. Bloquear o app nas recentes (impede que MIUI mate o app)
 *
 * Sem essas configuracoes, o MIUI pode encerrar o app em background,
 * causando perda de sincronizacao e dados.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiuiSetupScreen(
    onConcluir: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Estado dos passos
    var passo1Feito by remember { mutableStateOf(false) }
    var passo2Feito by remember { mutableStateOf(false) }

    val deviceName = remember {
        val brand = Build.BRAND?.replaceFirstChar { it.uppercase() } ?: "Xiaomi"
        val model = Build.MODEL ?: ""
        "$brand $model".trim()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.background(
                    Brush.horizontalGradient(listOf(AppColors.Primary, AppColors.Secondary))
                ),
                actions = {
                    TextButton(onClick = onConcluir) {
                        Text(
                            "PULAR",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // ========================================
            // HEADER
            // ========================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(AppColors.Primary, AppColors.Primary.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Icone do escudo
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        "Configuracao Importante",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Seu aparelho $deviceName precisa de ajustes para o app funcionar corretamente em segundo plano.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    // Badge de alerta
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                "Sem estas configuracoes, o app pode ser encerrado pelo sistema",
                                fontSize = 12.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ========================================
            // PASSO 1: AUTOSTART
            // ========================================
            StepCard(
                stepNumber = 1,
                title = "Ativar Inicio Automatico",
                description = "Permite que o app mantenha seus servicos funcionando em segundo plano para sincronizar dados corretamente.",
                icon = Icons.Default.RocketLaunch,
                iconColor = Color(0xFF2196F3),
                isDone = passo1Feito,
                buttonText = if (passo1Feito) "Feito" else "Abrir AutoStart",
                onClick = {
                    if (!passo1Feito) {
                        abrirAutoStart(context)
                        passo1Feito = true
                    }
                },
                instructions = listOf(
                    "Toque no botao abaixo",
                    "Encontre \"Trakvia\" ou \"Portal do Motorista\" na lista",
                    "Ative a chave ao lado do nome do app",
                    "Volte para este app"
                )
            )

            Spacer(Modifier.height(12.dp))

            // ========================================
            // PASSO 2: CADEADO NAS RECENTES
            // ========================================
            StepCard(
                stepNumber = 2,
                title = "Bloquear App nas Recentes",
                description = "Impede que o sistema encerre o app ao limpar apps recentes.",
                icon = Icons.Default.Lock,
                iconColor = Color(0xFFFF9800),
                isDone = passo2Feito,
                buttonText = if (passo2Feito) "Feito" else "Entendi, ja vou fazer",
                onClick = { passo2Feito = true },
                instructions = listOf(
                    "Abra a tela de apps recentes (botao quadrado ou gesto de baixo para cima)",
                    "Encontre o card deste app",
                    "Segure o card e arraste para baixo (ou toque no icone de cadeado)",
                    "O cadeado aparecera no canto do card"
                ),
                isManual = true
            )

            Spacer(Modifier.height(24.dp))

            // ========================================
            // BOTAO CONCLUIR
            // ========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Button(
                    onClick = {
                        // Salvar que o guia foi concluido
                        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("miui_setup_done", true)
                            .putBoolean("show_miui_guide", false)
                            .apply()
                        onConcluir()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (passo1Feito && passo2Feito)
                            AppColors.Secondary else AppColors.Primary
                    )
                ) {
                    Icon(
                        if (passo1Feito && passo2Feito)
                            Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                        null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (passo1Feito && passo2Feito)
                            "TUDO PRONTO! CONTINUAR" else "CONTINUAR MESMO ASSIM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (!(passo1Feito && passo2Feito)) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Voce pode configurar depois em Configuracoes do celular",
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ========================================
// CARD DE PASSO
// ========================================
@Composable
private fun StepCard(
    stepNumber: Int,
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: Color,
    isDone: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    instructions: List<String>,
    isManual: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDone) 0.dp else 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDone) Color(0xFFF0FFF4) else Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header do passo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Numero/Check do passo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDone) Color(0xFF4CAF50) else iconColor.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            "$stepNumber",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = iconColor
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDone) Color(0xFF2E7D32) else AppColors.TextPrimary
                    )
                    Text(
                        description,
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 16.sp
                    )
                }

                Icon(
                    icon,
                    null,
                    tint = if (isDone) Color(0xFF4CAF50) else iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Instrucoes (so mostra se nao esta feito)
            if (!isDone) {
                Spacer(Modifier.height(16.dp))

                // Box de instrucoes
                Surface(
                    color = Color(0xFFF5F7FA),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                if (isManual) Icons.Default.TouchApp else Icons.Default.ListAlt,
                                null,
                                tint = AppColors.TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                if (isManual) "Como fazer:" else "Passo a passo:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextSecondary
                            )
                        }

                        instructions.forEachIndexed { index, instruction ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Bolinha numerada
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(iconColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = iconColor
                                    )
                                }
                                Text(
                                    instruction,
                                    fontSize = 13.sp,
                                    color = AppColors.TextPrimary,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Botao de acao
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iconColor
                    )
                ) {
                    Icon(
                        if (isManual) Icons.Default.Check else Icons.Default.OpenInNew,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buttonText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "Configurado com sucesso",
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}


// ========================================
// FUNCOES AUXILIARES
// ========================================

/**
 * Tenta abrir a tela de AutoStart do MIUI.
 * Testa varios intents em ordem de prioridade (MIUI 14+, 12-13, generico).
 */
private fun abrirAutoStart(context: Context) {
    val intents = listOf(
        // MIUI 12-14: SecurityCenter AutoStart
        Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // HyperOS / MIUI 14+
        Intent().apply {
            component = android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Fallback: PowerKeeper
        Intent().apply {
            component = android.content.ComponentName(
                "com.miui.powerkeeper",
                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
            )
            putExtra("package_name", context.packageName)
            putExtra("package_label", getAppLabel(context))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Fallback generico MIUI
        Intent().apply {
            action = "miui.intent.action.OP_AUTO_START"
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
        // Ultimo fallback: configuracoes do app
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )

    for (intent in intents) {
        try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
        } catch (_: Exception) {
            continue
        }
    }

    // Se nenhum funcionou, abrir config geral
    try {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallback)
    } catch (_: Exception) { }
}

private fun getAppLabel(context: Context): String {
    return try {
        context.packageManager.getApplicationLabel(context.applicationInfo).toString()
    } catch (_: Exception) {
        "Trakvia"
    }
}

/**
 * Verifica se deve mostrar o guia MIUI.
 * Retorna true se:
 * - E dispositivo Xiaomi/POCO/Redmi
 * - O guia nunca foi concluido
 */
fun shouldShowMiuiSetup(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
    val brand = Build.BRAND?.lowercase() ?: ""
    val isMiui = manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
            brand.contains("poco") || brand.contains("redmi") || brand.contains("xiaomi")

    if (!isMiui) return false

    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return !prefs.getBoolean("miui_setup_done", false)
}
