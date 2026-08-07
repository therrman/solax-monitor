package cz.solax.monitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Karta se záhlavím sekce a volitelným stavovým odznakem vpravo. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.weight(1f),
                )
                badge?.invoke()
            }
            Spacer(Modifier.width(8.dp))
            HorizontalDivider(
                Modifier.padding(top = 8.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )
            content()
        }
    }
}

/** Velké číslo s jednotkou - pro hodnoty, které chceš vidět na první pohled. */
@Composable
fun BigValue(
    value: String,
    unit: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    caption: String? = null,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(
            " $unit",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 5.dp),
        )
        if (caption != null) {
            Spacer(Modifier.weight(1f))
            Text(
                caption,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

/** Řádek popisek - hodnota. Hodnota je monospace, ať čísla neposkakují. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

/** Barevný odznak stavu. */
@Composable
fun Badge(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** Hlavička tabulky fází. */
@Composable
fun PhaseHeader(vararg columns: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        columns.forEachIndexed { i, c ->
            Text(
                c,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = Modifier.weight(if (i == 0) 0.7f else 1f),
            )
        }
    }
}

/** Řádek tabulky fází. */
@Composable
fun PhaseRow(label: String, vararg values: String, valueColor: Color? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.7f),
        )
        values.forEach { v ->
            Text(
                v,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
