package sync

import api.ApiClient
import database.AppRepository
import database.TipoCombustivelSync
import database.TipoDespesaSync
import kotlinx.datetime.Clock
import util.LogWriter

/**
 * Catálogos da empresa (rotas, placas, formas de pagamento, tipos de despesa e
 * de combustível, tipo de operação) — um lugar só pra baixar e salvar.
 *
 * Antes cada tela e cada volta ao painel baixava tudo de novo (sync.php), e a
 * sincronização usava esse mesmo download só pra testar se tinha internet. Com
 * 2G na estrada, isso atrasava tudo e gastava o plano de dados do motorista.
 * Agora baixa no máximo uma vez a cada [INTERVALO_MS], a não ser que peça
 * [atualizar] com forcar = true (puxar pra atualizar no painel).
 */
object CatalogoSync {

    private const val INTERVALO_MS = 60 * 60 * 1000L

    private var ultimaAtualizacao = 0L

    /** Login acabou de baixar os catálogos — conta como atualização. */
    fun marcarAtualizado() {
        ultimaAtualizacao = Clock.System.now().toEpochMilliseconds()
    }

    /**
     * Baixa e salva os catálogos se a última atualização tiver mais de uma
     * hora (ou se [forcar]). Nunca lança exceção: sem internet, fica com o que
     * já está salvo no celular. Retorna true se atualizou agora.
     */
    suspend fun atualizar(repository: AppRepository, forcar: Boolean = false): Boolean {
        val agora = Clock.System.now().toEpochMilliseconds()
        if (!forcar && agora - ultimaAtualizacao < INTERVALO_MS) return false
        return try {
            val sync = ApiClient.syncDados(repository.getMotoristaLogado()?.motorista_id ?: "")
            if (sync.status != "ok") return false
            repository.salvarDestinos(sync.destinos.map { it.id to it.nome })
            repository.salvarEquipamentos(sync.equipamentos)
            repository.salvarConfiguracaoEmpresa(sync.configuracoes.tipo_operacao)
            repository.salvarFormasPagamento(sync.formas_pagamento.map { Triple(it.id, it.nome, it.codigo) })
            repository.salvarTiposDespesa(sync.tipos_despesa.map { TipoDespesaSync(it.id, it.nome, it.icone, it.cor) })
            repository.salvarTiposCombustivel(sync.tipos_combustivel.map { TipoCombustivelSync(it.id, it.nome, it.requer_horas) })
            ultimaAtualizacao = agora
            true
        } catch (e: Exception) {
            LogWriter.log("⚠️ Catálogos não atualizados: ${e.message}")
            false
        }
    }
}
