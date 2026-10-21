package br.com.lfsystem.app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import sync.SyncManager
import util.LogWriter
import util.SyncNotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Envio dos lançamentos pendentes com o app FECHADO (Android). Antes, a fila
 * só andava com o app aberto na tela inicial — o motorista lançava, guardava
 * o celular e nada subia até ele abrir o app de novo com sinal.
 *
 * Roda só com internet (restrição do WorkManager), usa o mesmo SyncManager da
 * tela (com a trava global, nunca envia em paralelo com ela) e tenta de novo
 * com espera crescente enquanto sobrar item parado por falta de conexão.
 * Lançamento recusado pelo servidor fica para o motorista corrigir.
 */
class SincronizacaoWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return try {
            AppInicializacao.configurarApi(ctx)
            val repo = AppInicializacao.repositorio(ctx)
            val motorista = repo.getMotoristaLogado() ?: return Result.success()
            if (repo.countTotalPendentes() == 0L) {
                SyncNotificationHelper.cancelar(ctx)
                return Result.success()
            }

            SyncManager(repo).sincronizar(motorista = motorista, silencioso = true) { }

            val restantes = repo.countTotalPendentes()
            val recusados = repo.getLancamentosRecusados().size
            LogWriter.log("🔄 Worker: restam $restantes pendente(s), $recusados recusado(s)")
            when {
                restantes == 0L -> { SyncNotificationHelper.cancelar(ctx); Result.success() }
                // Sobrou algo que não é recusa do servidor (sem internet, servidor
                // fora, viagem ainda subindo) — tenta de novo mais tarde.
                restantes > recusados -> { lembrarPendentes(restantes); Result.retry() }
                else -> { lembrarPendentes(restantes); Result.success() }
            }
        } catch (e: Exception) {
            LogWriter.log("❌ Worker de sincronização falhou: ${e.message}")
            Result.retry()
        }
    }

    // Lembrete na barra de notificações só com o app fechado — com o motorista
    // dentro do app a tela inicial já mostra as pendências.
    private fun lembrarPendentes(restantes: Long) {
        if (!AppInicializacao.emPrimeiroPlano) SyncNotificationHelper.mostrar(applicationContext, restantes)
    }

    companion object {
        private const val NOME_PERIODICO = "sincronizacao_periodica"
        private const val NOME_AGORA = "sincronizacao_quando_tiver_rede"

        private val comInternet = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Rede de segurança: a cada ~15 min (mínimo do Android), só com internet. */
        fun agendarPeriodico(context: Context) {
            val pedido = PeriodicWorkRequestBuilder<SincronizacaoWorker>(15, TimeUnit.MINUTES)
                .setConstraints(comInternet)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NOME_PERIODICO, ExistingPeriodicWorkPolicy.KEEP, pedido)
        }

        /** Envia assim que houver internet — chamado quando o app vai pro fundo com pendências. */
        fun agendarQuandoTiverRede(context: Context) {
            val pedido = OneTimeWorkRequestBuilder<SincronizacaoWorker>()
                .setConstraints(comInternet)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOME_AGORA, ExistingWorkPolicy.REPLACE, pedido)
        }
    }
}
