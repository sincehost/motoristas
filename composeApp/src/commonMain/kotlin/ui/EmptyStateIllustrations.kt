package ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp

/**
 * Ilustração de caminhão parado para estado "Nenhuma viagem em andamento"
 */
@Composable
fun TruckIdleIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(110.dp, 55.dp)) {
        val w = size.width
        val h = size.height

        val roadY = h * 0.82f
        val truckColor = Color(0xFF07275A)
        val truckAccent = Color(0xFF499C2A)
        val wheelColor = Color(0xFF333333)
        val groundColor = Color(0xFFE0E0E0)
        val skyDots = Color(0xFFBDBDBD)

        // Estrada
        drawRoundRect(
            color = groundColor,
            topLeft = Offset(0f, roadY),
            size = Size(w, h - roadY),
            cornerRadius = CornerRadius(4f)
        )
        // Linha tracejada na estrada
        val dashWidth = w / 20f
        for (i in 0..9) {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(i * dashWidth * 2 + dashWidth * 0.3f, roadY + (h - roadY) / 2 - 2f),
                size = Size(dashWidth, 3f),
                cornerRadius = CornerRadius(2f)
            )
        }

        // Corpo do caminhão (baú)
        val truckX = w * 0.15f
        val truckW = w * 0.52f
        val truckH = h * 0.35f
        val truckY = roadY - truckH - h * 0.08f

        drawRoundRect(
            color = truckColor.copy(alpha = 0.15f),
            topLeft = Offset(truckX, truckY),
            size = Size(truckW, truckH),
            cornerRadius = CornerRadius(6f)
        )
        drawRoundRect(
            color = truckColor.copy(alpha = 0.3f),
            topLeft = Offset(truckX + 4f, truckY + 4f),
            size = Size(truckW - 8f, truckH - 8f),
            cornerRadius = CornerRadius(4f)
        )

        // Cabine
        val cabX = truckX + truckW - 4f
        val cabW = w * 0.22f
        val cabH = truckH + h * 0.08f
        val cabY = roadY - cabH

        drawRoundRect(
            color = truckColor.copy(alpha = 0.7f),
            topLeft = Offset(cabX, cabY),
            size = Size(cabW, cabH),
            cornerRadius = CornerRadius(8f, 8f)
        )

        // Janela da cabine
        drawRoundRect(
            color = Color(0xFFB3E5FC).copy(alpha = 0.6f),
            topLeft = Offset(cabX + cabW * 0.15f, cabY + cabH * 0.12f),
            size = Size(cabW * 0.7f, cabH * 0.35f),
            cornerRadius = CornerRadius(4f)
        )

        // Faixa verde no baú
        drawRoundRect(
            color = truckAccent.copy(alpha = 0.5f),
            topLeft = Offset(truckX + 8f, truckY + truckH * 0.7f),
            size = Size(truckW - 16f, truckH * 0.15f),
            cornerRadius = CornerRadius(2f)
        )

        // Rodas
        val wheelR = h * 0.07f
        val wheel1X = truckX + truckW * 0.2f
        val wheel2X = truckX + truckW * 0.65f
        val wheel3X = cabX + cabW * 0.5f
        val wheelY = roadY - wheelR * 0.3f

        for (wx in listOf(wheel1X, wheel2X, wheel3X)) {
            drawCircle(color = wheelColor.copy(alpha = 0.6f), radius = wheelR, center = Offset(wx, wheelY))
            drawCircle(color = Color(0xFF888888).copy(alpha = 0.5f), radius = wheelR * 0.45f, center = Offset(wx, wheelY))
        }

        // ZZZ (dormindo / parado)
        val zColor = Color(0xFF9E9E9E)
        val zX = cabX + cabW + 12f
        val zY = cabY - 8f
        drawZzz(zX, zY, zColor, 0.6f)
        drawZzz(zX + 14f, zY - 16f, zColor, 0.8f)
        drawZzz(zX + 30f, zY - 36f, zColor, 1f)
    }
}

private fun DrawScope.drawZzz(x: Float, y: Float, color: Color, scale: Float) {
    val s = 10f * scale
    val path = Path().apply {
        moveTo(x, y)
        lineTo(x + s, y)
        lineTo(x, y + s * 0.8f)
        lineTo(x + s, y + s * 0.8f)
    }
    drawPath(path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * scale))
}

/**
 * Ilustração de caixa vazia para listas sem dados
 */
@Composable
fun EmptyBoxIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(140.dp, 100.dp)) {
        val w = size.width
        val h = size.height

        val boxColor = Color(0xFFBDBDBD)
        val boxShadow = Color(0xFFE0E0E0)

        val cx = w / 2
        val cy = h * 0.55f
        val bw = w * 0.4f
        val bh = h * 0.35f

        // Sombra
        drawOval(
            color = boxShadow,
            topLeft = Offset(cx - bw * 0.6f, cy + bh * 0.7f),
            size = Size(bw * 1.2f, h * 0.08f)
        )

        // Caixa frente
        drawRoundRect(
            color = boxColor.copy(alpha = 0.3f),
            topLeft = Offset(cx - bw / 2, cy - bh / 2),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(4f)
        )

        // Abas da caixa (aberta)
        val abaPath = Path().apply {
            moveTo(cx - bw / 2, cy - bh / 2)
            lineTo(cx - bw / 2 - 8f, cy - bh / 2 - bh * 0.35f)
            lineTo(cx - 4f, cy - bh / 2 - bh * 0.3f)
            lineTo(cx, cy - bh / 2)
            close()
        }
        drawPath(abaPath, color = boxColor.copy(alpha = 0.4f))

        val abaPath2 = Path().apply {
            moveTo(cx, cy - bh / 2)
            lineTo(cx + 4f, cy - bh / 2 - bh * 0.3f)
            lineTo(cx + bw / 2 + 8f, cy - bh / 2 - bh * 0.35f)
            lineTo(cx + bw / 2, cy - bh / 2)
            close()
        }
        drawPath(abaPath2, color = boxColor.copy(alpha = 0.25f))

        // Linha central da caixa
        drawLine(
            color = boxColor.copy(alpha = 0.5f),
            start = Offset(cx, cy - bh / 2),
            end = Offset(cx, cy + bh / 2),
            strokeWidth = 1.5f
        )
    }
}
