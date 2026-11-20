package com.ytdlp.gui.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

data class FallingIcon(
    val id: Int,
    val icon: IconType,
    val x: Float,
    val initialY: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val rotation: Float,
    val rotationSpeed: Float,
    val wobbleOffset: Float,
    val wobbleSpeed: Float
)

enum class IconType {
    PLAY, MUSIC, DOWNLOAD, HEADPHONES
}

@Composable
fun FallingIconsBackground(
    modifier: Modifier = Modifier,
    iconCount: Int = 12,
    primaryColor: Color = Color(0xFF8B5CF6),
    secondaryColor: Color = Color(0xFF06B6D4)
) {
    // Animação infinita para o tempo - BEM LENTA
    val infiniteTransition = rememberInfiniteTransition(label = "falling")
    val animatedTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(300000, easing = LinearEasing), // 5 minutos para ciclo completo
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )
    
    // Gerar ícones uma única vez - GRANDES e LENTOS
    val icons = remember {
        List(iconCount) { index ->
            FallingIcon(
                id = index,
                icon = IconType.entries[index % IconType.entries.size],
                x = Random.nextFloat(),
                initialY = Random.nextFloat() * 1.5f - 0.3f, // Espalhar mais
                size = Random.nextFloat() * 40f + 50f, // MUITO MAIORES: 50-90dp
                speed = Random.nextFloat() * 0.015f + 0.008f, // MUITO MAIS LENTOS
                alpha = Random.nextFloat() * 0.15f + 0.08f, // Um pouco mais visíveis
                rotation = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() * 0.8f + 0.3f) * (if (Random.nextBoolean()) 1f else -1f), // Rotação mais visível, alguns horário, outros anti-horário
                wobbleOffset = Random.nextFloat() * 360f,
                wobbleSpeed = Random.nextFloat() * 0.08f + 0.04f // Wobble mais suave
            )
        }
    }
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val screenWidth = size.width
        val screenHeight = size.height
        
        icons.forEach { icon ->
            // Calcular posição Y baseada no tempo (loop infinito)
            val yProgress = (icon.initialY + animatedTime * icon.speed) % 1.4f
            val y = yProgress * screenHeight - screenHeight * 0.2f
            
            // Wobble horizontal mais suave
            val wobble = sin((animatedTime * 0.5f + icon.wobbleOffset) * icon.wobbleSpeed) * 20f
            val x = icon.x * screenWidth + wobble
            
            // Rotação contínua e suave enquanto cai
            val currentRotation = (animatedTime * icon.rotationSpeed + icon.rotation) % 360f
            
            val color = when (icon.icon) {
                IconType.PLAY -> primaryColor
                IconType.MUSIC, IconType.HEADPHONES, IconType.DOWNLOAD -> secondaryColor
            }.copy(alpha = icon.alpha)
            
            drawIcon(
                icon = icon.icon,
                x = x,
                y = y,
                size = icon.size,
                rotation = currentRotation,
                color = color
            )
        }
    }
}

private fun DrawScope.drawIcon(
    icon: IconType,
    x: Float,
    y: Float,
    size: Float,
    rotation: Float,
    color: Color
) {
    rotate(rotation, pivot = Offset(x, y)) {
        when (icon) {
            IconType.PLAY -> drawPlayIcon(x, y, size, color)
            IconType.MUSIC -> drawMusicIcon(x, y, size, color)
            IconType.DOWNLOAD -> drawDownloadIcon(x, y, size, color)
            IconType.HEADPHONES -> drawHeadphonesIcon(x, y, size, color)
        }
    }
}

// ▶ Play icon
private fun DrawScope.drawPlayIcon(x: Float, y: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(x - size * 0.4f, y - size * 0.5f)
        lineTo(x + size * 0.5f, y)
        lineTo(x - size * 0.4f, y + size * 0.5f)
        close()
    }
    drawPath(path, color)
}

// ♪ Music note icon
private fun DrawScope.drawMusicIcon(x: Float, y: Float, size: Float, color: Color) {
    // Stem
    drawLine(
        color = color,
        start = Offset(x + size * 0.2f, y - size * 0.5f),
        end = Offset(x + size * 0.2f, y + size * 0.2f),
        strokeWidth = size * 0.12f
    )
    // Note head
    drawCircle(
        color = color,
        radius = size * 0.25f,
        center = Offset(x, y + size * 0.25f)
    )
    // Flag
    drawLine(
        color = color,
        start = Offset(x + size * 0.2f, y - size * 0.5f),
        end = Offset(x + size * 0.5f, y - size * 0.2f),
        strokeWidth = size * 0.1f
    )
}

// ⬇ Download icon
private fun DrawScope.drawDownloadIcon(x: Float, y: Float, size: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(x, y - size * 0.4f),
        end = Offset(x, y + size * 0.2f),
        strokeWidth = size * 0.15f
    )
    val path = Path().apply {
        moveTo(x - size * 0.3f, y)
        lineTo(x, y + size * 0.35f)
        lineTo(x + size * 0.3f, y)
        close()
    }
    drawPath(path, color)
    drawLine(
        color = color,
        start = Offset(x - size * 0.4f, y + size * 0.45f),
        end = Offset(x + size * 0.4f, y + size * 0.45f),
        strokeWidth = size * 0.12f
    )
}

// 🎧 Headphones icon
private fun DrawScope.drawHeadphonesIcon(x: Float, y: Float, size: Float, color: Color) {
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(x - size * 0.4f, y - size * 0.4f),
        size = Size(size * 0.8f, size * 0.6f),
        style = Stroke(width = size * 0.1f)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(x - size * 0.5f, y),
        size = Size(size * 0.25f, size * 0.4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.08f)
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(x + size * 0.25f, y),
        size = Size(size * 0.25f, size * 0.4f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size * 0.08f)
    )
}
