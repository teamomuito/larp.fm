package io.github.teamomuito.larpfm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// "Liquid glass" without live blur: translucent panels with a lit edge over a still, colourful
// backdrop. Everything is a plain gradient, so it costs about the same to draw as flat colours.

private val Pink = Color(0xFFFF4FA3)
private val Violet = Color(0xFF8B5CF6)
private val Peach = Color(0xFFFF8A65)

/** The colours the glass sits on: a deep gradient with three soft glows. Drawn once per size. */
@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = if (dark) listOf(Color(0xFF1C0B18), Color(0xFF0D0610)) else listOf(Color(0xFFFFF0F6), Color(0xFFF4ECFF))
    val glow = if (dark) 0.42f else 0.30f
    Box(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val backdrop = Brush.verticalGradient(base)
                fun blob(color: Color, alpha: Float, x: Float, y: Float, radius: Float) = Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), Color.Transparent),
                    center = Offset(x, y),
                    radius = radius,
                )
                val pink = blob(Pink, glow, w * 0.05f, h * 0.08f, w * 0.85f)
                val violet = blob(Violet, glow * 0.8f, w * 1.0f, h * 0.42f, w * 0.8f)
                val peach = blob(Peach, glow * 0.6f, w * 0.15f, h * 0.9f, w * 0.75f)
                onDrawBehind {
                    drawRect(backdrop)
                    drawRect(pink)
                    drawRect(violet)
                    drawRect(peach)
                }
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/** How see-through panels are, and the light caught along their edge. */
@Composable
private fun glassFill(tint: Color?): Brush {
    val dark = isSystemInDarkTheme()
    val base = tint ?: Color.White
    return if (dark) {
        Brush.verticalGradient(listOf(base.copy(alpha = if (tint == null) 0.13f else 0.30f), base.copy(alpha = if (tint == null) 0.05f else 0.18f)))
    } else {
        Brush.verticalGradient(listOf(base.copy(alpha = if (tint == null) 0.72f else 0.85f), base.copy(alpha = if (tint == null) 0.42f else 0.6f)))
    }
}

@Composable
private fun glassEdge(): Brush {
    val dark = isSystemInDarkTheme()
    val light = Color.White
    return Brush.linearGradient(
        listOf(
            light.copy(alpha = if (dark) 0.45f else 0.95f),
            light.copy(alpha = if (dark) 0.06f else 0.35f),
            light.copy(alpha = if (dark) 0.18f else 0.7f),
        ),
    )
}

/** Adds the glass look to anything: see-through fill and a lit 1dp edge. */
@Composable
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), tint: Color? = null): Modifier =
    this
        .clip(shape)
        .background(glassFill(tint))
        .border(1.dp, glassEdge(), shape)

/** A frosted panel. [tint] colours the glass, e.g. for a warning. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.glass(tint = tint).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A small heading above a group of panels. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 8.dp, top = 12.dp),
    )
}

/** Tinted-glass colours for secondary buttons, so they sit in the panels instead of on them. */
@Composable
fun glassButtonColors(): ButtonColors = ButtonDefaults.filledTonalButtonColors(
    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/** A pill of choices with the selected one filled in, e.g. Last.fm / Libre.fm. */
@Composable
fun <T> GlassSegments(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    Row(Modifier.glass(CircleShape).padding(4.dp)) {
        for (option in options) {
            val isSelected = option == selected
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable(enabled = enabled && !isSelected) { onSelect(option) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
