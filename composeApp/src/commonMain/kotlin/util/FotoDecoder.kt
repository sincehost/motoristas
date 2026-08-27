package util

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Decodifica uma foto salva localmente como Base64 (com ou sem o prefixo
 * "data:image/...;base64,") para exibir em tela — usado pela tela de
 * Pendências de Sincronização pra mostrar a foto de um lançamento que ainda
 * não sincronizou, sem depender de nenhuma tela de edição específica.
 * Retorna null (nunca lança exceção) se o dado estiver corrompido.
 */
expect fun decodificarFotoBase64(base64: String): ImageBitmap?
