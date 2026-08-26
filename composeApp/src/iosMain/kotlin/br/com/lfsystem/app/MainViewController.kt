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
import util.Keychain

fun MainViewController() = ComposeUIViewController {
    val repository = remember {
        // Iniciar monitoramento de rede (equivalente ao Android)
        NetworkMonitor.iniciar()

        // Token de sessão vai pro Keychain (não NSUserDefaults) — o token
        // Bearer dá acesso a todos os dados do tenant, e NSUserDefaults é
        // texto puro que entra em backup do iTunes/iCloud e é legível por
        // qualquer processo com acesso ao sandbox do app. api_url não é
        // sensível, continua em NSUserDefaults normalmente.
        // Migra automaticamente o valor antigo em texto puro, se existir,
        // pra não forçar relogin de quem já estava logado antes desta
        // correção.
        val legacyPrefs = NSUserDefaults.standardUserDefaults
        legacyPrefs.stringForKey("auth_token")?.let { tokenAntigo ->
            Keychain.set("auth_token", tokenAntigo)
            legacyPrefs.removeObjectForKey("auth_token")
            legacyPrefs.synchronize()
        }

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
                Keychain.set("auth_token", token)
            }

            override fun getToken(): String? {
                return Keychain.get("auth_token")
            }
        })

        AppRepository(DatabaseDriverFactory())
    }

    App(repository)
}