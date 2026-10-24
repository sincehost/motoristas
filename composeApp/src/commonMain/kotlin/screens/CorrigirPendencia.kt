package screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import br.com.lfsystem.app.database.Abastecimento
import br.com.lfsystem.app.database.Arla
import br.com.lfsystem.app.database.Descarga
import br.com.lfsystem.app.database.FinalizacaoViagem
import br.com.lfsystem.app.database.Frete
import br.com.lfsystem.app.database.Manutencao
import br.com.lfsystem.app.database.OutraDespesa
import br.com.lfsystem.app.database.Viagem
import database.AppRepository
import ui.AppColors
import ui.GradientTopBar
import util.dataApiParaBR
import util.dataBRParaApi
import util.formatarKmBR
import util.formatarKmExibicao
import util.formatarKmInput
import util.kmParaDouble
import util.kmReferenciaDoErro
import util.lerKmDaFoto
import util.mascaraCentavos
import util.mascaraData
import util.mascaraMilhar
import util.normalizarKmParaEnvio

// ===============================
// MODELO
// ===============================

enum class TipoCampoCorrecao { KM, DINHEIRO, MILHAR, DATA, TEXTO }

/** Um campo editável. [valor] vem no formato gravado no banco local. */
data class CampoCorrecao(
    val chave: String,
    val rotulo: String,
    val tipo: TipoCampoCorrecao,
    val valor: String,
    val obrigatorio: Boolean = true
)

/**
 * Tudo que a tela de correção precisa pra um lançamento recusado pelo servidor.
 *
 * @param chaveKm campo de KM que o erro aponta — recebe a conferência com o
 *   valor de referência da mensagem e a leitura automática da [fotoPainel].
 * @param salvar recebe os valores já no formato de gravação (KM "X.Y", data
 *   "yyyy-MM-dd", dinheiro "1.234,56") e limpa o erro do lançamento.
 * @param marcarComoEnviado quando o servidor diz que o registro já existe
 *   (foi enviado antes e a resposta se perdeu) — tira da fila sem perder nada.
 */
data class CorrecaoLancamento(
    val tipo: String,
    val descricao: String,
    val erro: String,
    val campos: List<CampoCorrecao>,
    val chaveKm: String? = null,
    val fotoPainel: String? = null,
    val temDecimal: Boolean = true,
    val salvar: (Map<String, String>) -> Unit,
    val marcarComoEnviado: (() -> Unit)? = null
)

// ===============================
// MONTAGEM POR TIPO — só pra lançamento recusado pelo servidor (erro de
// dado). Falha passageira (sem internet etc.) não tem o que corrigir.
// ===============================

private fun erroCorrigivel(erro: String?): String? = erro?.takeUnless { util.isErroTransitorio(it) }

fun correcaoViagem(repo: AppRepository, v: Viagem): CorrecaoLancamento? {
    val erro = erroCorrigivel(v.ultimo_erro) ?: return null
    return CorrecaoLancamento(
        tipo = "Viagem",
        descricao = "${v.placa.ifBlank { "Sem placa" }} — ${v.destino_nome}",
        erro = erro,
        campos = listOf(
            CampoCorrecao("km", "KM de início", TipoCampoCorrecao.KM, v.km_inicio),
            CampoCorrecao("data", "Data da viagem", TipoCampoCorrecao.DATA, v.data_viagem),
            CampoCorrecao("numerobd", "Ordem de frete", TipoCampoCorrecao.TEXTO, v.numerobd),
            CampoCorrecao("cte", "Nº do CT-e", TipoCampoCorrecao.TEXTO, v.cte),
            CampoCorrecao("peso", "Peso da carga (kg)", TipoCampoCorrecao.MILHAR, v.pesocarga, obrigatorio = false)
        ),
        chaveKm = "km",
        fotoPainel = v.foto_painel_saida,
        temDecimal = repo.hodometroTemDecimal(v.placa),
        salvar = { m -> repo.corrigirViagem(v.id, m.getValue("numerobd"), m.getValue("cte"), m.getValue("data"), m.getValue("km"), m.getValue("peso")) }
    )
}

fun correcaoFinalizacao(repo: AppRepository, f: FinalizacaoViagem): CorrecaoLancamento? {
    val erro = erroCorrigivel(f.ultimo_erro) ?: return null
    return CorrecaoLancamento(
        tipo = "Finalização de viagem",
        descricao = "Chegada em ${dataApiParaBR(f.data_chegada)}",
        erro = erro,
        campos = listOf(
            CampoCorrecao("km", "KM de chegada", TipoCampoCorrecao.KM, f.km_chegada),
            CampoCorrecao("data", "Data de chegada", TipoCampoCorrecao.DATA, f.data_chegada)
        ),
        chaveKm = "km",
        fotoPainel = f.foto_painel_chegada,
        salvar = { m -> repo.corrigirFinalizacao(f.id, m.getValue("data"), m.getValue("km")) }
    )
}

fun correcaoAbastecimento(repo: AppRepository, a: Abastecimento): CorrecaoLancamento? {
    val erro = erroCorrigivel(a.ultimo_erro) ?: return null
    return CorrecaoLancamento(
        tipo = "Abastecimento",
        descricao = "${a.posto.ifBlank { "Sem posto" }} — R$ ${a.valor}",
        erro = erro,
        campos = listOf(
            CampoCorrecao("km", "KM no posto", TipoCampoCorrecao.KM, a.km_posto),
            CampoCorrecao("data", "Data", TipoCampoCorrecao.DATA, a.data_),
            CampoCorrecao("posto", "Nome do posto", TipoCampoCorrecao.TEXTO, a.posto),
            CampoCorrecao("litros", "Litros", TipoCampoCorrecao.DINHEIRO, a.litros),
            CampoCorrecao("valor", "Valor total (R$)", TipoCampoCorrecao.DINHEIRO, a.valor),
            CampoCorrecao("valor_litro", "Valor do litro (R$)", TipoCampoCorrecao.DINHEIRO, a.valor_litro)
        ),
        chaveKm = "km",
        fotoPainel = a.foto_marcador,
        salvar = { m ->
            repo.corrigirAbastecimento(a.id, m.getValue("data"), m.getValue("posto"), m.getValue("km"),
                m.getValue("litros"), m.getValue("valor_litro"), m.getValue("valor"))
        },
        marcarComoEnviado = { repo.marcarAbastecimentoSincronizado(a.id) }
    )
}

fun correcaoArla(repo: AppRepository, ar: Arla): CorrecaoLancamento? {
    val erro = erroCorrigivel(ar.ultimo_erro) ?: return null
    return CorrecaoLancamento(
        tipo = "ARLA 32",
        descricao = "${ar.posto.ifBlank { "Sem posto" }} — R$ ${ar.valor}",
        erro = erro,
        campos = listOf(
            CampoCorrecao("km", "KM no posto", TipoCampoCorrecao.KM, ar.km_posto),
            CampoCorrecao("data", "Data", TipoCampoCorrecao.DATA, ar.data_),
            CampoCorrecao("posto", "Nome do posto", TipoCampoCorrecao.TEXTO, ar.posto),
            CampoCorrecao("litros", "Litros", TipoCampoCorrecao.DINHEIRO, ar.litros),
            CampoCorrecao("valor", "Valor total (R$)", TipoCampoCorrecao.DINHEIRO, ar.valor)
        ),
        chaveKm = "km",
        salvar = { m -> repo.corrigirArla(ar.id, m.getValue("data"), m.getValue("posto"), m.getValue("km"), m.getValue("litros"), m.getValue("valor")) },
        marcarComoEnviado = { repo.marcarArlaSincronizada(ar.id) }
    )
}

fun correcaoManutencao(repo: AppRepository, mt: Manutencao): CorrecaoLancamento? {
    val erro = erroCorrigivel(mt.ultimo_erro) ?: return null
    val campos = buildList {
        add(CampoCorrecao("data", "Data", TipoCampoCorrecao.DATA, mt.data_manutencao))
        add(CampoCorrecao("valor", "Valor (R$)", TipoCampoCorrecao.DINHEIRO, mt.valor))
        if (!mt.km_troca_oleo.isNullOrBlank()) add(CampoCorrecao("km_oleo", "KM da troca de óleo", TipoCampoCorrecao.KM, mt.km_troca_oleo!!))
        if (!mt.km_troca_pneu.isNullOrBlank()) add(CampoCorrecao("km_pneu", "KM da troca de pneu", TipoCampoCorrecao.KM, mt.km_troca_pneu!!))
    }
    val chaveKm = when {
        erro.contains("pneu", ignoreCase = true) && campos.any { it.chave == "km_pneu" } -> "km_pneu"
        erro.contains("óleo", ignoreCase = true) && campos.any { it.chave == "km_oleo" } -> "km_oleo"
        else -> null
    }
    return CorrecaoLancamento(
        tipo = "Manutenção",
        descricao = "${mt.placa.ifBlank { "Sem placa" }} — ${mt.servico}",
        erro = erro,
        campos = campos,
        chaveKm = chaveKm,
        temDecimal = repo.hodometroTemDecimal(mt.placa),
        salvar = { m -> repo.corrigirManutencao(mt.id, m.getValue("data"), m.getValue("valor"), m["km_oleo"], m["km_pneu"]) },
        marcarComoEnviado = { repo.marcarManutencaoSincronizada(mt.id) }
    )
}

fun correcaoDescarga(repo: AppRepository, d: Descarga): CorrecaoLancamento? {
    val erro = erroCorrigivel(d.ultimo_erro) ?: return null
    return CorrecaoLancamento(
        tipo = "Descarga",
        descricao = "${d.placa.ifBlank { "Sem placa" }} — R$ ${d.valor}",
        erro = erro,
        campos = listOf(
            CampoCorrecao("data", "Data", TipoCampoCorrecao.DATA, d.data_),
            CampoCorrecao("valor", "Valor (R$)", TipoCampoCorrecao.DINHEIRO, d.valor)
        ),
        salvar = { m -> repo.corrigirDescarga(d.id, m.getValue("data"), m.getValue("valor")) },
        marcarComoEnviado = { repo.marcarDescargaSincronizada(d.id) }
    )
}

fun correcaoOutraDespesa(repo: AppRepository, o: OutraDespesa): CorrecaoLancamento? {
    val erro = erroCorrigivel(o.ultimo_erro) ?: return null
    return CorrecaoLancamento(
        tipo = "Outra despesa",
        descricao = "${o.tipo} — R$ ${o.valor}",
        erro = erro,
        campos = listOf(
            CampoCorrecao("data", "Data", TipoCampoCorrecao.DATA, o.data_),
            CampoCorrecao("valor", "Valor (R$)", TipoCampoCorrecao.DINHEIRO, o.valor),
            CampoCorrecao("descricao", "Descrição", TipoCampoCorrecao.TEXTO, o.descricao ?: "", obrigatorio = false)
        ),
        salvar = { m -> repo.corrigirOutraDespesa(o.id, m.getValue("data"), m.getValue("valor"), m.getValue("descricao").ifBlank { null }) },
        marcarComoEnviado = { repo.marcarOutraDespesaSincronizada(o.id) }
    )
}

fun correcaoFrete(repo: AppRepository, f: Frete): CorrecaoLancamento? {
    val erro = erroCorrigivel(f.ultimo_erro) ?: return null
    return CorrecaoLancamento(
        tipo = "Frete",
        descricao = "${f.tipo} — R$ ${f.valor}",
        erro = erro,
        campos = listOf(
            CampoCorrecao("numero", "Número / identificação", TipoCampoCorrecao.TEXTO, f.numero_identificacao, obrigatorio = false),
            CampoCorrecao("valor", "Valor (R$)", TipoCampoCorrecao.DINHEIRO, f.valor)
        ),
        salvar = { m -> repo.corrigirFrete(f.id, m.getValue("numero"), m.getValue("valor")) },
        marcarComoEnviado = { repo.marcarFreteSincronizado(f.id) }
    )
}

// ===============================
// TELA
// ===============================

private fun paraEdicao(campo: CampoCorrecao): String = when (campo.tipo) {
    TipoCampoCorrecao.DATA -> dataApiParaBR(campo.valor)
    else -> campo.valor
}

private fun aplicarMascara(tipo: TipoCampoCorrecao, texto: String): String = when (tipo) {
    TipoCampoCorrecao.KM -> formatarKmInput(texto)
    TipoCampoCorrecao.DINHEIRO -> mascaraCentavos(texto)
    TipoCampoCorrecao.MILHAR -> mascaraMilhar(texto)
    TipoCampoCorrecao.DATA -> mascaraData(texto)
    TipoCampoCorrecao.TEXTO -> texto
}

/**
 * Tela cheia (por cima da lista de Pendências, mesmo esquema do
 * AppAlertDialog — Dialog/Popup não aparece no iPhone dentro da navegação).
 *
 * Correção automática: se o erro é de KM e o lançamento tem foto do painel,
 * relê a foto (mesma leitura do scanner) usando o valor que o servidor
 * informou como referência, e já preenche o campo pro motorista conferir.
 */
@Composable
fun CorrigirLancamentoScreen(
    correcao: CorrecaoLancamento,
    onSalvo: (mensagem: String) -> Unit,
    onFechar: () -> Unit
) {
    val valores = remember {
        mutableStateMapOf<String, TextFieldValue>().apply {
            correcao.campos.forEach { c ->
                val t = paraEdicao(c)
                put(c.chave, TextFieldValue(t, TextRange(t.length)))
            }
        }
    }
    var erroValidacao by remember { mutableStateOf<String?>(null) }
    var lendoFoto by remember { mutableStateOf(false) }
    var kmLidoDaFoto by remember { mutableStateOf<Double?>(null) }

    val referenciaKm = remember { if (correcao.chaveKm != null) kmReferenciaDoErro(correcao.erro) else null }
    val jaExisteNoServidor = util.isErroRegistroJaExiste(correcao.erro) && correcao.marcarComoEnviado != null
    val viagemEncerrada = correcao.erro.contains("já foi finalizada e não aceita", ignoreCase = true)
    // Viagem que o servidor diz já ter, mas que o app não achou aberta (já foi
    // finalizada lá, ou com outra placa/data). Editar não resolve.
    val viagemJaCadastrada = correcao.erro.contains("já foi cadastrada", ignoreCase = true)
    val fotoBitmap = remember(correcao.fotoPainel) {
        correcao.fotoPainel?.takeIf { it.isNotBlank() }?.let { util.decodificarFotoBase64(it) }
    }

    PlatformBackHandler(enabled = true, onBack = onFechar)

    LaunchedEffect(Unit) {
        val chave = correcao.chaveKm ?: return@LaunchedEffect
        val foto = correcao.fotoPainel?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (referenciaKm == null) return@LaunchedEffect
        lendoFoto = true
        val km = try { lerKmDaFoto(foto, referenciaKm, 5000.0, correcao.temDecimal) } catch (_: Exception) { null }
        lendoFoto = false
        if (km != null) {
            kmLidoDaFoto = km
            val texto = if (correcao.temDecimal) formatarKmExibicao(km) else km.toLong().toString()
            valores[chave] = TextFieldValue(texto, TextRange(texto.length))
        }
    }

    fun salvar() {
        erroValidacao = null
        val gravar = mutableMapOf<String, String>()
        for (campo in correcao.campos) {
            val texto = valores[campo.chave]?.text?.trim().orEmpty()
            if (texto.isBlank()) {
                if (campo.obrigatorio) { erroValidacao = "Preencha: ${campo.rotulo}"; return }
                gravar[campo.chave] = ""
                continue
            }
            gravar[campo.chave] = when (campo.tipo) {
                TipoCampoCorrecao.KM -> normalizarKmParaEnvio(texto)
                TipoCampoCorrecao.DATA -> dataBRParaApi(texto) ?: run { erroValidacao = "${campo.rotulo}: data inválida (use DD/MM/AAAA)"; return }
                else -> texto
            }
        }
        val chave = correcao.chaveKm
        if (chave != null && referenciaKm != null) {
            val km = kmParaDouble(gravar[chave])
            if (km < referenciaKm) {
                erroValidacao = "O KM precisa ser igual ou maior que ${formatarKmBR(referenciaKm, correcao.temDecimal)}, " +
                    "o valor informado pelo servidor. Confira no painel ou na foto."
                return
            }
        }
        try {
            correcao.salvar(gravar)
            AppEvents.emitir(AppEvent.DadosPendentesAlterados)
            onSalvo("Corrigido! O lançamento será reenviado agora.")
        } catch (e: Exception) {
            erroValidacao = "Não foi possível salvar a correção: ${util.mensagemErroAmigavel(e.message)}"
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().zIndex(1500f).background(AppColors.Background)
    ) {
        Column(Modifier.fillMaxSize()) {
            GradientTopBar(title = "Corrigir lançamento", onBackClick = onFechar)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cabeçalho + motivo da recusa
                Card(
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(correcao.tipo.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = AppColors.TextSecondary)
                        Text(correcao.descricao, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(ui.Lucide.TriangleAlert, null, tint = Color(0xFFC2410C), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("O servidor recusou: ${correcao.erro}", fontSize = 13.sp, color = Color(0xFF9A3412))
                        }
                    }
                }

                // Orientação conforme o tipo de erro
                when {
                    viagemEncerrada -> Aviso(
                        "Este lançamento não pode mais ser enviado pelo app: a viagem já foi encerrada no sistema. " +
                            "Avise o escritório (use \"Exportar e enviar\" na lista) e depois descarte.",
                        Color(0xFF9A3412), Color(0xFFFDEBDD)
                    )
                    viagemJaCadastrada -> Aviso(
                        "O servidor já tem uma viagem com esta ordem de frete, CT-e, placa e data. " +
                            "Editar não resolve: avise o escritório (use \"Exportar e enviar\" na lista) para conferir se é a mesma viagem.",
                        Color(0xFF9A3412), Color(0xFFFDEBDD)
                    )
                    // O servidor compara só alguns campos (valor, litros…), então
                    // "igual" pode ser um 2º lançamento de verdade com o mesmo
                    // valor — não dá pra prometer que remover não perde nada.
                    jaExisteNoServidor -> Aviso(
                        "O servidor já tem um lançamento igual a este nesta viagem. Normalmente é o mesmo, " +
                            "enviado de novo porque a confirmação não chegou ao celular — aí pode remover da lista.\n\n" +
                            "Se foram dois lançamentos de verdade com o mesmo valor (ex.: dois abastecimentos iguais), " +
                            "não remova: avise o escritório.",
                        AppColors.Primary, AppColors.Primary.copy(alpha = 0.08f)
                    )
                    referenciaKm != null -> Aviso(
                        "O KM precisa ser igual ou maior que ${formatarKmBR(referenciaKm, correcao.temDecimal)}. " +
                            "Confira o número na foto do painel abaixo e corrija.",
                        AppColors.Primary, AppColors.Primary.copy(alpha = 0.08f)
                    )
                    else -> Aviso("Confira os dados abaixo, corrija o que estiver errado e salve para reenviar.",
                        AppColors.Primary, AppColors.Primary.copy(alpha = 0.08f))
                }

                if (lendoFoto) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                        Text("Lendo o KM na foto do painel…", fontSize = 14.sp, color = AppColors.TextPrimary)
                    }
                }
                kmLidoDaFoto?.let { km ->
                    Aviso(
                        "Lemos ${formatarKmBR(km, correcao.temDecimal)} na foto do painel e já preenchemos. Confira e salve.",
                        Color(0xFF2E7D32), Color(0xFFE8F4E3)
                    )
                }

                if (fotoBitmap != null) {
                    Image(
                        bitmap = fotoBitmap,
                        contentDescription = "Foto do painel",
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black),
                        contentScale = ContentScale.Fit
                    )
                }

                if (!viagemEncerrada && !viagemJaCadastrada) {
                    correcao.campos.forEach { campo ->
                        val destaque = campo.chave == correcao.chaveKm
                        OutlinedTextField(
                            value = valores[campo.chave] ?: TextFieldValue(""),
                            onValueChange = { novo ->
                                val t = aplicarMascara(campo.tipo, novo.text)
                                valores[campo.chave] = if (campo.tipo == TipoCampoCorrecao.TEXTO) novo else TextFieldValue(t, TextRange(t.length))
                                erroValidacao = null
                            },
                            label = { Text(campo.rotulo + if (campo.obrigatorio) " *" else "") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = if (destaque) OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color(0xFFC2410C),
                                focusedBorderColor = Color(0xFFC2410C)
                            ) else ui.darkTextFieldColors(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = when (campo.tipo) {
                                    TipoCampoCorrecao.KM -> KeyboardType.Decimal
                                    TipoCampoCorrecao.TEXTO -> KeyboardType.Text
                                    else -> KeyboardType.Number
                                }
                            )
                        )
                    }
                }

                erroValidacao?.let { Text(it, fontSize = 13.sp, color = AppColors.Error) }

                if (jaExisteNoServidor) {
                    Button(
                        onClick = {
                            correcao.marcarComoEnviado?.invoke()
                            AppEvents.emitir(AppEvent.DadosPendentesAlterados)
                            onSalvo("Removido da lista: o lançamento já estava no servidor.")
                        },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                    ) { Text("Já está no servidor — remover da lista", fontWeight = FontWeight.Bold) }
                }
                if (!viagemEncerrada && !viagemJaCadastrada) {
                    Button(
                        onClick = { salvar() },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) { Text("Salvar e reenviar", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
                OutlinedButton(
                    onClick = onFechar,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (viagemEncerrada || viagemJaCadastrada) "Fechar" else "Cancelar") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Aviso(texto: String, corTexto: Color, corFundo: Color) {
    Text(
        texto,
        fontSize = 14.sp,
        color = corTexto,
        modifier = Modifier.fillMaxWidth().background(corFundo, RoundedCornerShape(10.dp)).padding(12.dp)
    )
}
