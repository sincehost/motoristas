package br.com.lfsystem.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.App
import api.ApiClient
import api.ApiUrlStorage
import database.AppRepository
import database.DatabaseDriverFactory
import platform.Foundation.NSUserDefaults
import screens.NetworkMonitor

fun MainViewController() = ComposeUIViewController {
    val repository = remember {
        // Iniciar monitoramento de rede (equivalente ao Android)
        NetworkMonitor.iniciar()

        // Configurar storage da URL da API usando NSUserDefaults
        ApiClient.setStorage(object : ApiUrlStorage {
            private val prefs = NSUserDefaults.standardUserDefaults

            override fun saveApiUrl(url: String) {
                prefs.setObject(url, forKey = "api_url")
                prefs.synchronize()
            }

            override fun getApiUrl(): String? {
                return prefs.stringForKey("api_url")
            }

            override fun saveToken(token: String) {
                prefs.setObject(token, forKey = "auth_token")
                prefs.synchronize()
            }

            override fun getToken(): String? {
                return prefs.stringForKey("auth_token")
            }
        })

        AppRepository(DatabaseDriverFactory())
    }

    App(repository)
}