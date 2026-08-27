package util

import androidx.compose.runtime.Composable

/**
 * Devolve uma função que abre o menu nativo de compartilhamento (WhatsApp,
 * e-mail, etc.) com um texto e, opcionalmente, fotos anexadas (Base64,
 * com ou sem prefixo "data:image/...;base64,") — usada pelo botão
 * "Exportar e enviar" de Pendências de Sincronização, pra o motorista
 * mandar pro suporte um lançamento que não conseguiu sincronizar, junto
 * com a foto do painel/cupom, sem precisar reinstalar o app.
 */
@Composable
expect fun rememberCompartilharTexto(): (titulo: String, texto: String, fotosBase64: List<String>) -> Unit
