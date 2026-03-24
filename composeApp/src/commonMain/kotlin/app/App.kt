package app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import screens.DashboardScreen
import screens.LoginScreen
import screens.SplashScreen
import ui.AppTheme

@Composable
fun App(
    repository: AppRepository,
    showMiuiSetup: Boolean = false,
    miuiSetupContent: (@Composable (onConcluir: () -> Unit) -> Unit)? = null
) {
    var isLoggedIn by remember {
        mutableStateOf(
            try { repository.getMotoristaLogado() != null }
            catch (_: Exception) { false }
        )
    }
    var showSplash by remember { mutableStateOf(true) }
    var needsMiuiSetup by remember { mutableStateOf(showMiuiSetup) }
    var appError by remember { mutableStateOf<String?>(null) }

    // Se algum erro grave aconteceu, mostrar tela de erro
    if (appError != null) {
        MaterialTheme {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        "Ocorreu um erro",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        appError ?: "Erro desconhecido",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        appError = null
                        showSplash = true
                    }) {
                        Text("Tentar novamente")
                    }
                }
            }
        }
        return
    }

    AppTheme {
        if (showSplash) {
            SplashScreen(onFinished = { showSplash = false })
        } else if (needsMiuiSetup && miuiSetupContent != null) {
            miuiSetupContent { needsMiuiSetup = false }
        } else if (isLoggedIn) {
            DashboardScreen(
                repository = repository,
                onLogout = { isLoggedIn = false }
            )
        } else {
            LoginScreen(
                repository = repository,
                onLoginSuccess = {
                    isLoggedIn = true
                    if (showMiuiSetup) {
                        needsMiuiSetup = true
                    }
                }
            )
        }
    }
}
