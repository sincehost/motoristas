package util

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.AppRepository
import kotlinx.coroutines.delay
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ui.AppColors

/**
 * Rascunho de formulário guardado no banco do celular (tabela Preferencia).
 *
 * rememberSaveable só sobrevive à câmera/recriação da tela; se o motorista
 * saía do formulário (voltar, atender ligação e o sistema fechar o app,
 * conferir algo no painel), perdia tudo o que tinha digitado. Agora os
 * textos e escolhas ficam salvos até o lançamento ser gravado.
 *
 * Fotos NÃO entram (base64 grande demais pra isso) — o aviso avisa.
 *
 * [dono] amarra o rascunho a um contexto (ex.: a viagem em andamento): um
 * rascunho de abastecimento da viagem anterior não aparece na viagem nova.
 */
class Rascunho(private val repository: AppRepository, chave: String, private val dono: String) {

    private val chavePreferencia = "rascunho:$chave"
    private var encerrado = false

    /** Valores recuperados ao abrir a tela (vazio se não havia rascunho). */
    val valores: Map<String, String> = carregar()

    val recuperado: Boolean get() = valores.isNotEmpty()

    private fun carregar(): Map<String, String> = try {
        val texto = repository.getPreferencia(chavePreferencia).orEmpty()
        if (texto.isBlank()) emptyMap() else {
            val mapa = Json.decodeFromString<Map<String, String>>(texto)
            if (mapa[CAMPO_DONO] != dono) emptyMap() else mapa - CAMPO_DONO
        }
    } catch (_: Exception) {
        emptyMap()
    }

    fun salvar(mapa: Map<String, String>) {
        if (encerrado) return
        try {
            if (mapa.values.all { it.isBlank() }) {
                repository.salvarPreferencia(chavePreferencia, "")
            } else {
                repository.salvarPreferencia(chavePreferencia, Json.encodeToString(mapa + (CAMPO_DONO to dono)))
            }
        } catch (_: Exception) {}
    }

    /** Lançamento gravado (ou motorista pediu pra limpar): apaga e para de salvar. */
    fun limpar(encerrar: Boolean = true) {
        if (encerrar) encerrado = true
        try { repository.salvarPreferencia(chavePreferencia, "") } catch (_: Exception) {}
    }

    private companion object {
        const val CAMPO_DONO = "_dono"
    }
}

@Composable
fun rememberRascunho(repository: AppRepository, chave: String, dono: String): Rascunho =
    remember { Rascunho(repository, chave, dono) }

/** Salva o rascunho logo depois que o motorista para de digitar. */
@Composable
fun SalvarRascunhoAutomatico(rascunho: Rascunho, vararg campos: Pair<String, String>) {
    val mapa = campos.toMap()
    LaunchedEffect(mapa) {
        delay(700)
        rascunho.salvar(mapa)
    }
}

/** Aviso no topo do formulário quando um rascunho foi recuperado. */
@Composable
fun RascunhoRecuperadoAviso(onLimpar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AppColors.Primary.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Recuperamos o que você tinha digitado. As fotos precisam ser tiradas de novo.",
                fontSize = 13.sp,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onLimpar) { Text("Limpar") }
        }
    }
}
