package com.samuel.ielts.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiParticle(
    val startXFraction: Float,
    val startYFraction: Float,
    val velocityX: Float,
    val velocityY: Float,
    val gravity: Float,
    val sizePx: Float,
    val aspectRatio: Float,
    val initialAngle: Float,
    val spinSpeed: Float,
    val swayPhase: Float,
    val color: Color,
    val isCircle: Boolean
)

private val ConfettiPalette = listOf(
    Color(0xFF1D4ED8),
    Color(0xFF0F766E),
    Color(0xFFF59E0B),
    Color(0xFFEF4444),
    Color(0xFF10B981),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899)
)

@Composable
fun ConfettiBurst(
    show: Boolean,
    onFinished: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val particles = remember(show) {
        if (!show) {
            emptyList()
        } else {
            val random = Random(System.nanoTime())
            List(64) { index ->
                val angle = -Math.PI * (0.15 + random.nextDouble() * 0.70)
                val speed = 0.42f + random.nextFloat() * 0.65f
                ConfettiParticle(
                    startXFraction = 0.18f + random.nextFloat() * 0.64f,
                    startYFraction = 0.22f + random.nextFloat() * 0.16f,
                    velocityX = (cos(angle) * speed).toFloat(),
                    velocityY = (sin(angle) * speed * 1.15f).toFloat(),
                    gravity = 1.45f + random.nextFloat() * 0.75f,
                    sizePx = 10f + random.nextFloat() * 12f,
                    aspectRatio = 0.45f + random.nextFloat() * 0.55f,
                    initialAngle = random.nextFloat() * 360f,
                    spinSpeed = (random.nextFloat() - 0.5f) * 720f,
                    swayPhase = random.nextFloat() * 6.28f,
                    color = ConfettiPalette[index % ConfettiPalette.size],
                    isCircle = index % 5 == 0
                )
            }
        }
    }

    LaunchedEffect(show) {
        if (show) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2200, easing = LinearEasing)
            )
            onFinished()
        } else {
            progress.snapTo(0f)
        }
    }

    if (!show || particles.isEmpty()) return

    val t = progress.value
    val fadeAlpha = when {
        t < 0.72f -> 1f
        else -> ((1f - t) / 0.28f).coerceIn(0f, 1f)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        particles.forEach { p ->
            val sway = sin(t * 10f + p.swayPhase) * 18f * t
            val x = p.startXFraction * width + p.velocityX * width * t + sway
            val y = p.startYFraction * height +
                p.velocityY * height * t +
                0.5f * p.gravity * height * t * t

            if (y <= height + 40f && x >= -40f && x <= width + 40f) {
                val drawColor = p.color.copy(alpha = fadeAlpha)
                if (p.isCircle) {
                    drawCircle(
                        color = drawColor,
                        radius = p.sizePx * 0.45f,
                        center = Offset(x, y)
                    )
                } else {
                    val w = p.sizePx
                    val h = p.sizePx * p.aspectRatio
                    withTransform({
                        rotate(
                            degrees = p.initialAngle + p.spinSpeed * t,
                            pivot = Offset(x, y)
                        )
                    }) {
                        drawRect(
                            color = drawColor,
                            topLeft = Offset(x - w / 2f, y - h / 2f),
                            size = Size(w, h)
                        )
                    }
                }
            }
        }
    }
}
