# GUIA DE SUBSTITUIÇÃO — Compressão de Fotos
#
# O ImageCompressor.kt já está no zip. Agora você precisa substituir
# o código inline de compressão em cada tela por uma chamada ao ImageCompressor.
#
# São 10 telas. Em cada uma, há 2 blocos para trocar:
#   1. cameraLauncher (foto da câmera)
#   2. galleryLauncher (foto da galeria)
#
# O padrão é SEMPRE o mesmo. Veja abaixo:
#
# ================================================================
# PASSO 1: Adicionar import em cada tela que usa foto
# ================================================================
#
# No topo de cada arquivo, adicionar:
#   import util.ImageCompressor
#
# E REMOVER estes imports que não serão mais necessários:
#   import android.util.Base64
#   import java.io.ByteArrayOutputStream
#
# ================================================================
# PASSO 2: Substituir bloco do cameraLauncher
# ================================================================
#
# ---- ANTES (em todas as telas) ----
#
#   val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
#       if (success && photoFile != null && photoFile!!.exists()) {
#           try {
#               val originalBitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath)
#               if (originalBitmap != null) {
#                   val maxWidth = 800
#                   val maxHeight = 600
#                   val ratio = minOf(maxWidth.toFloat() / originalBitmap.width, maxHeight.toFloat() / originalBitmap.height)
#                   val newWidth = (originalBitmap.width * ratio).toInt()
#                   val newHeight = (originalBitmap.height * ratio).toInt()
#                   val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
#
#                   val outputStream = ByteArrayOutputStream()
#                   scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
#                   val base64 = "data:image/jpeg;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
#
#                   ... atribuições ...
#               }
#           } catch (e: Exception) { }
#       }
#   }
#
# ---- DEPOIS ----
#
#   val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
#       if (success && photoFile != null && photoFile!!.exists()) {
#           val result = ImageCompressor.compressFromFile(photoFile!!)
#           if (result.bitmap != null) {
#               ... atribuições com result.bitmap e result.base64 ...
#           }
#       }
#   }
#
# ================================================================
# PASSO 3: Substituir bloco do galleryLauncher
# ================================================================
#
# ---- ANTES ----
#
#   val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
#       if (uri != null) {
#           try {
#               val inputStream = context.contentResolver.openInputStream(uri)
#               val originalBitmap = BitmapFactory.decodeStream(inputStream)
#               inputStream?.close()
#
#               if (originalBitmap != null) {
#                   val maxWidth = 800
#                   val maxHeight = 600
#                   val ratio = ...
#                   val scaledBitmap = ...
#
#                   val outputStream = ByteArrayOutputStream()
#                   scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
#                   val base64 = "data:image/jpeg;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
#
#                   ... atribuições ...
#               }
#           } catch (e: Exception) { }
#       }
#   }
#
# ---- DEPOIS ----
#
#   val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
#       if (uri != null) {
#           val result = ImageCompressor.compressFromUri(context, uri)
#           if (result.bitmap != null) {
#               ... atribuições com result.bitmap e result.base64 ...
#           }
#       }
#   }
#
# ================================================================
# EXEMPLOS POR TELA (atribuições específicas de cada uma)
# ================================================================
#
# -- OutrasDespesasScreen.android.kt --
# Camera:
#   val result = ImageCompressor.compressFromFile(photoFile!!)
#   if (result.bitmap != null) {
#       fotoComprovante = result.bitmap
#       fotoComprovanteBase64 = result.base64
#   }
# Galeria: igual
#
# -- AdicionarArlaScreen.android.kt --
# Camera:
#   val result = ImageCompressor.compressFromFile(photoFile!!)
#   if (result.bitmap != null) {
#       fotoBitmap = result.bitmap
#       fotoBase64 = result.base64
#   }
# Galeria: igual
#
# -- AdicionarDescargaScreen.android.kt --
# Camera:
#   val result = ImageCompressor.compressFromFile(photoFile!!)
#   if (result.bitmap != null) {
#       fotoBitmap = result.bitmap
#       fotoBase64 = result.base64
#   }
# Galeria: igual
#
# -- AdicionarCombustivelScreen.android.kt -- (tem 2 fotos: marcador e cupom)
# Camera:
#   val result = ImageCompressor.compressFromFile(photoFile!!)
#   if (result.bitmap != null) {
#       when (currentPhotoType) {
#           "marcador" -> { fotoMarcador = result.bitmap; fotoMarcadorBase64 = result.base64 }
#           "cupom" -> { fotoCupom = result.bitmap; fotoCupomBase64 = result.base64 }
#       }
#   }
# Galeria: igual
#
# -- FinalizarViagemScreen.android.kt -- (tem 1 foto do painel)
# Camera:
#   val result = ImageCompressor.compressFromFile(photoFile!!)
#   if (result.bitmap != null) {
#       fotoPainel = result.bitmap
#       fotoPainelBase64 = result.base64
#   }
# Galeria: igual
#
# -- ManutencaoScreen.android.kt -- (tem 2 fotos: comprovante1 e comprovante2)
# Camera:
#   val result = ImageCompressor.compressFromFile(photoFile!!)
#   if (result.bitmap != null) {
#       when (currentPhotoType) {
#           "comprovante1" -> { fotoComprovante1 = result.bitmap; fotoComprovante1Base64 = result.base64 }
#           "comprovante2" -> { fotoComprovante2 = result.bitmap; fotoComprovante2Base64 = result.base64 }
#       }
#   }
# Galeria: igual
#
# -- EditarCombustivelScreen.android.kt -- (marcador + cupom)
# -- EditarArlaScreen.android.kt --
# -- EditarDescargaScreen.android.kt --
# -- EditarManutencaoScreen.android.kt -- (comprovante1 + comprovante2)
# Todos seguem o mesmo padrão acima.
#
# ================================================================
# RESULTADO
# ================================================================
#
# ANTES: Foto de 12MP (4032x3024) = 5MB → Base64 ~7MB
#   - Carregava inteira na memória (48MB em ARGB_8888)
#   - Redimensionava para 800x600 (texto ficava ilegível)
#   - Base64.DEFAULT com quebras de linha
#
# DEPOIS: Mesma foto
#   - inSampleSize=2 → carrega como 2016x1512 (6MB em RGB_565)
#   - Redimensiona para 1280x960 (texto legível em cupons e painel)
#   - JPEG qualidade 85 → ~150-300KB
#   - Base64.NO_WRAP → ~200-400KB final
#   - Bitmap original reciclado automaticamente
#
# Comparação de tamanhos:
#   Original sem compressão: ~7MB (trava sync em 3G)
#   Com compressão:          ~200-400KB (envia em 2-3 segundos em 3G)
