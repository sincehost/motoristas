package ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import util.EstabilizadorKm
import util.LeitorKm
import util.LogWriter
import util.formatarKmBR
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max

// Moldura onde o motorista enquadra o hodômetro — fração da área da câmera.
// O texto só conta se o centro da linha cair dentro dela (ignora relógio,
// rádio, computador de bordo etc. fora do enquadramento).
private const val MOLDURA_ESQ = 0.07f
private const val MOLDURA_DIR = 0.93f
private const val MOLDURA_TOPO = 0.34f
private const val MOLDURA_BASE = 0.50f

/**
 * Câmera dentro do app que lê o KM do hodômetro ao vivo (ML Kit, no próprio
 * aparelho, sem internet) e tira a foto de comprovante.
 *
 * Fluxo: o motorista enquadra o painel na moldura → o app lê vários quadros
 * por segundo → quando o MESMO número aparece em 3 das últimas 5 leituras e
 * passa na conferência (entre [referenciaKm] e referência + [limiteKm]), a
 * leitura trava e mostra o KM grande → "Usar este KM" tira a foto e devolve
 * os dois. Sem leitura firme, "Só tirar a foto" segue como antes (ele digita).
 * Sem [referenciaKm] não sugere KM nenhum — não tem com o que conferir.
 *
 * Abre por cima da tela (mesmo esquema do AppAlertDialog). Permissão de
 * câmera deve estar concedida antes de abrir.
 *
 * @param onConcluir km lido (null = só a foto) e o arquivo JPEG já na
 *   orientação certa — passar pra CameraState.onFotoExterna().
 * @param onUsarCameraDoCelular fallback se a câmera não abrir aqui (aparelho
 *   incompatível) — a tela volta pra câmera do sistema de sempre.
 */
@Composable
fun ScannerKm(
    referenciaKm: Double?,
    limiteKm: Double,
    temDecimal: Boolean,
    onConcluir: (km: Double?, foto: File) -> Unit,
    onUsarCameraDoCelular: () -> Unit,
    onFechar: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val podeLer = referenciaKm != null && referenciaKm > 0.0

    var kmLendo by remember { mutableStateOf<Double?>(null) }
    var kmFirme by remember { mutableStateOf<Double?>(null) }
    var capturando by remember { mutableStateOf(false) }
    var lanterna by remember { mutableStateOf(false) }
    var erroCamera by remember { mutableStateOf(false) }
    var erroCaptura by remember { mutableStateOf<String?>(null) }
    var mostrarDica by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val pausado = remember { AtomicBoolean(false) }
    val tamanhoPreview = remember { AtomicReference(IntSize.Zero) }
    val estabilizador = remember { EstabilizadorKm() }
    val capturaExecutor = remember { Executors.newSingleThreadExecutor() }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            // ~2 MP: suficiente pro comprovante (o envio comprime pra 1280px)
            // sem estourar memória em aparelho simples.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    )
                    .build()
            )
            .build()
    }

    BackHandler(onBack = onFechar)

    DisposableEffect(lifecycleOwner) {
        val analiseExecutor = Executors.newSingleThreadExecutor()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val providerFuture = ProcessCameraProvider.getInstance(context)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analise = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                if (podeLer) {
                    analise.setAnalyzer(
                        analiseExecutor,
                        AnalisadorKm(recognizer, analiseExecutor, pausado, tamanhoPreview) { linhas ->
                            val km = LeitorKm.escolher(LeitorKm.candidatos(linhas, temDecimal), referenciaKm, limiteKm)
                            mainExecutor.execute {
                                if (pausado.get()) return@execute
                                kmLendo = km
                                estabilizador.registrar(km)?.let {
                                    kmFirme = it
                                    pausado.set(true)
                                }
                            }
                        }
                    )
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analise, imageCapture
                )
            } catch (e: Exception) {
                LogWriter.log("❌ ScannerKm: câmera não abriu: ${e.message}")
                erroCamera = true
            }
        }, mainExecutor)

        onDispose {
            pausado.set(true)
            try { providerFuture.get().unbindAll() } catch (_: Exception) {}
            analiseExecutor.shutdown()
            capturaExecutor.shutdown()
            recognizer.close()
        }
    }

    LaunchedEffect(lanterna, camera) {
        try { camera?.cameraControl?.enableTorch(lanterna) } catch (_: Exception) {}
    }

    // Dica depois de alguns segundos sem leitura firme.
    LaunchedEffect(kmFirme) {
        mostrarDica = false
        if (kmFirme == null) {
            delay(8_000)
            mostrarDica = true
        }
    }

    fun capturar(km: Double?) {
        if (capturando) return
        capturando = true
        erroCaptura = null
        pausado.set(true)
        imageCapture.takePicture(capturaExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val arquivo = try {
                    salvarFotoNaOrientacaoCerta(context, image)
                } catch (e: Exception) {
                    LogWriter.log("❌ ScannerKm: erro ao salvar foto: ${e.message}")
                    null
                } finally {
                    image.close()
                }
                mainExecutor.execute {
                    capturando = false
                    if (arquivo != null) {
                        onConcluir(km, arquivo)
                    } else {
                        erroCaptura = "Não foi possível salvar a foto. Tente de novo."
                        if (kmFirme == null) pausado.set(false)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                LogWriter.log("❌ ScannerKm: erro na captura: ${exception.message}")
                mainExecutor.execute {
                    capturando = false
                    erroCaptura = "Não foi possível tirar a foto. Tente de novo."
                    if (kmFirme == null) pausado.set(false)
                }
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(2000f)
            .background(Color.Black)
            .onSizeChanged { tamanhoPreview.set(it) }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Escurece fora da moldura + borda da moldura
        val corMoldura = if (kmFirme != null) Color(0xFF66D944) else Color.White
        Canvas(modifier = Modifier.fillMaxSize()) {
            val moldura = RoundRect(
                left = size.width * MOLDURA_ESQ,
                top = size.height * MOLDURA_TOPO,
                right = size.width * MOLDURA_DIR,
                bottom = size.height * MOLDURA_BASE,
                cornerRadius = CornerRadius(16.dp.toPx())
            )
            val fora = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, GSize(size.width, size.height)))
                addRoundRect(moldura)
            }
            drawPath(fora, Color.Black.copy(alpha = 0.55f))
            drawPath(Path().apply { addRoundRect(moldura) }, corMoldura, style = Stroke(width = 3.dp.toPx()))
        }

        // Topo: fechar, instrução, lanterna
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(
                    onClick = onFechar,
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) { Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White) }
                IconButton(
                    onClick = { lanterna = !lanterna },
                    modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        if (lanterna) Icons.Default.FlashlightOn else Icons.Default.FlashlightOff,
                        contentDescription = if (lanterna) "Desligar lanterna" else "Ligar lanterna",
                        tint = Color.White
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Enquadre os números do hodômetro na moldura",
                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }

        // Painel de baixo
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = AppColors.Surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val firme = kmFirme
                when {
                    erroCamera -> {
                        Text("Não foi possível abrir a câmera aqui.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary, textAlign = TextAlign.Center)
                        BotaoPrincipal("Usar a câmera do celular", AppColors.Primary, onClick = onUsarCameraDoCelular)
                    }
                    !podeLer -> {
                        Text(
                            "Leitura automática indisponível: não há KM anterior desta placa para conferir. Tire a foto e digite o KM.",
                            fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center
                        )
                        BotaoPrincipal(if (capturando) "Salvando…" else "Tirar foto", AppColors.Primary, enabled = !capturando) { capturar(null) }
                    }
                    firme != null -> {
                        Text("KM LIDO", fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AppColors.TextSecondary)
                        Text(formatarKmBR(firme, temDecimal), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        Text("Confira com o painel antes de usar.", fontSize = 13.sp, color = AppColors.TextSecondary)
                        BotaoPrincipal(if (capturando) "Salvando…" else "Usar este KM", Color(0xFF2E7D32), enabled = !capturando) { capturar(firme) }
                        TextButton(
                            onClick = {
                                kmFirme = null; kmLendo = null
                                estabilizador.limpar()
                                pausado.set(false)
                            },
                            enabled = !capturando
                        ) { Text("Ler de novo", fontSize = 15.sp) }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                            val lendo = kmLendo
                            Text(
                                if (lendo != null) "Lendo ${formatarKmBR(lendo, temDecimal)}…" else "Procurando o KM…",
                                fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary
                            )
                        }
                        if (mostrarDica) {
                            Text(
                                "Não está lendo? Aproxime o celular, evite reflexo ou ligue a lanterna. Você também pode tirar só a foto e digitar o KM.",
                                fontSize = 13.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center
                            )
                        }
                        OutlinedButton(
                            onClick = { capturar(null) },
                            enabled = !capturando,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text(if (capturando) "Salvando…" else "Só tirar a foto", fontSize = 15.sp) }
                    }
                }
                erroCaptura?.let { Text(it, fontSize = 13.sp, color = AppColors.Error, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun BotaoPrincipal(texto: String, cor: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = cor, contentColor = Color.White)
    ) { Text(texto, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Lê o texto de cada quadro da câmera e entrega só as linhas cujo centro cai
 * dentro da moldura. Um quadro por vez (KEEP_ONLY_LATEST + close só depois do
 * OCR terminar), então nunca acumula fila.
 */
private class AnalisadorKm(
    private val recognizer: com.google.mlkit.vision.text.TextRecognizer,
    private val executor: java.util.concurrent.Executor,
    private val pausado: AtomicBoolean,
    private val tamanhoPreview: AtomicReference<IntSize>,
    private val onLinhas: (List<String>) -> Unit
) : ImageAnalysis.Analyzer {

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || pausado.get()) {
            proxy.close()
            return
        }
        val rotacao = proxy.imageInfo.rotationDegrees
        val larguraImg = if (rotacao % 180 == 0) proxy.width else proxy.height
        val alturaImg = if (rotacao % 180 == 0) proxy.height else proxy.width
        val moldura = molduraNaImagem(larguraImg, alturaImg, tamanhoPreview.get())

        recognizer.process(InputImage.fromMediaImage(media, rotacao))
            .addOnSuccessListener(executor) { texto ->
                val linhas = texto.textBlocks
                    .flatMap { it.lines }
                    .filter { linha ->
                        val caixa = linha.boundingBox
                        caixa != null && moldura.contains(caixa.centerX(), caixa.centerY())
                    }
                    .map { it.text }
                onLinhas(linhas)
            }
            .addOnCompleteListener(executor) { proxy.close() }
    }
}

/**
 * Converte a moldura (fração da tela) pra coordenadas da imagem analisada.
 * PreviewView em FILL_CENTER: a imagem é ampliada até cobrir a tela e o
 * excesso é cortado nas bordas — mesma conta aqui. Margem extra de 4% em cima
 * e embaixo pra não perder dígito encostado na borda.
 */
private fun molduraNaImagem(larguraImg: Int, alturaImg: Int, preview: IntSize): Rect {
    if (preview.width == 0 || preview.height == 0) return Rect(0, 0, larguraImg, alturaImg)
    val pw = preview.width.toFloat()
    val ph = preview.height.toFloat()
    val escala = max(pw / larguraImg, ph / alturaImg)
    val offX = (pw - larguraImg * escala) / 2f
    val offY = (ph - alturaImg * escala) / 2f
    fun x(px: Float) = ((px - offX) / escala).toInt().coerceIn(0, larguraImg)
    fun y(py: Float) = ((py - offY) / escala).toInt().coerceIn(0, alturaImg)
    return Rect(
        x(pw * MOLDURA_ESQ),
        y(ph * (MOLDURA_TOPO - 0.04f)),
        x(pw * MOLDURA_DIR),
        y(ph * (MOLDURA_BASE + 0.04f))
    )
}

/** Foto da ImageCapture → JPEG em arquivo, com os pixels já girados (sem depender de EXIF). */
private fun salvarFotoNaOrientacaoCerta(context: Context, image: ImageProxy): File {
    val original = image.toBitmap()
    val rotacao = image.imageInfo.rotationDegrees
    val final = if (rotacao != 0) {
        Bitmap.createBitmap(original, 0, 0, original.width, original.height, Matrix().apply { postRotate(rotacao.toFloat()) }, true)
    } else original
    val pasta = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    val arquivo = File(pasta, "KM_${System.currentTimeMillis()}.jpg")
    FileOutputStream(arquivo).use { final.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    if (final !== original) original.recycle()
    final.recycle()
    return arquivo
}
