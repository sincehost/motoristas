package util

import android.content.Context
import br.com.lfsystem.app.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico do app (Android).
 *
 * - Guarda só as últimas [MAX_LINHAS] linhas: antes a lista crescia sem limite
 *   durante toda a sessão.
 * - Seguro entre threads: a sincronização roda fora da thread da tela e
 *   também em segundo plano (SincronizacaoWorker).
 * - Cada linha vai como "rastro" pro Crashlytics: se o app quebrar, o
 *   relatório chega com o que aconteceu antes. As linhas não levam senha,
 *   token nem foto.
 * - No console (Logcat) só em versão de desenvolvimento.
 */
actual object LogWriter {

    private const val MAX_LINHAS = 300
    private var context: Context? = null
    private val logs = ArrayDeque<String>()
    private val formato = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    actual fun log(message: String) {
        val linha = synchronized(this) {
            val l = "[${formato.format(Date())}] $message"
            logs.addLast(l)
            while (logs.size > MAX_LINHAS) logs.removeFirst()
            l
        }
        try { FirebaseCrashlytics.getInstance().log(linha) } catch (_: Exception) {}
        if (BuildConfig.DEBUG) println(linha)
    }

    actual fun saveToFile(): String {
        val ctx = context ?: return "Erro: Context não inicializado"
        return try {
            val pasta = File(ctx.getExternalFilesDir(null), "logs").apply { mkdirs() }
            val nome = "sync_${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.txt"
            val arquivo = File(pasta, nome)
            arquivo.writeText(getLogsAsText())
            arquivo.absolutePath
        } catch (e: Exception) {
            "Erro ao salvar: ${e.message}"
        }
    }

    actual fun clearLogs() {
        synchronized(this) { logs.clear() }
    }

    actual fun getLogsAsText(): String = synchronized(this) { logs.joinToString("\n") }
}
