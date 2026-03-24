package api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ===============================
// INTERFACE PARA SALVAR URL E TOKEN
// ===============================
interface ApiUrlStorage {
    fun saveApiUrl(url: String)
    fun getApiUrl(): String?
    fun saveToken(token: String)
    fun getToken(): String?
}

object ApiClient {

    private const val CENTRAL_URL = "https://lfsystem.com.br/api"
    private var baseUrl: String? = null
    private var storage: ApiUrlStorage? = null
    private var authToken: String? = null

    private val client = createHttpClient()

    // ===============================
    // CONFIGURAR STORAGE
    // ===============================
    fun setStorage(storage: ApiUrlStorage) {
        this.storage = storage
        // Recupera URL e token salvos ao inicializar
        baseUrl = storage.getApiUrl()
        authToken = storage.getToken()
    }

    // ===============================
    // TOKEN DE AUTENTICAÇÃO
    // ===============================
    fun setToken(token: String) {
        authToken = token
        storage?.saveToken(token)
    }

    fun getToken(): String? = authToken

    // ===============================
    // LIMPAR CONFIGURAÇÃO (LOGOUT)
    // ===============================
    fun limparConfiguracao() {
        baseUrl = null
        authToken = null
        storage?.saveApiUrl("")
        storage?.saveToken("")
    }

    // ===============================
    // BUSCAR EMPRESAS (AUTOCOMPLETE)
    // ===============================
    suspend fun buscarEmpresas(termo: String): BuscarEmpresasResponse {
        return client.get("$CENTRAL_URL/buscar_empresas.php") {
            parameter("termo", termo)
        }.body()
    }

    // ===============================
    // CONFIGURAR URL DA API
    // ===============================
    suspend fun configurarApiUrl(cnpj: String) {
        try {
            val response: ApiUrlResponse = client.get("$CENTRAL_URL/get_api_url.php") {
                parameter("cnpj", cnpj)
            }.body()

            if (response.status == "ok" && response.url_api != null) {
                baseUrl = response.url_api
                storage?.saveApiUrl(response.url_api)  // Salva persistentemente
            } else {
                throw Exception(response.mensagem ?: "Erro ao buscar URL da API")
            }
        } catch (e: Exception) {
            throw Exception("Erro ao configurar URL da API: ${e.message}")
        }
    }

    private fun getBaseUrl(): String {
        return baseUrl ?: throw Exception("URL da API não configurada. Execute configurarApiUrl() primeiro.")
    }

    /**
     * Adiciona header de Authorization com Bearer token em todas as requests autenticadas.
     * Se o token não estiver disponível (ex: primeiro login), não adiciona header.
     */
    private fun HttpRequestBuilder.withAuth() {
        authToken?.let { token ->
            if (token.isNotEmpty()) {
                header("Authorization", "Bearer $token")
            }
        }
    }

    // ===============================
    // LOGIN
    // ===============================
    suspend fun login(
        cnpj: String,
        usuario: String,
        senha: String
    ): LoginResponse {
        // SEMPRE reconfigura a URL da API baseada no CNPJ
        // Isso garante que cada CNPJ acesse apenas sua própria API
        configurarApiUrl(cnpj)

        return client.post("${getBaseUrl()}/auth/login.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(LoginRequestSimples(usuario, senha))
        }.body()
    }

    // ===============================
    // SINCRONIZAR DADOS (Destinos e Equipamentos)
    // ===============================
    suspend fun syncDados(): SyncDadosResponse {
        return client.post("${getBaseUrl()}/sync.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(emptyMap<String, String>())
        }.body()
    }

    // ===============================
    // BUSCAR DESTINOS
    // ===============================
    suspend fun getDestinos(): DestinosResponse {
        return client.get("${getBaseUrl()}/destinos.php").body()
    }

    // ===============================
    // BUSCAR EQUIPAMENTOS
    // ===============================
    suspend fun getEquipamentos(): EquipamentosResponse {
        return client.get("${getBaseUrl()}/equipamentos.php").body()
    }

    // ===============================
    // INICIAR VIAGEM
    // ===============================
    suspend fun iniciarViagem(request: IniciarViagemRequest): IniciarViagemResponse {
        return client.post("${getBaseUrl()}/viagem/inserir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // LISTAR VIAGENS
    // ===============================
    suspend fun listarViagens(request: ListarViagensRequest): ListarViagensResponse {
        return client.post("${getBaseUrl()}/viagem/listar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // DETALHES DA VIAGEM
    // ===============================
    suspend fun detalheViagem(request: ViagemDetalheRequest): ViagemDetalheResponse {
        return client.post("${getBaseUrl()}/viagem/detalhe.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // EDITAR VIAGEM
    // ===============================
    suspend fun atualizarViagem(request: AtualizarViagemRequest): AtualizarViagemResponse {
        return client.post("${getBaseUrl()}/viagem/atualizar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // EXCLUIR VIAGEM
    // ===============================
    suspend fun excluirViagem(request: ExcluirViagemRequest): ExcluirViagemResponse {
        return client.post("${getBaseUrl()}/viagem/excluir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // INSERIR VIAGEM
    // ===============================
    suspend fun inserirViagem(viagem: ViagemRequest): ViagemResponse {
        return client.post("${getBaseUrl()}/viagem/inserir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(viagem)
        }.body()
    }

    // ===============================
    // FINALIZAR VIAGEM
    // ===============================
    suspend fun finalizarViagem(request: FinalizarViagemRequest): FinalizarViagemResponse {
        return client.post("${getBaseUrl()}/viagem/finalizar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // DESPESAS DA VIAGEM
    // ===============================
    suspend fun despesasViagem(request: DespesasRequest): DespesasResponse {
        return client.post("${getBaseUrl()}/viagem/despesas.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // RESUMO DA VIAGEM
    // ===============================
    suspend fun resumoViagem(request: ResumoRequest): ResumoResponse {
        return client.post("${getBaseUrl()}/viagem/resumo.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // ABASTECIMENTO
    // ===============================
    suspend fun abastecimentoDados(request: AbastecimentoDadosRequest): AbastecimentoDadosResponse {
        return client.post("${getBaseUrl()}/abastecimento/dados.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun salvarAbastecimento(request: SalvarAbastecimentoRequest): SalvarAbastecimentoResponse {
        return client.post("${getBaseUrl()}/abastecimento/salvar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun salvarCombustivel(request: SalvarCombustivelRequest): SalvarCombustivelResponse {
        return client.post("${getBaseUrl()}/abastecimento/salvar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun detalheAbastecimento(request: DetalheAbastecimentoRequest): DetalheAbastecimentoResponse {
        return client.post("${getBaseUrl()}/abastecimento/detalhe.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun buscarAbastecimento(request: BuscarAbastecimentoRequest): BuscarAbastecimentoResponse {
        return client.post("${getBaseUrl()}/abastecimento/editar_abastecimento_dados.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun atualizarAbastecimento(request: AtualizarAbastecimentoRequest): AtualizarAbastecimentoResponse {
        return client.post("${getBaseUrl()}/abastecimento/atualizar_abastecimento.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun excluirAbastecimento(request: ExcluirDespesaRequest): ExcluirDespesaResponse {
        return client.post("${getBaseUrl()}/abastecimento/excluir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // ARLA
    // ===============================
    suspend fun arlaDados(request: ArlaDadosRequest): ArlaDadosResponse {
        return client.post("${getBaseUrl()}/arla/dados.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun salvarArla(request: SalvarArlaRequest): SalvarArlaResponse {
        return client.post("${getBaseUrl()}/arla/salvar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun detalheArla(request: DetalheArlaRequest): DetalheArlaResponse {
        return client.post("${getBaseUrl()}/arla/detalhe.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }



    suspend fun excluirArla(request: ExcluirDespesaRequest): ExcluirDespesaResponse {
        return client.post("${getBaseUrl()}/arla/excluir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }


    // Após a linha ~250 (na seção de ARLA)

    suspend fun buscarArla(request: BuscarArlaRequest): BuscarArlaResponse {
        return client.post("${getBaseUrl()}/arla/buscar_arla_dados.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // SUBSTITUIR a função atualizarArla existente por:
    suspend fun atualizarArla(request: AtualizarArlaRequest): AtualizarArlaResponse {
        return client.post("${getBaseUrl()}/arla/atualizar_arla.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }
    // ===============================
    // DESCARGA
    // ===============================
    suspend fun salvarDescarga(request: SalvarDescargaRequest): SalvarDescargaResponse {
        return client.post("${getBaseUrl()}/descarga/salvar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun detalheDescarga(request: DetalheDescargaRequest): DetalheDescargaResponse {
        return client.post("${getBaseUrl()}/descarga/detalhe.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }


    suspend fun excluirDescarga(request: ExcluirDescargaRequest): ExcluirDespesaResponse {
        return client.post("${getBaseUrl()}/descarga/excluir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun buscarDescarga(request: BuscarDescargaRequest): BuscarDescargaResponse {
        return client.post("${getBaseUrl()}/descarga/buscar_descarga_dados.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun atualizarDescarga(request: AtualizarDescargaRequest): AtualizarDescargaResponse {
        return client.post("${getBaseUrl()}/descarga/atualizar_descarga.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // OUTRAS DESPESAS
    // ===============================
    // ===============================
    // OUTRAS DESPESAS
    // ===============================
    suspend fun salvarOutraDespesa(request: SalvarOutraDespesaRequest): SalvarOutraDespesaResponse {
        return client.post("${getBaseUrl()}/despesa/salvar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun atualizarOutraDespesa(request: AtualizarOutraDespesaRequest): AtualizarOutraDespesaResponse {
        return client.post("${getBaseUrl()}/despesa/atualizar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun excluirOutraDespesa(request: ExcluirOutraDespesaRequest): ExcluirDespesaResponse {
        return client.post("${getBaseUrl()}/despesa/excluir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // MANUTENÇÃO
    // ===============================
    suspend fun salvarManutencao(request: SalvarManutencaoRequest): SalvarManutencaoResponse {
        return client.post("${getBaseUrl()}/manutencao/salvar.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun getManutencoes(
        motoristaId: String,
        page: Int = 1
    ): ManutencoesDadosResponse {
        return client.post("${getBaseUrl()}/manutencao/dados.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(mapOf(
                "motorista_id" to motoristaId,
                "page" to page.toString()  // ← CONVERTER PARA STRING
            ))
        }.body()
    }

    suspend fun detalheManutencao(request: DetalheManutencaoRequest): DetalheManutencaoResponse {
        return client.post("${getBaseUrl()}/manutencao/detalhe.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }


    suspend fun excluirManutencao(request: ExcluirDespesaRequest): ExcluirDespesaResponse {
        return client.post("${getBaseUrl()}/manutencao/excluir.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }
    suspend fun buscarManutencao(request: BuscarManutencaoRequest): BuscarManutencaoResponse {
        return client.post("${getBaseUrl()}/manutencao/buscar_manutencao_dados.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    suspend fun atualizarManutencao(request: AtualizarManutencaoRequest): AtualizarManutencaoResponse {
        return client.post("${getBaseUrl()}/manutencao/atualizar_manutencao.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }
    // ===============================
    // RELATÓRIO DE VIAGENS
    // ===============================
    suspend fun getRelatorioViagens(motoristaId: String, mes: String, ano: String): RelatorioViagensResponse {
        return client.post("${getBaseUrl()}/relatorio/viagens.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(mapOf(
                "motorista_id" to motoristaId,
                "mes" to mes,
                "ano" to ano
            ))
        }.body()
    }

    // ===============================
    // CHECKLIST PRÉ-VIAGEM
    // ===============================
    suspend fun salvarChecklistPre(request: SalvarChecklistPreRequest): SalvarChecklistPreResponse {
        return client.post("${getBaseUrl()}/checklist/salvar_pre.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }

    // ===============================
    // CHECKLIST PÓS-VIAGEM
    // ===============================
    suspend fun salvarChecklistPos(request: SalvarChecklistPosRequest): SalvarChecklistPosResponse {
        return client.post("${getBaseUrl()}/checklist/salvar_pos.php") {
            contentType(ContentType.Application.Json)
            withAuth()
            setBody(request)
        }.body()
    }
}

// ===============================
// REQUEST/RESPONSE CLASSES
// ===============================

@Serializable
data class LoginRequest(
    val cnpj: String,
    val usuario: String,
    val senha: String
)

@Serializable
data class LoginResponse(
    val status: String,
    val motorista_id: String? = null,
    val nome: String? = null,
    val token: String? = null,
    val mensagem: String? = null
)

@Serializable
data class SyncDadosResponse(
    val status: String,
    val destinos: List<DestinoDto> = emptyList(),
    val equipamentos: List<EquipamentoDto> = emptyList(),
    val mensagem: String? = null
)

@Serializable
data class DestinosResponse(
    val status: String,
    val destinos: List<DestinoDto> = emptyList(),
    val mensagem: String? = null
)

@Serializable
data class EquipamentosResponse(
    val status: String,
    val equipamentos: List<EquipamentoDto> = emptyList(),
    val mensagem: String? = null
)

@Serializable
data class DestinoDto(
    val id: String,
    val nome: String
)

@Serializable
data class EquipamentoDto(
    val id: String,
    val placa: String
)

@Serializable
data class ViagemRequest(
    val motorista_id: String,
    val numerobd: String,
    val cte: String,
    val numerobd2: String? = null,
    val cte2: String? = null,
    val destino_id: Int,
    val data_viagem: String,
    val km_inicio: String,
    val placa: String,
    val pesocarga: String,
    val valorfrete: String? = null,
    val foto_painel_saida: String? = null
)

@Serializable
data class ViagemResponse(
    val status: String,
    val mensagem: String? = null,
    val viagem_id: Int? = null
)

// ===============================
// INICIAR VIAGEM
// ===============================
@Serializable
data class IniciarViagemRequest(
    val motorista_id: String,
    val data: String,
    val destino_id: Int,
    val equipamento_id: Int,
    val km_saida: String,
    val peso_carga: String,
    val valor_frete: String,
    val ordem: String,
    val cte: String,
    val observacao: String,
    val foto_painel_saida_base64: String?
)

@Serializable
data class IniciarViagemResponse(
    val status: String,
    val mensagem: String? = null,
    val viagem_id: Int? = null
)

// ===============================
// COMBUSTÍVEL
// ===============================
@Serializable
data class SalvarCombustivelRequest(
    val motorista_id: String,
    val viagem_id: Int,
    val data: String,
    val valor: String,
    val litros: String,
    val posto: String,
    val km_posto: String,
    val foto_base64: String?
)

@Serializable
data class SalvarCombustivelResponse(
    val status: String,
    val mensagem: String? = null
)

@Serializable
data class ApiUrlResponse(
    val status: String,
    val url_api: String? = null,
    val mensagem: String? = null
)

@Serializable
data class LoginRequestSimples(
    val usuario: String,
    val senha: String
)

@Serializable
data class BuscarEmpresasResponse(
    val status: String,
    val empresas: List<EmpresaDto> = emptyList(),
    val mensagem: String? = null
)

@Serializable
data class EmpresaDto(
    val cnpj: String,
    val nome_empresa: String
)

