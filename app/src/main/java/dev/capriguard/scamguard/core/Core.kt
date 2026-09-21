@file:Suppress("DEPRECATION")

package dev.capriguard.scamguard.core

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/* ------------------------------------------------------------------ palette */

/**
 * One accent, one cool-grey family. The accent is spent only where the money
 * is: primary action, active state, progress.
 */
object Palette {
    val Capri = Color(0xFF17A2A8)
    val CapriBright = Color(0xFF4FE0E4)
    val CapriDeep = Color(0xFF0B6C72)

    val Ink1 = Color(0xFF07161A)
    val Ink2 = Color(0xFF10262B)
    val Ink3 = Color(0xFF1A353C)

    val Paper1 = Color(0xFFF6FAFB)
    val Paper2 = Color(0xFFECF2F4)

    val TextHiDark = Color(0xFFF2F8F9)
    val TextLoDark = Color(0xFF9FB6BB)
    val TextHiLight = Color(0xFF06171A)
    val TextLoLight = Color(0xFF4E6A70)

    /** Signal severity only — never decoration. */
    val Calm = Color(0xFF3FB27F)
    val Warm = Color(0xFFE8A33D)
    val Alert = Color(0xFFE2604F)
}

/** What a surface needs to stay legible while floating over photography. */
data class GlassTokens(
    val scrim: Color,
    val sheenTop: Color,
    val sheenFade: Color,
    val hairline: Color,
    val shadow: Color,
)

val LightGlass = GlassTokens(
    // A white scrim on a pale background has no edge to read from, so light mode
    // carries its separation in the hairline and the shadow instead.
    scrim = Color(0xF0FFFFFF),
    sheenTop = Color(0xFFFFFEFB),
    sheenFade = Color(0x14FFFFFF),
    hairline = Color(0x5208171A),
    shadow = Color(0x3308171A),
)

val DarkGlass = GlassTokens(
    scrim = Color(0xB310262B),
    sheenTop = Color(0x2EFFFFFF),
    sheenFade = Color(0x0AFFFFFF),
    hairline = Color(0x33FFFFFF),
    shadow = Color(0x73000000),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/* ------------------------------------------------------------------- shapes */

/**
 * Superellipse outline — the continuous corner. A plain rounded rect changes
 * curvature abruptly where the arc meets the straight edge, and that seam is
 * what makes a card read as generic. Exponent 2.0 is a true ellipse, 3.5+
 * approaches a rectangle; 2.6 is the legible middle.
 */
class ContinuousRoundedShape(private val radius: () -> Dp, private val exponent: Float = 2.6f) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val r = with(density) { radius().toPx() }.coerceAtMost(minOf(size.width, size.height) / 2f)
        if (r <= 0.5f) {
            path.addRect(Rect(0f, 0f, size.width, size.height))
            return Outline.Generic(path)
        }
        val e = 2f / exponent
        fun signedPow(v: Double) = abs(v).pow(e.toDouble()).let { if (v < 0) -it else it }

        val corners = arrayOf(
            doubleArrayOf(r.toDouble(), r.toDouble(), 180.0, 270.0),
            doubleArrayOf((size.width - r).toDouble(), r.toDouble(), 270.0, 360.0),
            doubleArrayOf((size.width - r).toDouble(), (size.height - r).toDouble(), 0.0, 90.0),
            doubleArrayOf(r.toDouble(), (size.height - r).toDouble(), 90.0, 180.0),
        )
        var started = false
        for (c in corners) {
            val steps = 18
            for (i in 0..steps) {
                val a = Math.toRadians(c[2] + (c[3] - c[2]) * i / steps)
                val x = c[0] + r * signedPow(cos(a))
                val y = c[1] + r * signedPow(sin(a))
                if (!started) {
                    path.moveTo(x.toFloat(), y.toFloat()); started = true
                } else {
                    path.lineTo(x.toFloat(), y.toFloat())
                }
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** The radius scale, stated once. Mixed radii without a rule read as assembled. */
object Radii {
    val CardShape = ContinuousRoundedShape({ 22.dp })
    val ControlShape = ContinuousRoundedShape({ 15.dp })
    val PillShape = ContinuousRoundedShape({ 9999.dp })
}

private fun Shape.pathFor(size: Size, layoutDirection: LayoutDirection, density: Density): Path =
    when (val o = createOutline(size, layoutDirection, density)) {
        is Outline.Rectangle -> Path().apply { addRect(o.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
        is Outline.Generic -> o.path
    }

/* ------------------------------------------------------------------- glass */

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radii.CardShape,
    elevation: Dp = 14.dp,
    padding: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalGlass.current
    Box(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = tokens.shadow, spotColor = tokens.shadow)
            .drawBehind {
                val p = shape.pathFor(size, layoutDirection, this)
                drawPath(p, SolidColor(tokens.scrim))
                drawPath(
                    p,
                    Brush.verticalGradient(
                        colors = listOf(tokens.sheenTop, tokens.sheenFade, tokens.sheenFade),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
            .border(0.7.dp, tokens.hairline, shape)
            .padding(padding),
        content = content,
    )
}

/** Compact glass control: same material as a card, one step lower. */
@Composable
fun GlassChip(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalGlass.current
    Box(
        modifier = modifier
            .drawBehind {
                drawPath(Radii.PillShape.pathFor(size, layoutDirection, this), SolidColor(tokens.scrim))
            }
            .border(0.7.dp, tokens.hairline, Radii.PillShape)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        content = content,
    )
}

/** Deep backdrop: base colour plus two light pools, so glass has something to float on. */
@Composable
fun LiquidBackdrop(
    base: Color,
    modifier: Modifier = Modifier,
    dark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit,
) {
    val pool = if (dark) 0.20f else 0.34f
    val deep = if (dark) 0.26f else 0.22f
    Box(modifier = modifier.fillMaxSize().background(base)) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Palette.Capri.copy(alpha = pool), Color.Transparent),
                            center = Offset(size.width * 0.14f, size.height * 0.08f),
                            radius = size.width * 0.95f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(Palette.CapriDeep.copy(alpha = deep), Color.Transparent),
                            center = Offset(size.width * 0.92f, size.height * 0.88f),
                            radius = size.width * 1.12f,
                        ),
                    )
                    if (!dark) {
                        // Light mode needs a mid-field pool, or the cards float on white.
                        drawRect(
                            Brush.radialGradient(
                                colors = listOf(Palette.Capri.copy(alpha = 0.16f), Color.Transparent),
                                center = Offset(size.width * 0.55f, size.height * 0.42f),
                                radius = size.width * 0.8f,
                            ),
                        )
                    }
                },
        )
        content()
    }
}

/* -------------------------------------------------------------- typography */

private val sans = FontFamily.SansSerif

val GuardTypography = Typography(
    displaySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 19.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp),
)

/** Figures the user compares must share advance width. */
val Numeric = TextStyle(fontFeatureSettings = "tnum")

fun androidx.compose.ui.text.TextStyle.tnum(): androidx.compose.ui.text.TextStyle =
    copy(fontFeatureSettings = if (fontFeatureSettings == null) "tnum" else "$fontFeatureSettings,tnum")

@Composable
fun GuardTheme(
    dark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = Palette.CapriBright,
            onPrimary = Palette.Ink1,
            background = Palette.Ink1,
            onBackground = Palette.TextHiDark,
            surface = Palette.Ink2,
            onSurface = Palette.TextHiDark,
            surfaceVariant = Palette.Ink3,
            onSurfaceVariant = Palette.TextLoDark,
            outline = Color(0xFF2A4B53),
        )
    } else {
        lightColorScheme(
            primary = Palette.CapriDeep,
            onPrimary = Color.White,
            background = Palette.Paper1,
            onBackground = Palette.TextHiLight,
            surface = Color.White,
            onSurface = Palette.TextHiLight,
            surfaceVariant = Palette.Paper2,
            onSurfaceVariant = Palette.TextLoLight,
            outline = Color(0xFFC9D6DA),
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = GuardTypography,
        shapes = Shapes(
            small = CircleShape,
            medium = RoundedCornerShape(15.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(22.dp),
        ),
    ) {
        CompositionLocalProvider(LocalGlass provides if (dark) DarkGlass else LightGlass) {
            content()
        }
    }
}

@Composable
fun Labeled(label: String, value: String, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(0.44f)) {
            Text(label, style = MaterialTheme.typography.labelMedium.tnum(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.bodyMedium.tnum(), color = tint, modifier = Modifier.weight(1f))
    }
}
