package api

import kotlinx.serialization.Serializable

// ========== VIAGENS LIST ==========
@Serializable
data class ViagemItem(
    val id: Int,
    val numerobd: String = "",
    val cte: String = "",
    val destino_nome: String = "",
    val data_viagem: String = "",
    val data_chegada: String? = null,
    val placa: String = "",
    val km_inicio: String = "",
    val pesocarga: String = "",
    val valorfrete: String = "",
    val finalizada: Boolean = false
)

@Serializable
data class ListarViagensResponse(
    val status: String,
    val mensagem: String? = null,
    val viagens: List<ViagemItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val total_pages: Int = 1
)

@Serializable
data class ListarViagensRequest(
    val motorista_id: String,
    val page: Int = 1
)

// ========== EXCLUIR VIAGEM ==========
@Serializable
data class ExcluirViagemRequest(

    val viagem_id: Int
)

@Serializable
data class ExcluirViagemResponse(
    val status: String,
    val mensagem: String? = null
)

// ========== DETALHE VIAGEM (para edição) ==========
@Serializable
data class ViagemDetalheRequest(

    val viagem_id: Int
)

@Serializable
data class ViagemDetalhe(
    val id: Int = 0,
    val numerobd: String = "",
    val numerobd2: String = "",
    val cte: String = "",
    val cte2: String = "",
    val destino_id: Int = 0,
    val destino_nome: String = "",
    val placa: String = "",
    val data_viagem: String = "",
    val data_chegada: String = "",
    val km_inicio: String = "",
    val km_chegada: String = "",
    val km_posto: String = "",
    val pesocarga: String = "",
    val pesocargaretorno: String = "",
    val valorfrete: String = "",
    val valorfreteretorno: String = "",
    val ordem_retorno: String = "",
    val cte_retorno: String = "",
    val descricao: String = "",
    val foto_painel_saida: String = "",
    val km_rota: String = "0",
    val media_rota: String = "0",
    val litros_rota: String = "0"
)

@Serializable
data class DestinoItem(
    val id: Int,
    val nome: String
)

@Serializable
data class EquipamentoItem(
    val id: Int,
    val placa: String
)

@Serializable
data class ViagemDetalheResponse(
    val status: String,
    val mensagem: String? = null,
    val viagem: ViagemDetalhe? = null,
    val destinos: List<DestinoItem> = emptyList(),
    val equipamentos: List<EquipamentoItem> = emptyList()
)

// ========== ATUALIZAR VIAGEM ==========
@Serializable
data class AtualizarViagemRequest(

    val viagem_id: Int,
    val numerobd: String = "",
    val numerobd2: String = "",
    val cte: String = "",
    val cte2: String = "",
    val destino_id: Int? = null,
    val placa: String = "",
    val data_viagem: String = "",
    val data_chegada: String = "",
    val km_inicio: String = "",
    val km_chegada: String = "",
    val km_posto: String = "",
    val pesocarga: String = "",
    val pesocargaretorno: String = "",
    val valorfrete: String = "",
    val valorfreteretorno: String = "",
    val ordem_retorno: String = "",
    val cte_retorno: String = "",
    val descricao: String = ""
)

@Serializable
data class AtualizarViagemResponse(
    val status: String,
    val mensagem: String? = null
)

// ========== DESPESAS ==========
@Serializable
data class DespesasRequest(

    val viagem_id: Int
)

@Serializable
data class AbastecimentoItem(
    val id: Int,
    val data: String,
    val posto: String = "",
    val tipo: String = "",
    val litros: Double = 0.0,
    val valor: Double = 0.0
)

@Serializable
data class ArlaItem(
    val id: Int,
    val data: String,
    val posto: String = "",
    val litros: Double = 0.0,
    val valor: Double = 0.0
)

@Serializable
data class DescargaItem(
    val id: Int,
    val data: String,
    val valor: Double = 0.0
)

@Serializable
data class OutraDespesaItem(
    val id: Int,
    val tipo: String = "",
    val descricao: String = "",
    val valor: Double = 0.0,
    val data: String = "",
    val local: String = ""
)

@Serializable
data class DespesasResponse(
    val status: String,
    val mensagem: String? = null,
    val viagem_aberta: Boolean = false,
    val abastecimentos: List<AbastecimentoItem> = emptyList(),
    val arla: List<ArlaItem> = emptyList(),
    val descargas: List<DescargaItem> = emptyList(),
    val outras_despesas: List<OutraDespesaItem> = emptyList()
)

// ========== RESUMO ==========
@Serializable
data class ResumoRequest(

    val viagem_id: Int
)

@Serializable
data class ResumoViagem(
    val motorista_nome: String = "",
    val destino_nome: String = "",
    val numerobd: String = "",
    val data_viagem: String = "",
    val data_chegada: String = "",
    val km_inicio: Double = 0.0,
    val km_chegada: Double = 0.0,
    val km_da_rota: Double = 0.0,
    val km_percorridos: Double = 0.0,
    val km_ultrapassados: Double = 0.0,
    val litros_diesel_caminhao: Double = 0.0,
    val litros_diesel_aparelho: Double = 0.0,
    val litros_arla: Double = 0.0,
    val litros_rota: Double = 0.0,
    val media_consumo: Double = 0.0,
    val media_rota: Double = 0.0,
    val media_arla: Double = 0.0,
    val media_aparelho: Double = 0.0,
    val soma_horas: Double = 0.0,
    val valor_diesel_caminhao: Double = 0.0,
    val valor_diesel_aparelho: Double = 0.0,
    val valor_arla: Double = 0.0,
    val valor_descarga: Double = 0.0,
    val comissao: Double = 0.0,
    val porcentagem_oleo: Double = 0.0,
    val total_despesas: Double = 0.0,
    val valor_frete: Double = 0.0,
    val valor_frete_retorno: Double = 0.0,
    val saldo_frete: Double = 0.0,
    val saldo_viagem: Double = 0.0,
    val descricao: String = "",
    val foto_painel_saida: String = "",
    val foto_painel_chegada: String = ""
)

@Serializable
data class ResumoResponse(
    val status: String,
    val mensagem: String? = null,
    val resumo: ResumoViagem? = null
)

// ========== EXCLUSÃO DE DESPESAS ==========
@Serializable
data class ExcluirDespesaRequest(

    val motorista_id: String,
    val id: Int
)

@Serializable
data class ExcluirDespesaResponse(
    val status: String,
    val mensagem: String? = null
)

// ========== ABASTECIMENTO - DETALHES E EDIÇÃO ==========
@Serializable
data class DetalheAbastecimentoRequest(

    val abastecimento_id: Int
)

@Serializable
data class DetalheAbastecimentoResponse(
    val status: String,
    val abastecimento: AbastecimentoDetalhe? = null,
    val mensagem: String? = null
)

@Serializable
data class AbastecimentoDetalhe(
    val id: Int,
    val data: String,
    val tipo: String,
    val equipamento_id: Int,
    val valor: Double,
    val litros: Double,
    val posto: String,
    val km_posto: String,
    val foto: String?
)

// ========== BUSCAR ABASTECIMENTO PARA EDIÇÃO (NOVO) ==========
@Serializable
data class BuscarAbastecimentoRequest(
    val abastecimento_id: Int,
    val motorista_id: String
)

@Serializable
data class BuscarAbastecimentoResponse(
    val status: String,
    val abastecimento: AbastecimentoCompleto? = null,
    val placas: List<String> = emptyList(),
    val mensagem: String? = null
)

@Serializable
data class AbastecimentoCompleto(
    val id: Int,
    val viagem_id: Int,
    val destino: String,
    val data_viagem: String,
    val placa: String,
    val data_abastecimento: String,
    val nome_posto: String,
    val km_posto: String,
    val tipo_combustivel: String,
    val horas: String,
    val litros_abastecidos: String,
    val valor_litro: String,
    val valor_total: String,
    val forma_pagamento: String,
    val foto_cupom: String? = null,
    val foto_marcador: String? = null
)

// ========== ATUALIZAR ABASTECIMENTO (CORRIGIDO) ==========
@Serializable
data class AtualizarAbastecimentoRequest(
    val abastecimento_id: Int,
    val motorista_id: String,
    val viagem_id: Int,
    val placa: String,
    val data_abastecimento: String,
    val nome_posto: String,
    val km_posto: String,
    val tipo_combustivel: String,
    val horas: String,
    val litros_abastecidos: String,
    val valor_litro: String,
    val valor_total: String,
    val forma_pagamento: String,
    val foto_cupom: String? = null,
    val foto_marcador: String? = null
)

@Serializable
data class AtualizarAbastecimentoResponse(
    val status: String,
    val mensagem: String?
)

// ========== ARLA - DETALHES E EDIÇÃO ==========
@Serializable
data class DetalheArlaRequest(

    val arla_id: Int
)

@Serializable
data class DetalheArlaResponse(
    val status: String,
    val arla: ArlaDetalhe? = null,
    val mensagem: String? = null
)

@Serializable
data class ArlaDetalhe(
    val id: Int,
    val data: String,
    val valor: Double,
    val litros: Double,
    val posto: String,
    val km_posto: String,
    val foto: String?
)


// ========== DESCARGA - DETALHES E EDIÇÃO ==========
@Serializable
data class DetalheDescargaRequest(

    val descarga_id: Int
)

@Serializable
data class DetalheDescargaResponse(
    val status: String,
    val descarga: DescargaDetalhe? = null,
    val mensagem: String? = null
)

@Serializable
data class DescargaDetalhe(
    val id: Int,
    val data: String,
    val placa: String,
    val ordem_descarga: Int,
    val valor: Double,
    val foto: String?
)




// ========== MANUTENÇÃO - DETALHES E EDIÇÃO ==========
@Serializable
data class DetalheManutencaoRequest(

    val manutencao_id: Int
)

@Serializable
data class DetalheManutencaoResponse(
    val status: String,
    val manutencao: ManutencaoDetalhe? = null,
    val mensagem: String? = null
)

@Serializable
data class ManutencaoDetalhe(
    val id: Int,
    val data_manutencao: String,
    val placa: String,
    val servico: String,
    val descricao_servico: String?,
    val local_manutencao: String?,
    val valor: Double,
    val km_troca_oleo: String?,
    val km_troca_pneu: String?,
    val pneus: String?,
    val tipos_pneu: String?,
    val foto_comprovante1: String?,
    val foto_comprovante2: String?
)




@Serializable
data class ExcluirDescargaRequest(
    val motorista_id: String,
    val descarga_id: Int
)
// ===============================
// ARLA - BUSCAR DADOS
// ===============================
@Serializable
data class BuscarArlaRequest(
    val arla_id: Int,
    val motorista_id: String
)

@Serializable
data class BuscarArlaResponse(
    val status: String,
    val arla: ArlaCompleto? = null,
    val mensagem: String? = null
)

@Serializable
data class ArlaCompleto(
    val id: Int,
    val viagem_id: Int,
    val destino: String,
    val data_viagem: String,
    val data_arla: String,
    val valor: String,
    val litros: String,
    val posto: String,
    val km_posto: String,
    val foto: String? = null
)

// ===============================
// ARLA - ATUALIZAR (SUBSTITUIR O EXISTENTE)
// ===============================
@Serializable
data class AtualizarArlaRequest(
    val arla_id: Int,
    val data: String,
    val valor: Double,
    val litros: Double,
    val posto: String,
    val km_posto: String,
    val foto: String? = null
)

@Serializable
data class AtualizarArlaResponse(
    val status: String,
    val mensagem: String? = null,
    val arla_id: Int? = null
)
// ===============================
// DESCARGA - BUSCAR DADOS
// ===============================
@Serializable
data class BuscarDescargaRequest(
    val descarga_id: Int,
    val motorista_id: String
)

@Serializable
data class BuscarDescargaResponse(
    val status: String,
    val descarga: DescargaCompleta? = null,
    val mensagem: String? = null
)

@Serializable
data class DescargaCompleta(
    val id: Int,
    val viagem_id: Int,
    val destino: String,
    val data_viagem: String,
    val data_descarga: String,
    val placa: String,
    val ordem_descarga: Int,
    val valor: String,
    val foto: String? = null
)

// ===============================
// DESCARGA - ATUALIZAR
// ===============================
@Serializable
data class AtualizarDescargaRequest(
    val descarga_id: Int,
    val motorista_id: String,
    val viagem_id: Int,
    val data: String,
    val placa: String,
    val ordem_descarga: Int,
    val valor: String,
    val foto: String? = null
)

@Serializable
data class AtualizarDescargaResponse(
    val status: String,
    val mensagem: String? = null,
    val descarga_id: Int? = null
)
@Serializable
data class BuscarManutencaoRequest(
    val manutencao_id: Int,
    val motorista_id: String
)

@Serializable
data class BuscarManutencaoResponse(
    val status: String,
    val manutencao: ManutencaoCompleta? = null,
    val mensagem: String? = null
)

@Serializable
data class ManutencaoCompleta(
    val id: Int,
    val viagem_id: Int,
    val data_manutencao: String,
    val placa: String,
    val servico: String,
    val descricao_servico: String? = null,
    val local_manutencao: String? = null,
    val valor: String,
    val km_troca_oleo: String? = null,
    val km_troca_pneu: String? = null,
    val pneus: String? = null,
    val tipos_pneu: String? = null,
    val foto_comprovante1: String? = null,
    val foto_comprovante2: String? = null
)

@Serializable
data class AtualizarManutencaoRequest(
    val manutencao_id: Int,
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
    val pneus: List<Int>? = null,
    val tipos_pneu: Map<Int, String>? = null,
    val foto_comprovante1: String? = null,
    val foto_comprovante2: String? = null
)

@Serializable
data class AtualizarManutencaoResponse(
    val status: String,
    val mensagem: String? = null,
    val manutencao_id: Int? = null
)

