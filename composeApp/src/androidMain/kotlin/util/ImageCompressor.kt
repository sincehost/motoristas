package util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * ImageCompressor — Utilitário centralizado de compressão de fotos.
 *
 * Problemas que resolve:
 * - Fotos de 5MB viravam ~7MB em Base64, travando sync em 3G
 * - 10 telas copiavam o mesmo bloco de compressão
 * - Sem inSampleSize: foto de 12MP carregada inteira na memória (OOM em dispositivos fracos)
 * - Base64.DEFAULT adicionava quebras de linha desnecessárias
 * - Bitmaps nunca eram reciclados (vazamento de memória)
 *
 * Resultado: fotos saem com ~200-400KB em vez de 5-7MB.
 * Resolução 1280x960 com qualidade 85 mantém texto legível em cupons e painel.
 *
 * Uso em qualquer tela:
 *   val (bitmap, base64) = ImageCompressor.compressFromUri(context, uri)
 *   val (bitmap, base64) = ImageCompressor.compressFromFile(file)
 */
object ImageCompressor {

    // Configurações otimizadas para fotos de comprovantes, cupons e painel de KM
    // Resolução alta o suficiente para texto legível, comprimida o suficiente para 3G
    private const val MAX_WIDTH = 1280
    private const val MAX_HEIGHT = 960
    private const val JPEG_QUALITY = 85

    // Limite máximo de Base64 (~500KB de base64 = ~350KB de imagem)
    private const val MAX_BASE64_LENGTH = 500_000

    /**
     * Resultado da compressão. Bitmap para exibir na tela + Base64 para enviar ao servidor.
     */
    data class CompressResult(
        val bitmap: Bitmap?,
        val base64: String?,
        val sizeKB: Int = 0
    )

    /**
     * Comprime imagem a partir de um Uri (câmera ou galeria).
     *
     * Processo:
     * 1. Lê dimensões sem carregar na memória (inJustDecodeBounds)
     * 2. Calcula inSampleSize para carregar já reduzida (economia de memória)
     * 3. Redimensiona para MAX_WIDTH x MAX_HEIGHT mantendo proporção
     * 4. Comprime para JPEG com qualidade configurável
     * 5. Converte para Base64 com NO_WRAP (sem quebras de linha)
     * 6. Recicla bitmap temporário para liberar memória
     */
    fun compressFromUri(
        context: Context,
        uri: Uri,
        maxWidth: Int = MAX_WIDTH,
        maxHeight: Int = MAX_HEIGHT,
        quality: Int = JPEG_QUALITY
    ): CompressResult {
        return try {
            // PASSO 1: Ler dimensões originais sem carregar na memória
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return CompressResult(null, null)
            }

            // PASSO 2: Calcular inSampleSize (carrega imagem já reduzida)
            // Ex: foto de 4000x3000 com max 800x600 → inSampleSize = 4
            //     Carrega como 1000x750 (economiza 93% de memória)
            val inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxWidth, maxHeight)

            // PASSO 3: Carregar bitmap com inSampleSize + RGB_565 (metade da memória vs ARGB_8888)
            val loadOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            var bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, loadOptions)
            } ?: return CompressResult(null, null)

            // PASSO 4: Redimensionar para tamanho final se ainda maior que o máximo
            bitmap = scaleIfNeeded(bitmap, maxWidth, maxHeight)

            // PASSO 5: Comprimir para JPEG e converter para Base64
            val base64 = bitmapToBase64(bitmap, quality)

            val sizeKB = (base64?.length ?: 0) / 1024

            CompressResult(bitmap = bitmap, base64 = base64, sizeKB = sizeKB)

        } catch (e: Exception) {
            e.printStackTrace()
            CompressResult(null, null)
        }
    }

    /**
     * Comprime imagem a partir de um File (foto salva pela câmera).
     */
    fun compressFromFile(
        file: File,
        maxWidth: Int = MAX_WIDTH,
        maxHeight: Int = MAX_HEIGHT,
        quality: Int = JPEG_QUALITY
    ): CompressResult {
        return try {
            if (!file.exists()) return CompressResult(null, null)

            // PASSO 1: Ler dimensões sem carregar na memória
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            val originalWidth = boundsOptions.outWidth
            val originalHeight = boundsOptions.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return CompressResult(null, null)
            }

            // PASSO 2: Calcular inSampleSize
            val inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxWidth, maxHeight)

            // PASSO 3: Carregar com inSampleSize
            val loadOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            var bitmap = BitmapFactory.decodeFile(file.absolutePath, loadOptions)
                ?: return CompressResult(null, null)

            // PASSO 4: Redimensionar se necessário
            bitmap = scaleIfNeeded(bitmap, maxWidth, maxHeight)

            // PASSO 5: Comprimir e converter
            val base64 = bitmapToBase64(bitmap, quality)

            val sizeKB = (base64?.length ?: 0) / 1024

            CompressResult(bitmap = bitmap, base64 = base64, sizeKB = sizeKB)

        } catch (e: Exception) {
            e.printStackTrace()
            CompressResult(null, null)
        }
    }

    // ===============================
    // FUNÇÕES INTERNAS
    // ===============================

    /**
     * Calcula inSampleSize ideal.
     * Potência de 2 mais próxima que mantém a imagem maior que o target.
     *
     * Ex: original 4032x3024, target 800x600 → inSampleSize = 4
     *     Resultado: carrega como 1008x756 (cabe na memória)
     */
    private fun calculateInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Int {
        var inSampleSize = 1

        if (originalHeight > targetHeight || originalWidth > targetWidth) {
            val halfHeight = originalHeight / 2
            val halfWidth = originalWidth / 2

            // Aumenta inSampleSize até que a imagem caiba no target
            while ((halfHeight / inSampleSize) >= targetHeight &&
                (halfWidth / inSampleSize) >= targetWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * Redimensiona bitmap se ainda maior que o máximo, mantendo proporção.
     * Recicla o bitmap original para liberar memória.
     */
    private fun scaleIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()

        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)

        // Reciclar original se foi criado um novo bitmap
        if (scaled !== bitmap) {
            bitmap.recycle()
        }

        return scaled
    }

    /**
     * Converte bitmap para Base64 com:
     * - JPEG compression com qualidade configurável
     * - Base64.NO_WRAP (sem quebras de linha — ~3% menor que DEFAULT)
     * - Prefixo data URI para envio direto ao servidor
     */
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String? {
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()

            // NO_WRAP: sem quebras de linha (Base64.DEFAULT adiciona \n a cada 76 chars)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)

            "data:image/jpeg;base64,$encoded"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
