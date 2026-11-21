package svoboda3.runet.abonent0007.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import svoboda3.runet.abonent0007.ui.theme.AccentBlue

/**
 * Тонкие прозрачные линии — «установка нейронных связей» при подключении.
 *
 * Сначала последовательно прорастают рёбра каркаса (от узла A к узлу B),
 * по уже готовым линиям пробегают световые импульсы, узлы подсвечиваются
 * в момент присоединения нового ребра. Все данные посчитаны заранее,
 * внутри кадра аллокаций нет.
 */
@Composable
fun NeuralLines(
    modifier: Modifier = Modifier,
    active: Boolean,
    color: Color = AccentBlue,
    nodeCount: Int = 12,
    pulseCount: Int = 2,
) {
    if (!active) return

    val progress by rememberInfiniteTransition(label = "neural").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "neuralProgress",
    )

    // Узлы (нормализованные координаты) и рёбра считаем один раз.
    val nodes = remember(nodeCount) {
        List(nodeCount) { i ->
            val sx = (i * 73 + 11) % 100 / 100f
            val sy = (i * 41 + 29) % 100 / 100f
            Offset(0.12f + sx * 0.76f, 0.16f + sy * 0.68f)
        }
    }
    val edges = remember(nodeCount) {
        buildList {
            for (i in 0 until nodeCount) {
                add(i to (i + 1) % nodeCount)
                add(i to (i + 4) % nodeCount)
            }
        }
    }

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val edgeCount = edges.size
        val slot = 1f / edgeCount
        val nodeRadius = 2.0f
        val strokeWidth = 1.6f

        // Экранные координаты узлов.
        val w = size.width
        val h = size.height
        fun nodeAt(index: Int): Offset {
            val n = nodes[index]
            return Offset(n.x * w, n.y * h)
        }

        // Узлы: лёгкая пульсация + подсветка при присоединении ребра.
        for (i in 0 until nodeCount) {
            var brightness = 0.22f + 0.10f * sin((progress * 2f + i * 0.6f) * (2f * PI)).toFloat()
            // Подсветка, если ребро, инцидентное узлу, сейчас «прорастает».
            for (e in 0 until edgeCount) {
                val (a, b) = edges[e]
                if (a != i && b != i) continue
                val local = ((progress - e * slot) / slot).coerceIn(0f, 1f)
                if (local in 0f..1f && local < 0.9f && progress >= e * slot) {
                    brightness += 0.25f * (1f - local / 0.9f)
                }
            }
            drawCircle(
                color = color.copy(alpha = brightness.coerceIn(0f, 0.8f)),
                radius = nodeRadius,
                center = nodeAt(i),
            )
        }

        // Рёбра: построенные остаются слабо видимыми; текущее «прорастает».
        for (e in 0 until edgeCount) {
            val (a, b) = edges[e]
            val edgeStart = e * slot
            if (progress < edgeStart) continue // очередь ещё не наступила
            val local = ((progress - edgeStart) / slot).coerceIn(0f, 1f)
            val from = nodeAt(a)
            val to = nodeAt(b)
            if (local >= 1f) {
                // Построено: слабая постоянная линия.
                drawLine(
                    color = color.copy(alpha = 0.16f),
                    start = from,
                    end = to,
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            } else {
                val t = FastOutSlowInEasing.transform((local / 0.85f).coerceIn(0f, 1f))
                val appear = (local / 0.15f).coerceIn(0f, 1f)
                drawLine(
                    color = color.copy(alpha = min(appear, 1f - local) * 0.5f),
                    start = from,
                    end = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Световые импульсы по уже построенным рёбрам.
        for (p in 0 until pulseCount) {
            val base = progress * edgeCount + p * (edgeCount / pulseCount.toFloat())
            val edgeIndex = (base.toInt()) % edgeCount
            val pos = base - base.toInt()
            val edgeStart = edgeIndex * slot
            if (progress < edgeStart + slot) continue // ребро ещё строится
            val (a, b) = edges[edgeIndex]
            val from = nodeAt(a)
            val to = nodeAt(b)
            val dot = Offset(from.x + (to.x - from.x) * pos, from.y + (to.y - from.y) * pos)
            drawCircle(color = color.copy(alpha = 0.8f), radius = 2.6f, center = dot)
        }
    }
}
