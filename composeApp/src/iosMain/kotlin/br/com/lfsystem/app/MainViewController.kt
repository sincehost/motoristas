package br.com.lfsystem.app

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.App
import screens.NetworkMonitor

fun MainViewController() = ComposeUIViewController {
    val repository = remember {
        // Iniciar monitoramento de rede (equivalente ao Android)
        NetworkMonitor.iniciar()

        // Mesma API e mesmo banco que o envio em segundo plano usa
        // (IosInicializacao) — uma conexão só com o arquivo do banco.
        IosInicializacao.configurarApi()
        IosInicializacao.repositorio()
    }

    App(repository)
}
