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
import androidx.lifecycle.compose.LifecycleResumeEffect
import api.ApiClient
import api.VersaoAppResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import database.AppRepository
import push.PushTokenManager
import push.plataformaPushId
import screens.BiometricLockScreen
import screens.DashboardScreen
import screens.LoginScreen
import screens.SplashScreen
import ui.AppTheme
import ui.AtualizacaoDisponivelDialog
import ui.AtualizacaoObrigatoriaScreen

@Composable
fun App(
    repository: AppRepository,
    showMiuiSetup: Boolean = false,
    miuiSetupContent: (@Composable (onConcluir: () -> Unit) -> Unit)? = null
) {
    // Tema claro/escuro escolhido no topo da tela inicial — antes voltava ao
    // claro toda vez que o app abria. Carregado aqui, antes da primeira tela,
    // pra não piscar no tema errado.
    remember {
        try {
            repository.getPreferencia("tema")?.let { salvo ->
                ui.ThemeMode.entries.firstOrNull { it.name == salvo }?.let { ui.ThemeManager.themeMode = it }
            }
        } catch (_: Exception) {}
    }
    LaunchedEffect(Unit) {
        snapshotFlow { ui.ThemeManager.themeMode }.collect { modo ->
            try { repository.salvarPreferencia("tema", modo.name) } catch (_: Exception) {}
        }
    }

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

    // Controle de atualização (fora/site/admin/versoes.php). Checa ao abrir e
    // ao voltar pro primeiro plano — motorista deixa o app em segundo plano
    // por dias, então só checar na abertura não pegaria uma versão mínima
    // nova. Sem internet/erro: mantém o último resultado e deixa usar (o
    // app é offline-first, não pode travar o motorista na estrada por isso).
    var versaoApp by remember { mutableStateOf<VersaoAppResponse?>(null) }
    var avisoOpcionalAdiado by remember { mutableStateOf(false) }
    var ultimaChecagemVersao by remember { mutableStateOf(0L) }
    val versaoScope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        val job = versaoScope.launch {
            val agora = Clock.System.now().toEpochMilliseconds()
            if (agora - ultimaChecagemVersao < 30 * 60 * 1000L) return@launch
            val resp = try {
                withTimeoutOrNull(8_000) { ApiClient.verificarVersaoApp() }
            } catch (_: Exception) { null }
            if (resp?.status == "ok") {
                versaoApp = resp
                ultimaChecagemVersao = agora
            }
        }
        onPauseOrDispose { job.cancel() }
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
        val versao = versaoApp
        if (!showSplash && versao != null && versao.obrigatoria) {
            AtualizacaoObrigatoriaScreen(versao)
            return@AppTheme
        }
        Box(modifier = Modifier.fillMaxSize()) {
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

        if (!showSplash && versao != null && versao.opcional && !avisoOpcionalAdiado) {
            AtualizacaoDisponivelDialog(versao, onDepois = { avisoOpcionalAdiado = true })
        }
        }
    }
}
