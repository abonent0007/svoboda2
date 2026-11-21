package svoboda3.runet.abonent0007.ui.method

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import svoboda3.runet.abonent0007.ui.theme.AccentGreen
import svoboda3.runet.abonent0007.ui.theme.Border
import svoboda3.runet.abonent0007.ui.theme.Elevated
import svoboda3.runet.abonent0007.ui.theme.TextPrimary
import svoboda3.runet.abonent0007.ui.theme.TextSecondary

/**
 * Единая кнопка-чип для настроек: всегда с контуром и фоном (не «голый текст»).
 * [selected] — акцентная подсветка выбранного состояния.
 */
@Composable
fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = when {
            selected -> AccentGreen.copy(alpha = 0.18f)
            else -> Elevated.copy(alpha = 0.5f)
        },
        border = BorderStroke(1.dp, if (selected) AccentGreen else Border),
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> TextSecondary.copy(alpha = 0.5f)
                selected -> AccentGreen
                else -> TextPrimary
            },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}
