package br.com.lfsystem.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import app.App
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import database.AppRepository
import push.PushTokenManager
import screens.NetworkMonitor
import util.BiometricAuth
import util.SyncNotificationHelper

// FragmentActivity (não ComponentActivity puro) — BiometricPrompt na versão
// da lib usada aqui (androidx.biometric 1.1.0) exige um FragmentActivity de
// verdade; sem isso, o cast em BiometricAuth.android.kt:87 lança
// ClassCastException toda vez que o motorista tenta desbloquear com digital.
class MainActivity : FragmentActivity() {

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Relatório de falhas só da versão publicada — as falhas de quem está
        // desenvolvendo não se misturam com as dos motoristas.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Banco: um por processo (compartilhado com o envio em segundo plano).
        // Se não abrir, guarda uma cópia do arquivo antes de recriar — nunca
        // apaga. O antigo "tratamento global de crash" apagava o banco inteiro
        // em qualquer erro com "SQLite" na mensagem (disco cheio, banco
        // ocupado…), levando junto os lançamentos não enviados.
        repository = try {
            AppInicializacao.repositorio(applicationContext)
        } catch (e: Exception) {
            setContent {
                MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Erro ao iniciar. Feche e abra o app de novo.")
                    }
                }
            }
            return
        }

        // Registra a Activity pra BiometricAuth poder abrir o prompt do
        // sistema — sem isso, BiometricAuth.isAvailable()/authenticate()
        // falham silenciosamente (activity == null).
        BiometricAuth.init(this)

        // Inicia monitoramento de rede
        NetworkMonitor.iniciar(applicationContext)

        // Cria canais de notificação
        SyncNotificationHelper.criarCanal(applicationContext)
        TrakviaMessagingService.criarCanal(applicationContext)

        // Busca o token FCM atual do aparelho (não bloqueia — chega via
        // callback; LoginScreen/App.kt registram com o backend quando
        // já tiver motorista logado).
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                task.result?.let { PushTokenManager.setToken(it) }
            }
        }

        // URL da API e token criptografados (mesma rotina do envio em segundo
        // plano). A permissão de notificação é pedida depois do login, na tela
        // inicial, com explicação — não mais aqui na primeira abertura.
        AppInicializacao.configurarApi(applicationContext)

        // Envio em segundo plano: rede de segurança periódica (só com internet).
        SincronizacaoWorker.agendarPeriodico(applicationContext)

        setContent {
            val needsMiuiSetup = remember {
                screens.shouldShowMiuiSetup(applicationContext)
            }
            App(
                repository = repository,
                showMiuiSetup = needsMiuiSetup,
                miuiSetupContent = { onConcluir ->
                    screens.MiuiSetupScreen(onConcluir = onConcluir)
                }
            )
        }
    }

    override fun onStop() {
        super.onStop()
        // App indo para background — notificar se tem pendentes
        AppInicializacao.emPrimeiroPlano = false
        try {
            val pendentes = repository.countTotalPendentes()
            if (pendentes > 0) {
                SyncNotificationHelper.mostrar(applicationContext, pendentes)
                // Envia assim que houver internet, mesmo com o app fechado.
                SincronizacaoWorker.agendarQuandoTiverRede(applicationContext)
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        AppInicializacao.emPrimeiroPlano = true
        // App voltando — cancelar notificação de pendentes
        SyncNotificationHelper.cancelar(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkMonitor.parar()
    }
}
