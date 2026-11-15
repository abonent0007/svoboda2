package svoboda.runet.abonent0007.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Неоновая надпись: одна чёткая градиентная заливка и мягкое свечение
 * через единственную тень (без обводок, размытых копий и смещений),
 * поэтому буквы остаются чёткими и не «раздваиваются».
 */
@Composable
fun NeonText(
    text: String,
    gradient: List<Color>,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    letterSpacing: TextUnit = 3.sp,
    strokeWidth: Dp = 0.dp,
    glowAlpha: Float = 0.9f,
    fillColor: Color = Color.White,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    val density = LocalDensity.current
    val base = TextStyle(
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        textAlign = TextAlign.Center,
    )
    val brush = Brush.linearGradient(
        colors = gradient,
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    val glowColor = gradient.getOrNull(1) ?: gradient.first()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            text = text,
            style = base.copy(
                brush = brush,
                shadow = Shadow(
                    color = glowColor.copy(alpha = 0.55f * glowAlpha),
                    offset = Offset.Zero,
                    blurRadius = with(density) { 22.dp.toPx() },
                ),
            ),
        )
    }
}
