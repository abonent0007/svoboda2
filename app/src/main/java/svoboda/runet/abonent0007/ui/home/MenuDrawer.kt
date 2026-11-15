package svoboda.runet.abonent0007.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import svoboda.runet.abonent0007.ui.theme.Border
import svoboda.runet.abonent0007.ui.theme.Elevated
import svoboda.runet.abonent0007.ui.theme.GoodGreen
import svoboda.runet.abonent0007.ui.theme.TextPrimary
import svoboda.runet.abonent0007.ui.theme.TextSecondary

/**
 * Плитка — одна строка. На главном экране полупрозрачная,
 * в меню (opaque=true) — практически непрозрачная.
 */
@Composable
fun TileRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    opaque: Boolean = false,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (opaque) Elevated else Elevated.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (value != null) {
                    Text(
                        text = value,
                        color = TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            control?.invoke()
        }
    }
}

/**
 * Ползунок в стиле приложения: серый = выключено,
 * зелёный со свечением = включено. Чуть компактнее стандартного.
 */
@Composable
fun NeonSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .scale(0.85f)
            .drawBehind {
                if (checked) {
                    drawCircle(
                        color = GoodGreen.copy(alpha = 0.25f),
                        radius = size.maxDimension * 0.95f,
                        center = center,
                    )
                }
            },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFFE9FFF5),
                checkedTrackColor = GoodGreen,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFF8FA1B3),
                uncheckedTrackColor = Color(0xFF2A3644),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/**
 * Карточка со слайдером: значение показывается один раз — крупно
 * справа от названия, мин/макс — под ползунком.
 */
@Composable
fun SliderCard(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    minLabel: String,
    maxLabel: String,
    opaque: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (opaque) Elevated else Elevated.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, Border),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = valueText,
                    color = GoodGreen,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = GoodGreen,
                    activeTrackColor = GoodGreen,
                    inactiveTrackColor = Color(0xFF2A3644),
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = minLabel, color = TextSecondary, fontSize = 11.sp)
                Text(text = maxLabel, color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}
