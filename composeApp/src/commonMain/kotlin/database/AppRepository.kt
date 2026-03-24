package database

import br.com.lfsystem.app.database.AppDatabase
import br.com.lfsystem.app.database.Motorista
import br.com.lfsystem.app.database.Destino
import br.com.lfsystem.app.database.Equipamento
import br.com.lfsystem.app.database.Viagem

import br.com.lfsystem.app.database.Arla
import br.com.lfsystem.app.database.Abastecimento
import br.com.lfsystem.app.database.ViagemAtual
import api.ApiClient
import util.PasswordHasher

class AppRepository(driverFactory: DatabaseDriverFactory) {

    private val database = AppDatabase(driverFactory.createDriver())
    private val queries = database.appDatabaseQueries

    // ===============================
    // MOTORISTA
    // ===============================

    fun getMotoristaLogado(): Motorista? {
        return queries.getMotorista().executeAsOneOrNull()
    }

    fun salvarMotorista(
        motoristaId: String,
        nome: String,
        cnpj: String,
        usuario: String,
        senha: String
    ) {
        val senhaHash = PasswordHasher.hash(senha)
        queries.insertMotorista(
            motorista_id = motoristaId,
            nome = nome,
            cnpj = cnpj,
            usuario = usuario,
            senha = senhaHash
        )

    }

    /**
     * Verifica credenciais offline comparando com hash armazenado.
     * Retorna true se CNPJ + usuario + senha estão corretos.
     */
    fun verificarCredenciaisOffline(cnpj: String, usuario: String, senha: String): Boolean {
        val local = getMotoristaLogado() ?: return false
        return local.cnpj == cnpj &&
               local.usuario == usuario &&
               PasswordHasher.verify(senha, local.senha)
    }

    fun logout() {
        queries.deleteMotorista()
        queries.deleteAllDestinos()
        queries.deleteAllEquipamentos()

        // IMPORTANTE: Limpa a URL da API salva
        ApiClient.limparConfiguracao()
    }

    // ===============================
    // DESTINOS (CACHE)
    // ===============================

    fun getAllDestinos(): List<Destino> {
        return queries.getAllDestinos().executeAsList()
    }

    fun getDestinosParaDropdown(): List<Pair<Long, String>> {
        return queries.getAllDestinos()
            .executeAsList()
            .map { it.servidor_id to it.nome }
    }

    fun salvarDestinos(destinos: List<Pair<String, String>>) {
        queries.deleteAllDestinos()
        destinos.forEach { (id, nome) ->
            queries.insertDestino(
                servidor_id = id.toLong(),
                nome = nome
            )
        }
    }

    fun getDestinoById(id: Long): Destino? {
        return queries.getDestinoById(id).executeAsOneOrNull()
    }

    // ===============================
    // EQUIPAMENTOS (CACHE)
    // ===============================

    fun getAllEquipamentos(): List<Equipamento> {
        return queries.getAllEquipamentos().executeAsList()
    }

    fun getEquipamentosParaDropdown(): List<Pair<Long, String>> {
        return queries.getAllEquipamentos()
            .executeAsList()
            .map { it.servidor_id to it.placa }
    }

    fun salvarEquipamentos(equipamentos: List<Pair<String, String>>) {
        queries.deleteAllEquipamentos()
        equipamentos.forEach { (id, placa) ->
            queries.insertEquipamento(
                servidor_id = id.toLong(),
                placa = placa
            )
        }
    }

    // ===============================
    // VIAGENS (OFFLINE-FIRST)
    // ===============================

    fun getAllViagens(): List<Viagem> {
        return queries.getAllViagens().executeAsList()
    }

    fun getViagensParaSincronizar(): List<Viagem> {
        return queries.getViagensNaoSincronizadas().executeAsList()
    }

    fun countViagensPendentes(): Long {
        return queries.countViagensNaoSincronizadas().executeAsOne()
    }

    fun salvarViagem(
        numerobd: String,
        cte: String,
        numerobd2: String?,
        cte2: String?,
        destinoId: Long,
        destinoNome: String,
        placa: String,
        dataViagem: String,
        kmInicio: String,
        pesoCarga: String,
        valorFrete: String?,
        fotoPainelSaida: String?,
        dataCriacao: String
    ) {
        queries.insertViagem(
            numerobd = numerobd,
            cte = cte,
            numerobd2 = numerobd2,
            cte2 = cte2,
            destino_id = destinoId,
            destino_nome = destinoNome,
            placa = placa,
            data_viagem = dataViagem,
            km_inicio = kmInicio,
            pesocarga = pesoCarga,
            valorfrete = valorFrete,
            foto_painel_saida = fotoPainelSaida,
            data_criacao = dataCriacao
        )
    }

    fun marcarViagemSincronizada(id: Long) {
        queries.marcarViagemSincronizada(id)
    }

    fun limparViagensSincronizadas() {
        queries.deleteViagensSincronizadas()
    }

    fun excluirViagemLocal(id: Long) {
        queries.deleteViagemById(id)
    }

    // Guarda o último viagem_id retornado pelo servidor após sincronização
    fun salvarUltimaViagemServidor(viagemIdServidor: Long) {
        queries.salvarUltimaViagemServidor(viagemIdServidor)
    }

    // Busca o último viagem_id do servidor (usado como fallback para ARLAs com viagem_id = -1)
    fun getUltimaViagemServidor(): Long? {
        return queries.getUltimaViagemServidor().executeAsOneOrNull()
    }

    // ===============================
    // MAPEAMENTO PERSISTENTE id_local -> id_servidor
    // Garante que o mapeamento sobrevive ao app ser fechado
    // ===============================

    fun salvarMapeamentoViagem(idLocal: Long, idServidor: Long) {
        try {
            queries.inserirMapeamentoViagem(id_local = idLocal, id_servidor = idServidor)
        } catch (e: Exception) {
            // Ignora erro silenciosamente para não quebrar o fluxo de sync
        }
    }

    fun getMapeamentoIdServidor(idLocal: Long): Long? {
        return try {
            queries.getMapeamentoViagem(id_local = idLocal).executeAsOneOrNull()
        } catch (e: Exception) {
            null
        }
    }

    fun deletarMapeamentoViagem(idLocal: Long) {
        try {
            queries.deletarMapeamentoViagem(id_local = idLocal)
        } catch (e: Exception) { }
    }

    /**
     * Retorna a última viagem NÃO SINCRONIZADA (criada offline)
     * Útil para adicionar arla/combustível/descarga em viagens offline
     */
    fun getUltimaViagemNaoSincronizada(): Viagem? {
        return queries.getViagensNaoSincronizadas()
            .executeAsList()
            .maxByOrNull { it.id }
    }

    /**
     * Deleta todos os itens com viagem_id inválido (<=0 ou NULL)
     * Isso limpa dados corrompidos que não podem ser sincronizados
     */
    fun limparDadosProblematicos() {
        database.transaction {
            queries.deletarArlasComViagemIdInvalido()
            queries.deletarAbastecimentosComViagemIdInvalido()
            queries.deletarDescargasComViagemIdInvalido()
        }
    }

    /**
     * CORREÇÃO C2: Limpa TODOS os registros sincronizados numa transação atômica.
     *
     * Antes, cada tabela era limpa separadamente. Se o app travasse entre as
     * chamadas (bateria, kill forçado, crash), o banco ficava em estado
     * inconsistente — ex: viagens removidas mas abastecimentos órfãos.
     *
     * Agora é tudo-ou-nada: ou todas as tabelas são limpas, ou nenhuma.
     */
    fun limparTodosSincronizadosAtomicamente() {
        database.transaction {
            queries.deleteViagensSincronizadas()
            queries.deleteAbastecimentosSincronizados()
            queries.deleteArlasSincronizadas()
            queries.deleteDescargasSincronizadas()
            queries.deleteManutencoesSincronizadas()
            queries.deleteFinalizacoesSincronizadas()
            queries.deleteOutrasDespesasSincronizadas()
            queries.deleteChecklistsPreSincronizados()
            queries.deleteChecklistsPosSincronizados()
        }
    }

    // ===============================
    // ARLA (IGUAL À VIAGEM)
    // ===============================

    // ===============================
// ARLA (IGUAL À VIAGEM)
// ===============================

    fun getArlasParaSincronizar(): List<Arla> {
        return queries.getArlasNaoSincronizadas().executeAsList()
    }

    fun salvarArla(
        motoristaId: String,
        viagemId: Long,
        data: String,
        valor: String,
        litros: String,
        posto: String,
        kmPosto: String,
        foto: String?
    ) {
        queries.insertArla(
            motoristaId,
            viagemId,
            data,
            valor,
            litros,
            posto,
            kmPosto,
            foto
        )
    }

    fun marcarArlaSincronizada(id: Long) {
        queries.marcarArlaSincronizada(id)
    }

    fun limparArlasSincronizadas() {
        queries.deleteArlasSincronizadas()
    }

// ===============================
// ABASTECIMENTO (IGUAL À VIAGEM)
// ===============================

    fun getAbastecimentosParaSincronizar(): List<Abastecimento> {
        return queries.getAbastecimentosNaoSincronizados().executeAsList()
    }

    fun salvarAbastecimento(
        motoristaId: String,
        viagemId: Long,
        equipamentoId: Long,
        data: String,
        valor: String,
        litros: String,
        posto: String,
        kmPosto: String,
        foto: String?,
        fotoMarcador: String?,
        tipoPagamento: String = "dinheiro",
        tipoCombustivel: String = "Diesel Caminhão",
        horas: String? = null
    ) {
        queries.insertAbastecimento(
            motoristaId,
            viagemId,
            equipamentoId,
            data,
            valor,
            litros,
            posto,
            kmPosto,
            foto,
            fotoMarcador,
            tipoPagamento,
            tipoCombustivel,
            horas
        )
    }

    fun marcarAbastecimentoSincronizado(id: Long) {
        queries.marcarAbastecimentoSincronizado(id)
    }

    fun limparAbastecimentosSincronizados() {
        queries.deleteAbastecimentosSincronizados()
    }
    // ===============================
    // VIAGEM ATUAL (OFFLINE)
    // ===============================

    fun getViagemAtual(): ViagemAtual? {
        return queries.getViagemAtual().executeAsOneOrNull()
    }

    fun salvarViagemAtual(viagemId: Long, destino: String, dataInicio: String, kmInicio: String = "", kmRota: String = "0") {
        queries.insertViagemAtual(viagemId, destino, dataInicio, kmInicio, kmRota)
    }

    fun limparViagemAtual() {
        queries.deleteViagemAtual()
    }

    // ===============================
    // CONTAGEM TOTAL
    // ===============================

    fun countTotalPendentes(): Long {
        return queries.countTotalPendentes().executeAsOne()
    }

    // ===============================
    // MANUTENÇÃO
    // ===============================

    fun salvarManutencao(
        motoristaId: String,
        viagemId: Long,
        dataManutencao: String,
        placa: String,
        servico: String,
        descricaoServico: String?,
        localManutencao: String?,
        valor: String,
        kmTrocaOleo: String?,
        kmTrocaPneu: String?,
        pneus: String?,
        tiposPneu: String?,
        fotoComprovante1: String?,
        fotoComprovante2: String?
    ): Long {
        queries.insertManutencao(
            motorista_id = motoristaId,
            viagem_id = viagemId,
            data_manutencao = dataManutencao,
            placa = placa,
            servico = servico,
            descricao_servico = descricaoServico,
            local_manutencao = localManutencao,
            valor = valor,
            km_troca_oleo = kmTrocaOleo,
            km_troca_pneu = kmTrocaPneu,
            pneus = pneus,
            tipos_pneu = tiposPneu,
            foto_comprovante1 = fotoComprovante1,
            foto_comprovante2 = fotoComprovante2
        )
        return queries.getLastInsertedManutencaoId().executeAsOne()
    }

    fun getManutencoesParaSincronizar(): List<br.com.lfsystem.app.database.Manutencao> {
        return queries.getManutencoesNaoSincronizadas().executeAsList()
    }

    fun marcarManutencaoSincronizada(id: Long) {
        queries.marcarManutencaoSincronizada(id)
    }

    fun limparManutencoesSincronizadas() {
        queries.deleteManutencoesSincronizadas()
    }

    // ===============================
    // FINALIZAÇÃO DE VIAGEM
    // ===============================

    fun salvarFinalizacaoViagem(
        motoristaId: String,
        viagemId: Long,
        dataChegada: String,
        kmChegada: String,
        pesocargaRetorno: String?,
        valorfreteRetorno: String?,
        observacao: String?,
        fotoPainelChegada: String?,
        teveRetorno: Boolean = false,
        localCarregou: String? = null,
        ordemRetorno: String? = null,
        cteRetorno: String? = null
    ) {
        queries.insertFinalizacaoViagem(
            motorista_id = motoristaId,
            viagem_id = viagemId,
            data_chegada = dataChegada,
            km_chegada = kmChegada,
            pesocarga_retorno = pesocargaRetorno,
            valorfrete_retorno = valorfreteRetorno,
            observacao = observacao,
            foto_painel_chegada = fotoPainelChegada,
            teve_retorno = if (teveRetorno) 1L else 0L,
            local_carregou = localCarregou,
            ordem_retorno = ordemRetorno,
            cte_retorno = cteRetorno
        )
    }

    fun getFinalizacoesParaSincronizar(): List<br.com.lfsystem.app.database.FinalizacaoViagem> {
        return queries.getFinalizacoesNaoSincronizadas().executeAsList()
    }

    fun marcarFinalizacaoSincronizada(id: Long) {
        queries.marcarFinalizacaoSincronizada(id)
    }

    fun limparFinalizacoesSincronizadas() {
        queries.deleteFinalizacoesSincronizadas()
    }

    // ===============================
    // DESCARGA
    // ===============================

    fun salvarDescarga(
        motoristaId: String,
        viagemId: Long,
        data: String,
        placa: String,
        ordemDescarga: Long,
        valor: String,
        foto: String?
    ) {
        queries.insertDescarga(
            motorista_id = motoristaId,
            viagem_id = viagemId,
            data_ = data,
            placa = placa,
            ordem_descarga = ordemDescarga,
            valor = valor,
            foto = foto
        )
    }

    fun getDescargasParaSincronizar(): List<br.com.lfsystem.app.database.Descarga> {
        return queries.getDescargasNaoSincronizadas().executeAsList()
    }

    fun marcarDescargaSincronizada(id: Long) {
        queries.marcarDescargaSincronizada(id)
    }

    fun limparDescargasSincronizadas() {
        queries.deleteDescargasSincronizadas()
    }

    // ===============================
    // ATUALIZAÇÃO DE IDs DE VIAGENS
    // ===============================

    /**
     * Atualiza o viagem_id de todos os itens dependentes (abastecimentos, arlas,
     * descargas, manutenções e finalizações) quando uma viagem é sincronizada
     * e recebe um novo ID do servidor
     *
     * @param idLocal ID local da viagem antes da sincronização
     * @param idServidor ID retornado pelo servidor após sincronização
     */
    fun atualizarViagemIdEmDependentes(idLocal: Long, idServidor: Long) {
        database.transaction {
            // Atualizar abastecimentos
            queries.atualizarViagemIdAbastecimentos(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )

            // Atualizar arlas
            queries.atualizarViagemIdArlas(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )

            // Atualizar descargas
            queries.atualizarViagemIdDescargas(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )

            // Atualizar manutenções
            queries.atualizarViagemIdManutencoes(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )

            // Atualizar finalizações
            queries.atualizarViagemIdFinalizacoes(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )

            // Atualizar outras despesas
            queries.atualizarViagemIdOutrasDespesas(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )

            // Atualizar checklists pré-viagem
            queries.atualizarViagemIdChecklistsPre(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )

            // Atualizar checklists pós-viagem
            queries.atualizarViagemIdChecklistsPos(
                viagem_id_novo = idServidor,
                viagem_id_antigo = idLocal
            )
        }

    }

    // ===============================
    // OUTRAS DESPESAS (Prioridade 2 - #6)
    // ===============================

    fun salvarOutraDespesa(
        motoristaId: String,
        viagemId: Long,
        tipo: String,
        descricao: String?,
        valor: String,
        data: String,
        local: String?,
        fotoComprovante: String?
    ) {
        queries.insertOutraDespesa(
            motorista_id = motoristaId,
            viagem_id = viagemId,
            tipo = tipo,
            descricao = descricao,
            valor = valor,
            data_ = data,
            local_ = local,
            foto_comprovante = fotoComprovante
        )
    }

    fun getOutrasDespesasParaSincronizar(): List<br.com.lfsystem.app.database.OutraDespesa> {
        return queries.getOutrasDespesasNaoSincronizadas().executeAsList()
    }

    fun marcarOutraDespesaSincronizada(id: Long) {
        queries.marcarOutraDespesaSincronizada(id)
    }

    fun limparOutrasDespesasSincronizadas() {
        queries.deleteOutrasDespesasSincronizadas()
    }

    // ===============================
    // CHECKLIST PRÉ-VIAGEM
    // ===============================

    fun salvarChecklistPre(
        motoristaId: String,
        viagemId: Long,
        dataChecklist: String,
        placa: String,
        docCnhValida: Boolean, docCrlvVeiculo: Boolean, docAnttValida: Boolean, docSeguroCarga: Boolean, docOrdemColeta: Boolean,
        eletFarolDianteiro: Boolean, eletFarolTraseiro: Boolean, eletLuzFreio: Boolean, eletSetaDireita: Boolean, eletSetaEsquerda: Boolean, eletLuzRe: Boolean, eletPainelFuncionando: Boolean,
        pneuCalibragemOk: Boolean, pneuEstadoConservacao: Boolean, pneuEstepeOk: Boolean, pneuFerramentasTroca: Boolean,
        fluidoOleoMotor: Boolean, fluidoAguaRadiador: Boolean, fluidoFluidoFreio: Boolean, fluidoArla32: Boolean, fluidoCombustivel: Boolean,
        segExtintorValidade: Boolean, segTriangulo: Boolean, segMacacoChaveRoda: Boolean, segConesFaixa: Boolean, segEpiCompleto: Boolean,
        carrLonasCordas: Boolean, carrPortasBau: Boolean, carrAssoalhoEstado: Boolean, carrTravasLacres: Boolean,
        cabBancosCintos: Boolean, cabEspelhosRetrovisores: Boolean, cabLimpadorParabrisa: Boolean, cabArCondicionado: Boolean, cabFreioEstacionamento: Boolean,
        observacoes: String?
    ) {
        queries.insertChecklistPre(
            motorista_id = motoristaId, viagem_id = viagemId, data_checklist = dataChecklist, placa = placa,
            doc_cnh_valida = if (docCnhValida) 1L else 0L, doc_crlv_veiculo = if (docCrlvVeiculo) 1L else 0L,
            doc_antt_valida = if (docAnttValida) 1L else 0L, doc_seguro_carga = if (docSeguroCarga) 1L else 0L,
            doc_ordem_coleta = if (docOrdemColeta) 1L else 0L,
            elet_farol_dianteiro = if (eletFarolDianteiro) 1L else 0L, elet_farol_traseiro = if (eletFarolTraseiro) 1L else 0L,
            elet_luz_freio = if (eletLuzFreio) 1L else 0L, elet_seta_direita = if (eletSetaDireita) 1L else 0L,
            elet_seta_esquerda = if (eletSetaEsquerda) 1L else 0L, elet_luz_re = if (eletLuzRe) 1L else 0L,
            elet_painel_funcionando = if (eletPainelFuncionando) 1L else 0L,
            pneu_calibragem_ok = if (pneuCalibragemOk) 1L else 0L, pneu_estado_conservacao = if (pneuEstadoConservacao) 1L else 0L,
            pneu_estepe_ok = if (pneuEstepeOk) 1L else 0L, pneu_ferramentas_troca = if (pneuFerramentasTroca) 1L else 0L,
            fluido_oleo_motor = if (fluidoOleoMotor) 1L else 0L, fluido_agua_radiador = if (fluidoAguaRadiador) 1L else 0L,
            fluido_fluido_freio = if (fluidoFluidoFreio) 1L else 0L, fluido_arla32 = if (fluidoArla32) 1L else 0L,
            fluido_combustivel = if (fluidoCombustivel) 1L else 0L,
            seg_extintor_validade = if (segExtintorValidade) 1L else 0L, seg_triangulo = if (segTriangulo) 1L else 0L,
            seg_macaco_chave_roda = if (segMacacoChaveRoda) 1L else 0L, seg_cones_faixa = if (segConesFaixa) 1L else 0L,
            seg_epi_completo = if (segEpiCompleto) 1L else 0L,
            carr_lonas_cordas = if (carrLonasCordas) 1L else 0L, carr_portas_bau = if (carrPortasBau) 1L else 0L,
            carr_assoalho_estado = if (carrAssoalhoEstado) 1L else 0L, carr_travas_lacres = if (carrTravasLacres) 1L else 0L,
            cab_bancos_cintos = if (cabBancosCintos) 1L else 0L, cab_espelhos_retrovisores = if (cabEspelhosRetrovisores) 1L else 0L,
            cab_limpador_parabrisa = if (cabLimpadorParabrisa) 1L else 0L, cab_ar_condicionado = if (cabArCondicionado) 1L else 0L,
            cab_freio_estacionamento = if (cabFreioEstacionamento) 1L else 0L,
            observacoes = observacoes
        )
    }

    fun getChecklistsPreParaSincronizar(): List<br.com.lfsystem.app.database.ChecklistPreViagem> {
        return queries.getChecklistsPreNaoSincronizados().executeAsList()
    }

    fun marcarChecklistPreSincronizado(id: Long) {
        queries.marcarChecklistPreSincronizado(id)
    }

    fun limparChecklistsPreSincronizados() {
        queries.deleteChecklistsPreSincronizados()
    }

    fun getChecklistPrePorViagem(viagemId: Long): br.com.lfsystem.app.database.ChecklistPreViagem? {
        return queries.getChecklistPrePorViagem(viagemId).executeAsOneOrNull()
    }

    // ===============================
    // CHECKLIST PÓS-VIAGEM
    // ===============================

    fun salvarChecklistPos(
        motoristaId: String,
        viagemId: Long,
        dataChecklist: String,
        placa: String,
        avariaCarroceria: Boolean, avariaCabine: Boolean, avariaPneus: Boolean, avariaEspelhos: Boolean, avariaFarois: Boolean, avariaDescricao: String?,
        posNivelOleo: Boolean, posNivelAgua: Boolean, posNivelCombustivel: Boolean, posNivelArla: Boolean,
        limpCabineLimpa: Boolean, limpCarroceriaLimpa: Boolean, limpBauVazio: Boolean,
        funcFreiosOk: Boolean, funcDirecaoOk: Boolean, funcSuspensaoOk: Boolean, funcMotorRuido: Boolean, funcCambioOk: Boolean,
        pendManutencaoUrgente: Boolean, pendDescricaoManutencao: String?, pendAbastecimentoNecessario: Boolean, pendTrocaOleoProxima: Boolean, pendKmAtual: String?,
        observacoes: String?
    ) {
        queries.insertChecklistPos(
            motorista_id = motoristaId, viagem_id = viagemId, data_checklist = dataChecklist, placa = placa,
            avaria_carroceria = if (avariaCarroceria) 1L else 0L, avaria_cabine = if (avariaCabine) 1L else 0L,
            avaria_pneus = if (avariaPneus) 1L else 0L, avaria_espelhos = if (avariaEspelhos) 1L else 0L,
            avaria_farois = if (avariaFarois) 1L else 0L, avaria_descricao = avariaDescricao,
            pos_nivel_oleo = if (posNivelOleo) 1L else 0L, pos_nivel_agua = if (posNivelAgua) 1L else 0L,
            pos_nivel_combustivel = if (posNivelCombustivel) 1L else 0L, pos_nivel_arla = if (posNivelArla) 1L else 0L,
            limp_cabine_limpa = if (limpCabineLimpa) 1L else 0L, limp_carroceria_limpa = if (limpCarroceriaLimpa) 1L else 0L,
            limp_bau_vazio = if (limpBauVazio) 1L else 0L,
            func_freios_ok = if (funcFreiosOk) 1L else 0L, func_direcao_ok = if (funcDirecaoOk) 1L else 0L,
            func_suspensao_ok = if (funcSuspensaoOk) 1L else 0L, func_motor_ruido = if (funcMotorRuido) 1L else 0L,
            func_cambio_ok = if (funcCambioOk) 1L else 0L,
            pend_manutencao_urgente = if (pendManutencaoUrgente) 1L else 0L, pend_descricao_manutencao = pendDescricaoManutencao,
            pend_abastecimento_necessario = if (pendAbastecimentoNecessario) 1L else 0L,
            pend_troca_oleo_proxima = if (pendTrocaOleoProxima) 1L else 0L, pend_km_atual = pendKmAtual,
            observacoes = observacoes
        )
    }

    fun getChecklistsPosParaSincronizar(): List<br.com.lfsystem.app.database.ChecklistPosViagem> {
        return queries.getChecklistsPosNaoSincronizados().executeAsList()
    }

    fun marcarChecklistPosSincronizado(id: Long) {
        queries.marcarChecklistPosSincronizado(id)
    }

    fun limparChecklistsPosSincronizados() {
        queries.deleteChecklistsPosSincronizados()
    }

    fun getChecklistPosPorViagem(viagemId: Long): br.com.lfsystem.app.database.ChecklistPosViagem? {
        return queries.getChecklistPosPorViagem(viagemId).executeAsOneOrNull()
    }

    // ===============================
    // FOTOS OFFLINE (Offline.sq)
    // ===============================

    private val offlineQueries = database.offlineQueries

    fun insertFotoOffline(
        viagemId: Long,
        tipo: String,
        imagemBase64: String,
        latitude: Double?,
        longitude: Double?
    ) {
        offlineQueries.insertFotoOffline(
            viagem_id = viagemId,
            tipo = tipo,
            imagem_base64 = imagemBase64,
            latitude = latitude,
            longitude = longitude
        )
    }

    fun getLastFotoOfflineId(): Long {
        // SQLite last_insert_rowid() retorna o ID da última inserção
        return offlineQueries.countFotosNaoSincronizadas().executeAsOne()
    }

    fun getFotosNaoSincronizadas(viagemId: Long): List<br.com.lfsystem.app.database.Fotos_offline> {
        return try {
            offlineQueries.getFotosNaoSincronizadas(viagemId).executeAsList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getAllFotosNaoSincronizadas(): List<br.com.lfsystem.app.database.Fotos_offline> {
        return try {
            offlineQueries.getAllFotosNaoSincronizadas().executeAsList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun marcarFotoOfflineSincronizada(fotoId: Long) {
        offlineQueries.marcarFotoSincronizada(fotoId)
    }

    fun deletarFotoOffline(fotoId: Long) {
        offlineQueries.deletarFoto(fotoId)
    }

    fun countFotosOfflinePendentes(): Long {
        return try {
            offlineQueries.countFotosNaoSincronizadas().executeAsOne()
        } catch (e: Exception) {
            0L
        }
    }
}