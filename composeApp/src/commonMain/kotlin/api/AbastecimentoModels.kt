package api

import kotlinx.serialization.Serializable

// ========== VIAGEM ABERTA (compartilhado) ==========
@Serializable
data class ViagemAberta(
    val id: Int,
    val destino: String,
    val data: String,
    val km_inicio: String = ""
)

// ========== ABASTECIMENTO ==========
@Serializable
data class AbastecimentoDadosRequest(

    val motorista_id: String
)

@Serializable
data class AbastecimentoDadosResponse(
    val status: String,
    val mensagem: String? = null,
    val viagens: List<ViagemAberta> = emptyList(),
    val placas: List<String> = emptyList()
)

@Serializable
data class SalvarAbastecimentoRequest(

    val motorista_id: String,
    val viagem_id: Int,
    val data: String,
    val placa: String,
    val nome_posto: String,
    val tipo_combustivel: String,
    val tipo_pagamento: String,
    val litros_abastecidos: String,
    val valor_litro: String,
    val valor: String,
    val km_posto: String,
    val horas: String? = null,
    val cupom_fiscal_base64: String? = null,
    val marcador_base64: String? = null
)

@Serializable
data class SalvarAbastecimentoResponse(
    val status: String,
    val mensagem: String? = null
)

// ========== ARLA ==========
@Serializable
data class ArlaDadosRequest(

    val motorista_id: String
)

@Serializable
data class ArlaDadosResponse(
    val status: String,
    val mensagem: String? = null,
    val viagens: List<ViagemAberta> = emptyList()
)

@Serializable
data class SalvarArlaRequest(

    val motorista_id: String,
    val viagem_id: Int,
    val data: String,
    val valor: String,
    val litros: String,
    val posto: String,
    val km_posto: String,
    val foto_base64: String? = null
)

@Serializable
data class SalvarArlaResponse(
    val status: String,
    val mensagem: String? = null
)

// ===============================
// FINALIZAR VIAGEM
// ===============================
@Serializable
data class FinalizarViagemRequest(

    val motorista_id: String,
    val viagem_id: Int,
    val km_chegada: String,
    val data_chegada: String,
    val observacao: String? = null,
    val teve_retorno: Boolean,
    val peso_carga_retorno: String? = null,
    val valor_frete_retorno: String? = null,
    val local_carregou: String? = null,
    val ordem_retorno: String? = null,
    val cte_retorno: String? = null,
    val foto_painel_base64: String? = null
)

@Serializable
data class FinalizarViagemResponse(
    val status: String,
    val mensagem: String? = null
)

// ===============================
// MANUTENÇÃO
// ===============================
@Serializable
data class SalvarManutencaoRequest(

    val motorista_id: String,
    val viagem_id: Int,
    val data_manutencao: String,
    val placa: String,
    val servico: String,
    val descricao_servico: String? = null,
    val local_manutencao: String? = null,
    val valor: String,
    val km_troca_oleo: String? = null,
    val km_troca_pneu: String? = null,
    val pneus: List<Int> = emptyList(),
    val tipos_pneu: Map<Int, String> = emptyMap(),
    val foto_comprovante1: String? = null,
    val foto_comprovante2: String? = null
)

@Serializable
data class SalvarManutencaoResponse(
    val status: String,
    val mensagem: String? = null,
    val manutencao_id: Int? = null
)

// ===============================
// LISTAGEM DE MANUTENÇÕES - ATUALIZADO COM PAGINAÇÃO
// ===============================
@Serializable
data class ManutencaoItem(
    val id: Int = 0,
    val viagem_id: Int = 0,
    val data_manutencao: String = "",
    val placa: String = "",
    val servico: String = "",
    val descricao_servico: String = "",
    val local_manutencao: String = "",
    val valor: String = "0"
)

@Serializable
data class ManutencoesDadosResponse(
    val status: String,
    val mensagem: String? = null,
    val manutencoes: List<ManutencaoItem> = emptyList(),
    // NOVOS CAMPOS PARA PAGINAÇÃO
    val page: Int = 1,
    val total_pages: Int = 1,
    val total: Int = 0
)

// ===============================
// RELATÓRIO DE VIAGENS
// ===============================
@Serializable
data class RelatorioViagensResponse(
    val status: String,
    val mensagem: String? = null,
    val total_viagens: Int = 0,
    val total_km_rodados: Double = 0.0,
    val total_litros_diesel: Double = 0.0,
    val total_valor_diesel: Double = 0.0,
    val total_litros_arla: Double = 0.0,
    val total_valor_arla: Double = 0.0,
    val total_valor_frete: Double = 0.0,
    val total_valor_frete_retorno: Double = 0.0,
    val total_valor_descarga: Double = 0.0,
    val total_outras_despesas: Double = 0.0,
    val media_km_litro: Double = 0.0,
    val media_arla: Double = 0.0,
    val motorista_nome: String = ""
)

// ===============================
// DESCARGA
// ===============================
@Serializable
data class SalvarDescargaRequest(

    val motorista_id: String,
    val viagem_id: Int,
    val data: String,
    val placa: String,
    val ordem_descarga: Int,
    val valor: String,
    val foto: String? = null
)

@Serializable
data class SalvarDescargaResponse(
    val status: String,
    val mensagem: String? = null,
    val descarga_id: Int? = null
)

// ===============================
// OUTRAS DESPESAS (Prioridade 2 - #6)
// ===============================
@Serializable
data class SalvarOutraDespesaRequest(
    val motorista_id: String,
    val viagem_id: Int,
    val tipo: String,
    val descricao: String? = null,
    val valor: String,
    val data: String,
    val local: String? = null,
    val foto_comprovante: String? = null
)

@Serializable
data class SalvarOutraDespesaResponse(
    val status: String,
    val mensagem: String? = null,
    val despesa_id: Int? = null
)

@Serializable
data class AtualizarOutraDespesaRequest(
    val despesa_id: Int,
    val motorista_id: String,
    val viagem_id: Int,
    val tipo: String,
    val descricao: String? = null,
    val valor: String,
    val data: String,
    val local: String? = null,
    val foto_comprovante: String? = null
)

@Serializable
data class AtualizarOutraDespesaResponse(
    val status: String,
    val mensagem: String? = null
)

@Serializable
data class ExcluirOutraDespesaRequest(
    val despesa_id: Int,
    val motorista_id: String
)

// ===============================
// CHECKLIST PRÉ-VIAGEM
// ===============================
@Serializable
data class SalvarChecklistPreRequest(
    val motorista_id: String,
    val viagem_id: Int,
    val data_checklist: String,
    val placa: String,
    val doc_cnh_valida: Int, val doc_crlv_veiculo: Int, val doc_antt_valida: Int, val doc_seguro_carga: Int, val doc_ordem_coleta: Int,
    val elet_farol_dianteiro: Int, val elet_farol_traseiro: Int, val elet_luz_freio: Int, val elet_seta_direita: Int, val elet_seta_esquerda: Int, val elet_luz_re: Int, val elet_painel_funcionando: Int,
    val pneu_calibragem_ok: Int, val pneu_estado_conservacao: Int, val pneu_estepe_ok: Int, val pneu_ferramentas_troca: Int,
    val fluido_oleo_motor: Int, val fluido_agua_radiador: Int, val fluido_fluido_freio: Int, val fluido_arla32: Int, val fluido_combustivel: Int,
    val seg_extintor_validade: Int, val seg_triangulo: Int, val seg_macaco_chave_roda: Int, val seg_cones_faixa: Int, val seg_epi_completo: Int,
    val carr_lonas_cordas: Int, val carr_portas_bau: Int, val carr_assoalho_estado: Int, val carr_travas_lacres: Int,
    val cab_bancos_cintos: Int, val cab_espelhos_retrovisores: Int, val cab_limpador_parabrisa: Int, val cab_ar_condicionado: Int, val cab_freio_estacionamento: Int,
    val observacoes: String? = null
)

@Serializable
data class SalvarChecklistPreResponse(
    val status: String,
    val mensagem: String? = null,
    val checklist_id: Int? = null
)

// ===============================
// CHECKLIST PÓS-VIAGEM
// ===============================
@Serializable
data class SalvarChecklistPosRequest(
    val motorista_id: String,
    val viagem_id: Int,
    val data_checklist: String,
    val placa: String,
    val avaria_carroceria: Int, val avaria_cabine: Int, val avaria_pneus: Int, val avaria_espelhos: Int, val avaria_farois: Int, val avaria_descricao: String? = null,
    val pos_nivel_oleo: Int, val pos_nivel_agua: Int, val pos_nivel_combustivel: Int, val pos_nivel_arla: Int,
    val limp_cabine_limpa: Int, val limp_carroceria_limpa: Int, val limp_bau_vazio: Int,
    val func_freios_ok: Int, val func_direcao_ok: Int, val func_suspensao_ok: Int, val func_motor_ruido: Int, val func_cambio_ok: Int,
    val pend_manutencao_urgente: Int, val pend_descricao_manutencao: String? = null, val pend_abastecimento_necessario: Int, val pend_troca_oleo_proxima: Int, val pend_km_atual: String? = null,
    val observacoes: String? = null
)

@Serializable
data class SalvarChecklistPosResponse(
    val status: String,
    val mensagem: String? = null,
    val checklist_id: Int? = null
)
