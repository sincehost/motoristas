package util

import kotlin.math.abs
import kotlin.math.round

/**
 * Utilitário único de quilometragem/hodômetro do sistema. Android, iOS, a
 * API e o banco (colunas DECIMAL(10,1)) usam todos o mesmo padrão de saída:
 * "XXXXXX.X" — ponto como separador decimal, sempre exatamente 1 casa
 * decimal, sem separador de milhar. Ex.: 115670.0, 115670.5, 118210.0.
 * Usar essas funções em QUALQUER campo de km (KM de Início, KM de Chegada,
 * km_posto, km_troca_oleo, km_troca_pneu etc.) — nunca tratar km como
 * string em comparações, nunca truncar a casa decimal.
 */

/**
 * Formata o texto digitado a cada keystroke (usar no onValueChange, junto
 * com TextFieldValue + cursor no final, como os demais campos numéricos
 * mascarados do app). Motorista digita o KM na ordem normal, igual aparece
 * no hodômetro (ex.: "62350" mostra "62350") — o "." só entra se o próprio
 * motorista digitar (ou ",", convertida automaticamente), pra quem realmente
 * precisa registrar fração de km. A casa decimal ausente NÃO é completada
 * aqui (isso ficaria "6235.0" pra quem digitou "62350" sem intenção de
 * decimal nenhuma) — só em normalizarKmParaEnvio(), na hora de salvar.
 *
 * Usada pelos campos de KM que ainda não sabem se o veículo tem casa
 * decimal no painel (posto, óleo, pneu, checklist etc.) — ver
 * formatarKmVeiculo() pros campos que já sabem (Início/Chegada de viagem).
 */
fun formatarKmInput(entrada: String): String {
    var texto = entrada.replace(',', '.').filter { it.isDigit() || it == '.' }

    // Mantém só o primeiro "." digitado — os demais são ignorados.
    val primeiroPonto = texto.indexOf('.')
    if (primeiroPonto != -1) {
        texto = texto.substring(0, primeiroPonto + 1) +
                texto.substring(primeiroPonto + 1).replace(".", "")
    }
    if (texto.isEmpty()) return ""

    val temPonto = texto.contains('.')
    val partes = texto.split(".", limit = 2)

    // 8 dígitos inteiros cobre o range inteiro de um DECIMAL(10,1) (até
    // 99999999.9) sem truncar valor real no round-trip.
    var inteiro = partes[0].take(8).trimStart('0')
    if (inteiro.isEmpty()) inteiro = "0"

    return if (temPonto) "$inteiro.${partes.getOrElse(1) { "" }.take(1)}" else inteiro
}

/**
 * Normaliza o valor final pra envio/comparação/gravação: garante sempre
 * exatamente 1 casa decimal. Chamar antes de comparar, calcular ou enviar
 * ao servidor. Diferente de formatarKmInput(), aqui sim a casa decimal
 * ausente vira ".0" — só faz sentido completar isso quando o motorista já
 * terminou de digitar, não a cada keystroke.
 */
fun normalizarKmParaEnvio(entrada: String): String {
    if (entrada.isBlank()) return "0.0"
    val formatado = formatarKmInput(entrada)
    if (formatado.isEmpty()) return "0.0"
    val partes = formatado.split(".", limit = 2)
    val decimal = partes.getOrElse(1) { "" }.ifEmpty { "0" }
    return "${partes[0]}.$decimal"
}

/**
 * Formata o texto digitado a cada keystroke pros campos de KM que já sabem
 * a placa do veículo (KM de Início/Chegada de viagem).
 *
 * @param temDecimal Vem de AppRepository.hodometroTemDecimal(placa) — se o
 * painel do hodômetro DESSA placa mostra 1 casa decimal ou só número
 * inteiro (é característica fixa do veículo, não do motorista digitando).
 *   - true: motorista digita tudo corrido, igual aparece no painel (ex.:
 *     "1156705" mostra "115670.5") — o "." entra sozinho na posição certa,
 *     sem o motorista precisar pensar nisso.
 *   - false: só número inteiro, sem "." nenhum (ex.: "62350" mostra
 *     "62350") — o painel desse veículo nunca mostra fração.
 */
fun formatarKmVeiculo(entrada: String, temDecimal: Boolean): String {
    if (!temDecimal) {
        // 8 dígitos cobre o range inteiro de um DECIMAL(10,1) (até 99999999).
        return entrada.filter { it.isDigit() }.take(8).trimStart('0')
    }

    // 9 dígitos = 8 inteiros + 1 decimal, cobre o range inteiro de um
    // DECIMAL(10,1) (até 99999999.9) sem truncar valor real no round-trip.
    val digitos = entrada.filter { it.isDigit() }.take(9)
    if (digitos.isEmpty()) return ""

    val decimal = digitos.last()
    var inteiro = digitos.dropLast(1).trimStart('0')
    if (inteiro.isEmpty()) inteiro = "0"

    return "$inteiro.$decimal"
}

/**
 * Normaliza pra envio/comparação/gravação o valor de um campo que já usa
 * formatarKmVeiculo() — garante sempre exatamente 1 casa decimal (mesmo
 * pra veículo sem casa decimal no painel: o banco, DECIMAL(10,1), guarda
 * "62350.0" do mesmo jeito). Chamar antes de comparar, calcular ou enviar
 * ao servidor — nunca no meio da digitação.
 */
fun normalizarKmVeiculoParaEnvio(entrada: String, temDecimal: Boolean): String {
    if (entrada.isBlank()) return "0.0"
    val formatado = formatarKmVeiculo(entrada, temDecimal)
    if (formatado.isEmpty()) return "0.0"
    return if (formatado.contains('.')) formatado else "$formatado.0"
}

/**
 * Converte um valor JÁ NORMALIZADO (vindo do banco local ou da API, sempre
 * "X.Y") pra Double — nunca comparar km como String. NÃO usar em texto
 * ainda sendo digitado pelo motorista: esse passa por
 * normalizarKmParaEnvio()/normalizarKmVeiculoParaEnvio() antes.
 */
fun kmParaDouble(km: String?): Double =
    km?.replace(',', '.')?.toDoubleOrNull() ?: 0.0

/**
 * Formata um Double no padrão de exibição/envio do app: "XXXXXX.X".
 * Usa aritmética inteira (décimos) em vez de Double.toString() pra evitar
 * artefatos de ponto flutuante (ex.: "1597884.8999999999") — mesmo padrão
 * já usado nos campos de dinheiro do app (multiplica, arredonda pra Long,
 * separa inteiro/decimal na mão).
 */
fun formatarKmExibicao(valor: Double): String {
    val decimosTotal = round(valor * 10).toLong()
    val negativo = decimosTotal < 0
    val absDecimos = abs(decimosTotal)
    val inteiro = absDecimos / 10
    val decimal = absDecimos % 10
    return (if (negativo) "-" else "") + "$inteiro.$decimal"
}
