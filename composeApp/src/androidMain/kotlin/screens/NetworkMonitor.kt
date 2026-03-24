package screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Monitora conectividade usando NetworkCallback do Android.
 * Sem polling — o sistema notifica automaticamente quando a rede muda.
 */
object NetworkMonitor {
    private var conectivityManager: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var ultimoEstado: Boolean? = null

    fun iniciar(context: Context) {
        conectivityManager = context.getSystemService(ConnectivityManager::class.java)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                notificar(true)
            }

            override fun onLost(network: android.net.Network) {
                notificar(false)
            }

            override fun onUnavailable() {
                notificar(false)
            }
        }

        conectivityManager?.registerNetworkCallback(request, callback!!)

        // Estado inicial imediato
        notificar(estaOnline())
    }

    fun parar() {
        callback?.let { conectivityManager?.unregisterNetworkCallback(it) }
        callback = null
        conectivityManager = null
    }

    fun estaOnline(): Boolean {
        val cm = conectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun notificar(online: Boolean) {
        // Só emite evento se o estado mudou
        if (ultimoEstado != online) {
            ultimoEstado = online
            AppEvents.emitir(AppEvent.Conectividade(online))
        }
    }
}
