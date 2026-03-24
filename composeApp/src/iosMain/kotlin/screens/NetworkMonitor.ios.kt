package screens

import platform.Network.*
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * NetworkMonitor — Implementação iOS
 *
 * Usa NWPathMonitor (Network.framework) para detectar mudanças
 * de conectividade e emitir AppEvent.Conectividade.
 *
 * Equivalente ao NetworkMonitor.kt do Android que usa
 * ConnectivityManager.NetworkCallback.
 */
@OptIn(ExperimentalForeignApi::class)
object NetworkMonitor {

    private var monitor: nw_path_monitor_t? = null
    private var ultimoEstado: Boolean? = null

    /**
     * Inicia o monitoramento de rede.
     * Chamar uma vez no MainViewController ou no App init.
     */
    fun iniciar() {
        monitor = nw_path_monitor_create()

        monitor?.let { mon ->
            nw_path_monitor_set_update_handler(mon) { path ->
                val status = nw_path_get_status(path)
                val online = status == nw_path_status_satisfied
                notificar(online)
            }

            // Usar a main queue para que os eventos sejam processados
            // no thread principal (compatível com Compose)
            nw_path_monitor_set_queue(mon, dispatch_get_main_queue())
            nw_path_monitor_start(mon)
        }

        // Estado inicial
        notificar(estaOnline())
    }

    /**
     * Para o monitoramento (chamar ao sair do app).
     */
    fun parar() {
        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
        ultimoEstado = null
    }

    /**
     * Verifica conectividade atual de forma síncrona.
     * Fallback simples: tenta resolver se o monitor está ativo.
     */
    fun estaOnline(): Boolean {
        return ultimoEstado ?: true // Assume online até provar o contrário
    }

    private fun notificar(online: Boolean) {
        // Só emite evento se o estado mudou
        if (ultimoEstado != online) {
            ultimoEstado = online
            AppEvents.emitir(AppEvent.Conectividade(online))
        }
    }
}
