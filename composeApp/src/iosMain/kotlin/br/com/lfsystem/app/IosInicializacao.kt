package br.com.lfsystem.app

import api.ApiClient
import api.ApiUrlStorage
import database.AppRepository
import database.DatabaseDriverFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSUserDefaults
import sync.SyncManager
import util.LogWriter

/**
 * Inicialização compartilhada entre a tela (MainViewController) e o envio em
 * segundo plano (SincronizacaoSegundoPlano, chamado pelo AppDelegate.swift) —
 * mesmo papel do AppInicializacao do Android. Tudo aqui roda na thread
 * principal (o AppDelegate registra a tarefa com DispatchQueue.main).
 */
object IosInicializacao {

    private var repositorio: AppRepository? = null
    private var apiConfigurada = false

    /** Um único AppRepository (uma conexão com o banco) pro app inteiro. */
    fun repositorio(): AppRepository =
        repositorio ?: AppRepository(DatabaseDriverFactory()).also { repositorio = it }

    /** URL da API e token guardados em NSUserDefaults. */
    fun configurarApi() {
        if (apiConfigurada) return
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
        apiConfigurada = true
    }
}

/**
 * Envio dos lançamentos pendentes com o app fora da tela — exposto ao Swift
 * como `SincronizacaoSegundoPlano.shared`. Antes o iPhone só enviava com o
 * app aberto: o motorista salvava o abastecimento, trocava de app, e o
 * lançamento ficava parado até ele voltar.
 */
object SincronizacaoSegundoPlano {

    private val escopo = MainScope()
    private var trabalho: Job? = null

    fun temPendencias(): Boolean = try {
        IosInicializacao.repositorio().countTotalPendentes() > 0
    } catch (_: Exception) {
        false
    }

    /**
     * Envia o que estiver pendente. [aoTerminar] recebe true se a rodada
     * terminou sem exceção. Não é chamado se [cancelar] interromper antes.
     */
    fun executar(aoTerminar: (Boolean) -> Unit) {
        val repo = try {
            IosInicializacao.configurarApi()
            IosInicializacao.repositorio()
        } catch (e: Exception) {
            LogWriter.log("❌ Segundo plano: banco/API não abriram (${e.message})")
            aoTerminar(false)
            return
        }
        val motorista = repo.getMotoristaLogado()
        if (motorista == null || repo.countTotalPendentes() == 0L) {
            aoTerminar(true)
            return
        }
        trabalho?.cancel()
        trabalho = escopo.launch {
            val ok = try {
                SyncManager(repo).sincronizar(motorista = motorista, silencioso = true) { }
                LogWriter.log("🔄 Segundo plano: restam ${repo.countTotalPendentes()} pendente(s)")
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogWriter.log("❌ Segundo plano falhou: ${e.message}")
                false
            }
            aoTerminar(ok)
        }
    }

    /** O iOS avisou que o tempo acabou — para o envio no ponto em que estiver. */
    fun cancelar() {
        trabalho?.cancel()
        trabalho = null
    }
}
