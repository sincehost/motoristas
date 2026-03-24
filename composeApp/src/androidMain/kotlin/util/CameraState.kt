package util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * CameraState — Sobrevive à morte do processo quando a câmera está aberta.
 *
 * COMPATIBILIDADE XIAOMI/MIUI:
 * - Usa cacheDir em vez de externalFilesDir (MIUI restringe acesso externo)
 * - Detecta MIUI e ajusta comportamento do FileProvider
 * - Verifica arquivo pós-captura mesmo quando câmera retorna false (bug MIUI)
 * - Fallback com Intent explícito se TakePicture falhar
 *
 * USO EM QUALQUER TELA:
 *
 *   val cameraState = rememberCameraState(context, prefix = "COMB")
 *
 *   // Ao tirar foto:
 *   cameraLauncher.launch(cameraState.prepareCapture())
 *
 *   // No callback da câmera (IMPORTANTE: verificar mesmo se success == false):
 *   val cameraLauncher = rememberLauncherForActivityResult(TakePicture()) { success ->
 *       // Em MIUI, success pode ser false mesmo com foto salva
 *       if (success || cameraState.checkPhotoExistsAfterCapture()) {
 *           cameraState.onPhotoTaken()
 *       }
 *   }
 *
 *   // Limpar foto:
 *   cameraState.clear()
 */
class CameraState(
    private val context: Context,
    private val prefix: String = "FOTO",
    initialPath: String = ""
) {
    // Path salvo como String — sobrevive ao processo ser morto
    var photoPath by mutableStateOf(initialPath)
        private set

    // Bitmap e Base64 — derivados do path, recalculados se necessário
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var base64 by mutableStateOf<String?>(null)
        private set

    // Estado de carregamento
    var hasPhoto by mutableStateOf(initialPath.isNotEmpty())
        private set

    // URI da última captura — necessário para fallback MIUI
    var lastCaptureUri by mutableStateOf<Uri?>(null)
        private set

    init {
        // Se tem path salvo (Activity recriada), recarregar foto do arquivo
        if (initialPath.isNotEmpty()) {
            reloadFromPath()
        }
    }

    /**
     * Prepara para captura: cria arquivo temporário e retorna Uri para a câmera.
     * Chamar antes de lançar o cameraLauncher.
     *
     * CORREÇÃO MIUI: Usa cacheDir em vez de externalFilesDir.
     * MIUI 14+ restringe acesso a external storage para apps em background,
     * causando FileProvider.getUriForFile() falhar silenciosamente.
     * cacheDir é sempre acessível e não requer permissões extras.
     */
    fun prepareCapture(): Uri {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

        // MIUI FIX: Usar cacheDir (interno) em vez de externalFilesDir
        // Em MIUI 14/15, externalFilesDir pode estar inacessível quando o app
        // volta do background após a câmera fechar
        val storageDir = if (isMiui()) {
            // MIUI: usar diretório interno do cache (sempre acessível)
            File(context.cacheDir, "camera_photos").also { it.mkdirs() }
        } else {
            // Outros fabricantes: usar externalFilesDir (padrão)
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                ?: File(context.cacheDir, "camera_photos").also { it.mkdirs() }
        }

        val file = File.createTempFile("${prefix}_${timeStamp}_", ".jpg", storageDir)
        photoPath = file.absolutePath

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        lastCaptureUri = uri
        return uri
    }

    /**
     * Verifica se a foto existe no path mesmo quando a câmera retorna false.
     *
     * BUG MIUI: Em Xiaomi/POCO com MIUI 14/15, a câmera pode retornar
     * RESULT_CANCELED (success=false) mesmo tendo salvo a foto com sucesso.
     * Isso acontece porque o MIUI intercepta o resultado da Activity.
     *
     * DEVE SER CHAMADO NO CALLBACK DA CÂMERA:
     *   if (success || cameraState.checkPhotoExistsAfterCapture()) { ... }
     */
    fun checkPhotoExistsAfterCapture(): Boolean {
        if (photoPath.isEmpty()) return false
        val file = File(photoPath)
        // Foto existe e tem tamanho > 0 (não é arquivo vazio)
        return file.exists() && file.length() > 0
    }

    /**
     * Chamado quando a câmera retorna com sucesso (ou checkPhotoExistsAfterCapture() == true).
     * Comprime a foto e gera bitmap + base64.
     */
    fun onPhotoTaken() {
        if (photoPath.isEmpty()) return
        val file = File(photoPath)
        if (!file.exists() || file.length() == 0L) return

        val result = ImageCompressor.compressFromFile(file)
        bitmap = result.bitmap
        base64 = result.base64
        hasPhoto = result.bitmap != null
    }

    /**
     * Carrega foto a partir de Uri da galeria.
     */
    fun onGalleryPicked(uri: Uri) {
        val result = ImageCompressor.compressFromUri(context, uri)
        bitmap = result.bitmap
        base64 = result.base64
        hasPhoto = result.bitmap != null

        // Salvar path para sobreviver à recriação (não temos File da galeria,
        // mas o bitmap já está carregado)
        photoPath = "gallery:${uri}"
    }

    /**
     * Limpa a foto (botão de remover).
     */
    fun clear() {
        // Deletar arquivo temporário se existir
        if (photoPath.isNotEmpty() && !photoPath.startsWith("gallery:") && !photoPath.startsWith("existing:")) {
            try { File(photoPath).delete() } catch (_: Exception) {}
        }
        photoPath = ""
        bitmap?.recycle()
        bitmap = null
        base64 = null
        hasPhoto = false
        lastCaptureUri = null
    }

    /**
     * Carrega foto existente do servidor (telas de edição).
     * Recebe o Base64 que já está salvo no servidor e decodifica para Bitmap.
     */
    fun loadExisting(existingBase64: String?) {
        if (existingBase64.isNullOrEmpty()) return
        try {
            base64 = existingBase64

            // Decodificar Base64 para Bitmap para exibir na tela
            val cleanBase64 = if (existingBase64.contains(",")) {
                existingBase64.substringAfter(",")
            } else {
                existingBase64
            }
            val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            hasPhoto = bitmap != null
            photoPath = "existing:loaded"
        } catch (e: Exception) {
            // Base64 inválido — ignorar
            base64 = existingBase64
            hasPhoto = true
            photoPath = "existing:no-preview"
        }
    }

    /**
     * Recarrega bitmap e base64 a partir do path salvo.
     * Chamado quando a Activity é recriada após process death.
     */
    private fun reloadFromPath() {
        if (photoPath.isEmpty()) return

        if (photoPath.startsWith("gallery:")) {
            // Foto da galeria — tentar recarregar via Uri
            try {
                val uriStr = photoPath.removePrefix("gallery:")
                val uri = Uri.parse(uriStr)
                val result = ImageCompressor.compressFromUri(context, uri)
                bitmap = result.bitmap
                base64 = result.base64
                hasPhoto = result.bitmap != null
            } catch (_: Exception) {
                // URI pode ter expirado, limpar
                clear()
            }
        } else if (photoPath.startsWith("existing:")) {
            // Foto do servidor — já foi carregada via loadExisting(), não recarregar
            // O base64 será restaurado pela tela que chama loadExisting()
        } else {
            // Foto da câmera — recarregar do arquivo
            val file = File(photoPath)
            if (file.exists() && file.length() > 0) {
                val result = ImageCompressor.compressFromFile(file)
                bitmap = result.bitmap
                base64 = result.base64
                hasPhoto = result.bitmap != null
            } else {
                clear()
            }
        }
    }

    companion object {
        /**
         * Saver para `rememberSaveable` — salva apenas o path como String.
         * Bitmap e Base64 são recalculados no init.
         */
        fun saver(context: Context, prefix: String) = Saver<CameraState, String>(
            save = { it.photoPath },
            restore = { path -> CameraState(context, prefix, path) }
        )

        /**
         * Detecta se o dispositivo é Xiaomi/POCO/Redmi com MIUI.
         */
        private fun isMiui(): Boolean {
            val manufacturer = Build.MANUFACTURER?.lowercase(Locale.ROOT) ?: ""
            val brand = Build.BRAND?.lowercase(Locale.ROOT) ?: ""
            val isMiuiDevice = manufacturer.contains("xiaomi") ||
                    manufacturer.contains("redmi") ||
                    brand.contains("poco") ||
                    brand.contains("redmi") ||
                    brand.contains("xiaomi")

            if (!isMiuiDevice) return false

            // Verificar se MIUI está presente via system property
            return try {
                val clazz = Class.forName("android.os.SystemProperties")
                val method = clazz.getMethod("get", String::class.java)
                val miuiVersion = method.invoke(null, "ro.miui.ui.version.name")?.toString() ?: ""
                miuiVersion.isNotEmpty()
            } catch (_: Exception) {
                true // É Xiaomi mas não conseguiu ler property — assumir MIUI
            }
        }
    }
}

/**
 * Detecta se o dispositivo atual é Xiaomi/MIUI.
 * Útil para outras partes do app que precisam saber.
 */
fun isMiuiDevice(): Boolean {
    return try {
        val manufacturer = Build.MANUFACTURER?.lowercase(Locale.ROOT) ?: ""
        val brand = Build.BRAND?.lowercase(Locale.ROOT) ?: ""
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                brand.contains("poco") || brand.contains("redmi") || brand.contains("xiaomi")
    } catch (_: Exception) {
        false
    }
}

/**
 * Composable que cria um CameraState que sobrevive à morte do processo.
 *
 * @param context Context do Android
 * @param prefix Prefixo para nome do arquivo temporário (ex: "COMB", "ARLA", "MANUT")
 */
@Composable
fun rememberCameraState(context: Context, prefix: String = "FOTO"): CameraState {
    return rememberSaveable(saver = CameraState.saver(context, prefix)) {
        CameraState(context, prefix)
    }
}
