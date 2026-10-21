package util

/**
 * Build instalado (inteiro, comparado com app_versao.build_atual/build_minimo
 * no servidor central — ver fora/site/api/versao_app.php).
 * Android = versionCode, iOS = CFBundleVersion.
 *
 * Long, não Int: no iPhone o Codemagic gera o build com data e hora
 * (ex.: 202610211345 — ver codemagic.yaml), que não cabe num Int.
 */
expect fun buildAtualApp(): Long

/** Nome da versão instalada (versionName / CFBundleShortVersionString), só pra exibir. */
expect fun versaoAtualApp(): String
