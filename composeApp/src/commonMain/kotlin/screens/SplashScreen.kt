package screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ui.AppColors

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // Animações
    val iconScale = remember { Animatable(0f) }
    val iconAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val dotsAlpha = remember { Animatable(0f) }

    // Animação de loading dots pulsando
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotPulse"
    )

    LaunchedEffect(Unit) {
        // Sequência de animações - RÁPIDA
        iconScale.animateTo(1.1f, tween(250, easing = EaseOutBack))
        iconScale.animateTo(1f, tween(100))
        iconAlpha.animateTo(1f, tween(200))

        delay(50)
        textAlpha.animateTo(1f, tween(250))

        delay(50)
        subtitleAlpha.animateTo(1f, tween(200))

        delay(50)
        dotsAlpha.animateTo(1f, tween(150))

        // Tempo total de exibição reduzido
        delay(400)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.Primary,
                        Color(0xFF0A3A7A),
                        AppColors.Secondary.copy(alpha = 0.8f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Ícone do caminhão com brilho
            Box(
                modifier = Modifier
                    .scale(iconScale.value)
                    .alpha(iconAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                // Círculo de fundo com brilho
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Nome do app
            Text(
                text = "Trakvia",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(Modifier.height(8.dp))

            // Subtítulo
            Text(
                text = "Gestão de Viagens e Frota",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.alpha(subtitleAlpha.value)
            )

            Spacer(Modifier.height(40.dp))

            // Loading dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.alpha(dotsAlpha.value)
            ) {
                repeat(3) { index ->
                    val delayedScale by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = index * 200, easing = EaseInOutCubic),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size((8 * delayedScale).dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.8f))
                    )
                }
            }
        }

        // Rodapé
        Text(
            text = "LF System",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .alpha(subtitleAlpha.value)
        )
    }
}
