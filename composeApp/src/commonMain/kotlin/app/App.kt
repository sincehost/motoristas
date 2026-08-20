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
import api.ApiClient
import database.AppRepository
import push.PushTokenManager
import push.plataformaPushId
import screens.BiometricLockScreen
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
    // Trava biométrica: só pede quando o app abre já logado (sessão local
    // persistente) — login novo via LoginScreen não passa por aqui, porque
    // o motorista acabou de confirmar a identidade digitando a senha.
    var isLocked by remember { mutableStateOf(isLoggedIn) }

    // Re-registra o token de push sempre que ele mudar (renovação do FCM)
    // enquanto já houver um motorista logado — o registro do 1º login já
    // acontece dentro do próprio LoginScreen.
    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        PushTokenManager.token.collect { fcmToken ->
            if (fcmToken == null) return@collect
            val motoristaId = try { repository.getMotoristaLogado()?.motorista_id } catch (_: Exception) { null }
            if (motoristaId != null) {
                try {
                    ApiClient.registrarFcmToken(motoristaId, fcmToken, plataformaPushId())
                } catch (_: Exception) {}
            }
        }
    }

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
        } else if (isLoggedIn && isLocked) {
            BiometricLockScreen(
                repository = repository,
                onUnlocked = { isLocked = false },
                onLogout = { isLoggedIn = false; isLocked = false }
            )
        } else if (isLoggedIn) {
            DashboardScreen(
                repository = repository,
                onLogout = { isLoggedIn = false; isLocked = false }
            )
        } else {
            LoginScreen(
                repository = repository,
                onLoginSuccess = {
                    isLoggedIn = true
                    isLocked = false
                    if (showMiuiSetup) {
                        needsMiuiSetup = true
                    }
                }
            )
        }
    }
}
