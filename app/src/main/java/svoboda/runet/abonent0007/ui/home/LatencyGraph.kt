package svoboda.runet.abonent0007.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import svoboda.runet.abonent0007.ui.theme.FairAmber
import svoboda.runet.abonent0007.ui.theme.GoodGreen
import svoboda.runet.abonent0007.ui.theme.PoorRed

/**
 * Сплошная линия пинга: цвет сегмента зависит от значения
 * (зелёный → янтарный → красный). Значения < 0 (потери) рисуются
 * красной точкой-провалом.
 */
@Composable
fun LatencyGraph(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    maxLatencyMs: Float = 300f,
) {
    if (samples.isEmpty()) return
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val stroke = 9f
        val step = w / (samples.size - 1).coerceAtLeast(1)

        // Едва заметные горизонтальные ориентиры.
        for (f in listOf(0.25f, 0.5f, 0.75f)) {
            val gy = h * f
            drawLine(
                color = Color(0x149FB0C2),
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f,
            )
        }

        fun y(value: Int): Float {
            val v = value.coerceIn(0, maxLatencyMs.toInt())
            return h - (v / maxLatencyMs) * (h - stroke * 2) - stroke
        }

        fun colorFor(value: Int): Color {
            val t = (value.toFloat() / maxLatencyMs).coerceIn(0f, 1f)
            return when {
                value < 0 -> PoorRed.copy(alpha = 0.9f)
                t <= 0.2f -> lerp(GoodGreen, GoodGreen, t / 0.2f)
                t <= 0.5f -> lerp(GoodGreen, FairAmber, (t - 0.2f) / 0.3f)
                t <= 0.85f -> lerp(FairAmber, PoorRed, (t - 0.5f) / 0.35f)
                else -> PoorRed
            }
        }

        var prevX = 0f
        var prevY = 0f
        var prevColor = Color.Transparent
        var started = false

        samples.forEachIndexed { index, value ->
            val x = index * step
            if (value < 0) {
                // Провал: рисуем точку на середине высоты.
                drawCircle(
                    color = PoorRed.copy(alpha = 0.8f),
                    radius = stroke * 0.9f,
                    center = Offset(x, h / 2f),
                )
                started = false
                return@forEachIndexed
            }
            val cy = y(value)
            val color = colorFor(value)
            if (started) {
                drawLine(
                    color = color,
                    start = Offset(prevX, prevY),
                    end = Offset(x, cy),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            } else {
                drawCircle(color = color, radius = stroke * 0.75f, center = Offset(x, cy))
            }
            prevX = x
            prevY = cy
            prevColor = color
            started = true
        }
    }
}
