package svoboda3.runet.abonent0007.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import svoboda3.runet.abonent0007.ui.theme.GoodGreen

/**
 * Расходящиеся от центра экрана полупрозрачные зелёные круги.
 * При подключении анимация останавливается (running = false) —
 * остаются едва заметные статичные кольца.
 */
@Composable
fun PulseRings(modifier: Modifier = Modifier, running: Boolean = true, ringCount: Int = 3) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = kotlin.math.hypot(cx.toDouble(), cy.toDouble()).toFloat() * 0.62f
        val density = 1f // координаты уже в пикселях

        for (i in 0 until ringCount) {
            val offset = i.toFloat() / ringCount
            val p = if (running) (phase + offset).rem(1f) else 0f
            val radius = maxRadius * (0.06f + 0.94f * p)
            val alphaScale = if (running) (1f - p) * (1f - p) else 1f
            val baseAlpha = 240f * (1f - i * 0.2f)
            val effective = if (running) baseAlpha else 130f
            val alpha = (effective * alphaScale).toInt().coerceIn(0, 255)
            val strokeWidth = if (running) {
                (4.8f - 2.6f * p) * density
            } else {
                2.0f * density
            }
            drawCircle(
                color = GoodGreen.copy(alpha = alpha / 255f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}
