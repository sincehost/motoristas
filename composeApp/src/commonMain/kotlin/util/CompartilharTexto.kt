package util

import androidx.compose.runtime.Composable

/**
 * Devolve uma função que abre o menu nativo de compartilhamento (WhatsApp,
 * e-mail, etc.) com um texto simples — usada pelo botão "Exportar e enviar"
 * de Pendências de Sincronização, pra o motorista mandar pro suporte um
 * lançamento que não conseguiu sincronizar, sem precisar reinstalar o app.
 */
@Composable
expect fun rememberCompartilharTexto(): (titulo: String, texto: String) -> Unit
