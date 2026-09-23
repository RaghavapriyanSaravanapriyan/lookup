package dev.lookup.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** the only palette in the app shell. charts may colour; chrome may not. */
object Term {
    val Bg = Color(0xFF000000)
    val Panel = Color(0xFF0B0C0E)
    val Panel2 = Color(0xFF131417)
    val Hairline = Color(0xFF26292F)
    val Grid = Color(0xFF1A1C20)
    val Ink = Color(0xFFFFFFFF)
    val Dim = Color(0xFFA1A1AA)
    val Faint = Color(0xFF52525B)
    val InvertBg = Color(0xFFFFFFFF)
    val InvertInk = Color(0xFF000000)

    // chart-only colour. never use for text, buttons, borders.
    val Blue = Color(0xFF3B82F6)
    val BlueDeep = Color(0xFF2563EB)
    val Amber = Color(0xFFFBBF24)
    val AmberDeep = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val RedDeep = Color(0xFFDC2626)

    val Mono = FontFamily.Monospace
}

fun tapeColorFor(confidence: Float): Color = when {
    confidence < 40f -> Term.Blue
    confidence < 70f -> Term.Amber
    else -> Term.Red
}

fun zoneLabel(confidence: Float): String = when {
    confidence < 30f -> "CLEAR"
    confidence < 40f -> "WATCH"
    confidence < 70f -> "OMEN"
    else -> "RED CANDLE"
}

/** mythical oracle one-liners. rotates with the doomscroll index. */
fun oracleLine(confidence: Float): String = when {
    confidence < 15f -> "the oracle is bored. no omens. touch grass, or don't."
    confidence < 30f -> "a faint rustle in the tape. your neck is still yours."
    confidence < 40f -> "the floor stirs. pocket or pilgrimage — undecided."
    confidence < 55f -> "omen rising. the sidewalk senses your glow."
    confidence < 70f -> "amber tape. the pigeons are taking bets."
    confidence < 85f -> "RED CANDLE FORMING. eyes up, apex predator."
    else -> "MAXIMUM DOOMSCROLL. the oracle begs: LOOK UP."
}

@Composable
fun Panel(
    title: String,
    right: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Term.Panel, RoundedCornerShape(10.dp))
            .border(1.dp, Term.Hairline, RoundedCornerShape(10.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(Term.Ink, RoundedCornerShape(1.dp))
                    .padding(0.dp),
            ) {}
            Text(
                text = title,
                fontFamily = Term.Mono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Term.Dim,
                modifier = Modifier.weight(1f),
            )
            if (right != null) {
                Text(
                    text = right,
                    fontFamily = Term.Mono,
                    fontSize = 10.sp,
                    color = Term.Faint,
                    letterSpacing = 1.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .background(Term.Hairline)
                .padding(0.dp)
                .background(Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Term.Hairline)
                    .padding(top = 0.dp),
            ) {}
        }
        content()
    }
}

@Composable
fun QuoteCell(
    label: String,
    valuePct: String,
    fraction: Float,
    sub: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Term.Panel2, RoundedCornerShape(8.dp))
            .border(1.dp, Term.Hairline, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        Text(
            text = label,
            fontFamily = Term.Mono,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            color = Term.Faint,
        )
        Text(
            text = valuePct,
            fontFamily = Term.Mono,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Term.Ink,
        )
        DepthBar(fraction = fraction)
        Text(
            text = sub,
            fontFamily = Term.Mono,
            fontSize = 9.sp,
            color = Term.Faint,
        )
    }
}

@Composable
fun DepthBar(fraction: Float, modifier: Modifier = Modifier) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Term.Grid, RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(f)
                .background(Term.Ink, RoundedCornerShape(2.dp))
                .padding(vertical = 2.dp),
        ) {}
    }
}

@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            fontFamily = Term.Mono,
            fontSize = 9.sp,
            letterSpacing = 2.sp,
            color = Term.Faint,
        )
        Text(
            text = value,
            fontFamily = Term.Mono,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Term.Ink,
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Term.Hairline)
            .padding(vertical = 0.5.dp),
    ) {}
}

@Composable
fun SectionRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = Term.Mono,
            fontSize = 10.sp,
            letterSpacing = 1.5.sp,
            color = Term.Faint,
        )
        Text(
            text = value,
            fontFamily = Term.Mono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Term.Ink,
        )
    }
}

@Composable
fun LiveDot(active: Boolean, modifier: Modifier = Modifier) {
    val c = if (active) Term.Ink else Term.Faint
    Box(
        modifier = modifier
            .drawBehind {
                drawCircle(color = c, radius = size.minDimension / 2f)
            }
            .padding(4.dp),
    ) {}
}

@Composable
fun HaltBanner(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    glyph: String = "[ ! ]",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, Term.Ink, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = glyph,
            fontFamily = Term.Mono,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Term.Ink,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = Term.Mono,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Term.Ink,
            )
            Text(
                text = body,
                fontSize = 12.sp,
                color = Term.Dim,
            )
        }
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    fontFamily = Term.Mono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Term.Ink,
                )
            }
        }
    }
}
