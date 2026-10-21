package br.com.lfsystem.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import api.ApiClient
import api.ApiUrlStorage
import database.AppRepository
import database.DatabaseDriverFactory
import util.LogWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Inicialização compartilhada entre a MainActivity e o envio em segundo plano
 * (SincronizacaoWorker) — o worker roda com o app fechado, sem Activity, e
 * precisa do mesmo banco e da mesma configuração da API.
 */
object AppInicializacao {

    private const val NOME_BANCO = "lfsystem.db"

    @Volatile
    private var repositorio: AppRepository? = null

    /** true entre onResume e onStop da MainActivity. */
    @Volatile
    var emPrimeiroPlano = false

    /**
     * Um único AppRepository (uma conexão com o banco) por processo: a tela e o
     * worker não ficam disputando o arquivo com duas conexões separadas.
     */
    fun repositorio(context: Context): AppRepository =
        repositorio ?: synchronized(this) {
            repositorio ?: abrirBanco(context.applicationContext).also { repositorio = it }
        }

    /**
     * Abre o banco e lê o motorista pra forçar a validação do schema. Se falhar
     * (ex.: banco restaurado de backup com schema antigo), GUARDA uma cópia do
     * arquivo antes de criar um banco novo — antes o arquivo era apagado e os
     * lançamentos não enviados sumiam sem aviso. A cópia fica na pasta de
     * bancos do app (lfsystem_backup_<data>.db) pra recuperação manual.
     */
    private fun abrirBanco(context: Context): AppRepository {
        return try {
            AppRepository(DatabaseDriverFactory(context)).also { it.getMotoristaLogado() }
        } catch (e: Exception) {
            LogWriter.log("❌ Banco não abriu (${e::class.simpleName}: ${e.message}) — guardando cópia e recriando")
            guardarCopiaDoBanco(context)
            AppRepository(DatabaseDriverFactory(context))
        }
    }

    private fun guardarCopiaDoBanco(context: Context) {
        val original = context.getDatabasePath(NOME_BANCO)
        if (!original.exists()) return
        val carimbo = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val pasta = original.parentFile ?: return
        // O banco tem arquivos auxiliares (-wal/-shm/-journal) — vão juntos.
        listOf("", "-wal", "-shm", "-journal").forEach { sufixo ->
            val arquivo = File(pasta, NOME_BANCO + sufixo)
            if (arquivo.exists()) {
                val destino = File(pasta, "lfsystem_backup_${carimbo}.db$sufixo")
                if (!arquivo.renameTo(destino)) {
                    try {
                        arquivo.copyTo(destino, overwrite = true)
                        arquivo.delete()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * URL da API e token — criptografados via Android Keystore
     * (EncryptedSharedPreferences). Migra automaticamente o arquivo antigo em
     * texto puro, se existir, pra não forçar relogin. Se o Keystore falhar,
     * cai pro arquivo antigo em vez de travar o app.
     */
    fun configurarApi(context: Context) {
        val ctx = context.applicationContext
        val legacyPrefs = ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val securePrefs = try {
            val masterKey = MasterKey.Builder(ctx)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                ctx,
                "app_prefs_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            null
        }
        if (securePrefs != null && legacyPrefs.contains("auth_token")) {
            securePrefs.edit()
                .putString("api_url", legacyPrefs.getString("api_url", null))
                .putString("auth_token", legacyPrefs.getString("auth_token", null))
                .apply()
            legacyPrefs.edit().clear().apply()
        }
        val prefs = securePrefs ?: legacyPrefs
        ApiClient.setStorage(object : ApiUrlStorage {
            override fun saveApiUrl(url: String) {
                prefs.edit().putString("api_url", url).apply()
            }
            override fun getApiUrl(): String? = prefs.getString("api_url", null)
            override fun saveToken(token: String) {
                prefs.edit().putString("auth_token", token).apply()
            }
            override fun getToken(): String? = prefs.getString("auth_token", null)
        })
    }
}
