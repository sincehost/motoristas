package util

/**
 * Linhas de texto reconhecidas numa foto já tirada (base64, com ou sem o
 * prefixo "data:image/...;base64,"). Roda no próprio aparelho, sem internet:
 * Android = ML Kit, iPhone = Vision. Qualquer falha devolve lista vazia — a
 * leitura do KM é ajuda, nunca pode impedir o motorista de seguir.
 */
expect suspend fun reconhecerTextoFoto(base64: String): List<String>

/**
 * Lê o KM de uma foto do hodômetro com as mesmas regras do scanner ao vivo
 * (ver [LeitorKm]). null = não leu nada confiável.
 */
suspend fun lerKmDaFoto(base64: String, referencia: Double?, limite: Double, temDecimal: Boolean): Double? {
    if (referencia == null || referencia <= 0.0) return null
    val linhas = reconhecerTextoFoto(base64)
    return LeitorKm.escolher(LeitorKm.candidatos(linhas, temDecimal), referencia, limite)
}
