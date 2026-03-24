package br.com.lfsystem.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import api.ApiClient
import api.ApiUrlStorage
import app.App
import database.AppRepository
import database.DatabaseDriverFactory
import screens.NetworkMonitor
import util.SyncNotificationHelper

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tratamento global de crash — evita "falhas contínuas"
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // Limpar cache de database problemático se for erro de schema
                val msg = throwable.message ?: ""
                if (msg.contains("no such column") || msg.contains("no such table") ||
                    msg.contains("has no column") || msg.contains("SQLite")) {
                    // Database corrompida — deletar para recriar
                    applicationContext.deleteDatabase("lfsystem.db")
                }
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            repository = AppRepository(DatabaseDriverFactory(applicationContext))
            // CORREÇÃO: Testar o banco ANTES de usar — força o SQLDelight a
            // validar o schema. Se o banco veio de backup com schema antigo,
            // o erro acontece AQUI (controlado) em vez de crashar depois.
            repository.getMotoristaLogado()
        } catch (e: Exception) {
            // Banco corrompido ou schema incompatível (comum após reinstalação
            // quando android:allowBackup restaura banco com schema antigo)
            try {
                applicationContext.deleteDatabase("lfsystem.db")
                repository = AppRepository(DatabaseDriverFactory(applicationContext))
            } catch (e2: Exception) {
                // Falha total — mostrar tela vazia, não crashar
                setContent {
                    MaterialTheme {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Erro ao iniciar. Reinstale o app.")
                        }
                    }
                }
                return
            }
        }

        // Inicia monitoramento de rede
        NetworkMonitor.iniciar(applicationContext)

        // Cria canais de notificação
        SyncNotificationHelper.criarCanal(applicationContext)

        // Pedir permissão de notificação (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        // Configurar storage da URL da API
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        ApiClient.setStorage(object : ApiUrlStorage {
            override fun saveApiUrl(url: String) {
                prefs.edit().putString("api_url", url).apply()
            }
            override fun getApiUrl(): String? {
                return prefs.getString("api_url", null)
            }
            override fun saveToken(token: String) {
                prefs.edit().putString("auth_token", token).apply()
            }
            override fun getToken(): String? {
                return prefs.getString("auth_token", null)
            }
        })

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
        try {
            val pendentes = repository.countTotalPendentes()
            if (pendentes > 0) {
                SyncNotificationHelper.mostrar(applicationContext, pendentes)
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        // App voltando — cancelar notificação de pendentes
        SyncNotificationHelper.cancelar(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        NetworkMonitor.parar()
    }
}
