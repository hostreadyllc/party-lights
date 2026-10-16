package com.ash.partylights

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything an effect needs to draw one frame. */
class Frame(
    val mode: Mode,
    val phase: Float,      // show time scaled by speed, in seconds
    val pulse: Float,      // 1 right on a beat, decays towards 0
    val beat: Int,         // beat counter
    val colors: List<Color>,
    val safe: Boolean,
    val text: String,
    val brightness: Float,
)

private fun fract(x: Float) = x - floor(x)

fun List<Color>.at(x: Float): Color {
    if (isEmpty()) return Color.White
    if (size == 1) return this[0]
    val f = fract(x) * size
    val i = f.toInt() % size
    return lerp(this[i], this[(i + 1) % size], f - floor(f))
}

private fun hash(a: Int, b: Int, c: Int): Int {
    var h = a * 374761393 + b * 668265263 + c * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    return (h xor (h ushr 16)) and 0x7fffffff
}

fun DrawScope.drawEffect(f: Frame, measurer: TextMeasurer) {
    drawRect(Color.Black)
    when (f.mode) {
        Mode.STROBE -> strobe(f)
        Mode.DISCO -> disco(f)
        Mode.LASERS -> lasers(f)
        Mode.SWIRL -> swirl(f)
        Mode.KALEIDO -> kaleido(f)
        Mode.TUNNEL -> tunnel(f)
        Mode.AURORA -> aurora(f)
        Mode.LAVA -> lava(f)
        Mode.BANNER -> banner(f, measurer)
    }
    if (f.brightness < 1f) drawRect(Color.Black.copy(alpha = (1f - f.brightness).coerceIn(0f, 0.9f)))
}

private fun DrawScope.strobe(f: Frame) {
    val x = f.phase * 4f
    val k = floor(x).toInt()
    val on = fract(x) < 0.35f || f.pulse > 0.75f
    if (on) {
        val c = f.colors[(k + f.beat) % f.colors.size]
        drawRect(if (k % 5 == 4) Color.White else c)
    }
}

private fun DrawScope.disco(f: Frame) {
    val cols = 4
    val cell = size.width / cols
    val rows = (size.height / cell).toInt() + 1
    val gap = cell * 0.06f
    val n = f.colors.size
    for (r in 0 until rows) for (c in 0 until cols) {
        val cur = hash(c, r, f.beat)
        val prev = hash(c, r, f.beat - 1)
        val curColor = if (cur % 4 == 0) Color.Black else f.colors[cur % n]
        val prevColor = if (prev % 4 == 0) Color.Black else f.colors[prev % n]
        // Safe mode cross-fades between beats instead of snapping.
        val color = if (f.safe) lerp(curColor, prevColor, f.pulse) else curColor
        val glow = 0.65f + 0.35f * f.pulse
        drawRoundRect(
            color = color.copy(alpha = glow),
            topLeft = Offset(c * cell + gap, r * cell + gap),
            size = Size(cell - 2 * gap, cell - 2 * gap),
            cornerRadius = CornerRadius(cell * 0.18f),
        )
    }
}

private fun DrawScope.lasers(f: Frame) {
    val w = size.width
    val h = size.height
    val len = hypot(w, h) * 1.3f
    val origins = listOf(
        Offset(0f, h) to 45f, Offset(w, h) to 135f, Offset(w / 2, h) to 90f, Offset(w / 2, 0f) to 270f,
    )
    origins.forEachIndexed { o, (origin, base) ->
        for (b in 0 until 6) {
            val deg = base + sin(f.phase * 0.8f + b * 0.9f + o * 1.7f) * 40f + (b - 2.5f) * 7f
            val a = deg * PI.toFloat() / 180f
            val end = origin + Offset(cos(a) * len, -sin(a) * len)
            val color = f.colors.at(b / 6f + f.phase * 0.05f + o * 0.27f)
            val width = 1f + f.pulse
            drawLine(color.copy(alpha = 0.18f), origin, end, strokeWidth = 34f * width, blendMode = BlendMode.Plus)
            drawLine(color.copy(alpha = 0.5f), origin, end, strokeWidth = 12f * width, blendMode = BlendMode.Plus)
            drawLine(color, origin, end, strokeWidth = 4f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
        }
    }
    drawCircle(Color.White.copy(alpha = 0.15f * f.pulse), radius = max(w, h))
}

private fun DrawScope.swirl(f: Frame) {
    val c = center
    val maxR = hypot(size.width, size.height) / 2f
    rotate(f.phase * 50f, c) {
        drawCircle(Brush.sweepGradient(f.colors + f.colors.first(), c), radius = maxR * 1.05f, center = c)
    }
    val arms = 6
    val strokeW = maxR / 9f
    for (arm in 0 until arms) {
        val path = Path()
        for (s in 0..60) {
            val r = s / 60f * maxR * 1.05f
            val th = arm * 2f * PI.toFloat() / arms + s / 60f * 7f - f.phase * 2.2f
            val p = Offset(c.x + cos(th) * r, c.y + sin(th) * r)
            if (s == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, Color.Black.copy(alpha = 0.6f), style = Stroke(width = strokeW, cap = StrokeCap.Round))
    }
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.25f + 0.5f * f.pulse), Color.Transparent), c, maxR * 0.35f),
        radius = maxR * 0.35f, center = c,
    )
}

private fun DrawScope.kaleido(f: Frame) {
    val c = center
    val maxR = hypot(size.width, size.height) / 2f
    rotate(-f.phase * 15f, c) {
        drawCircle(Brush.sweepGradient(f.colors + f.colors.first(), c), radius = maxR, center = c, alpha = 0.25f)
    }
    val seg = 12
    for (s in 0 until seg) {
        rotate(s * 360f / seg + f.phase * 10f, c) {
            scale(if (s % 2 == 0) 1f else -1f, 1f, c) {
                for (j in 0 until 7) {
                    val d = fract(f.phase * 0.12f + j / 7f) * maxR
                    val ang = (8f + sin(f.phase * 0.9f + j * 1.3f) * 7f) * PI.toFloat() / 180f
                    val p = Offset(c.x + cos(ang) * d, c.y + sin(ang) * d)
                    val rad = (10f + 40f * (0.5f + 0.5f * sin(f.phase * 1.3f + j))) * (d / maxR + 0.3f) * (1f + 0.4f * f.pulse)
                    drawCircle(f.colors.at(j / 7f + f.phase * 0.08f), radius = rad, center = p,
                        alpha = 0.75f, blendMode = BlendMode.Plus)
                }
            }
        }
    }
}

private fun DrawScope.tunnel(f: Frame) {
    val rings = 18
    val maxR = hypot(size.width, size.height) / 2f * 1.25f
    val t = f.phase * 0.6f
    val base = floor(t).toInt()
    val fr = t - base
    for (i in rings - 1 downTo 0) {
        val p = (i + fr) / rings
        val r = p * p * maxR
        val k = i - base
        val wobble = 1f - p
        val cc = Offset(
            center.x + sin(f.phase * 0.5f) * size.width * 0.15f * wobble,
            center.y + cos(f.phase * 0.4f) * size.height * 0.1f * wobble,
        )
        val col = f.colors.at(k * 0.13f)
        val shade = if (k % 2 == 0) col else lerp(col, Color.Black, 0.8f)
        drawCircle(shade, radius = r * (1f + 0.04f * f.pulse), center = cc)
    }
}

private fun DrawScope.aurora(f: Frame) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF020014), Color(0xFF000814))))
    for (b in 0 until 5) {
        val path = Path()
        val steps = 40
        val top = FloatArray(steps + 1) { s ->
            val x = s / steps.toFloat()
            h * (0.12f + 0.17f * b) +
                sin(x * 2f * PI.toFloat() * 1.2f + f.phase * 0.5f + b * 1.1f) * h * 0.07f +
                sin(x * 2f * PI.toFloat() * 2.7f - f.phase * 0.33f + b) * h * 0.03f
        }
        val thick = h * 0.3f
        path.moveTo(0f, top[0])
        for (s in 1..steps) path.lineTo(w * s / steps, top[s])
        for (s in steps downTo 0) path.lineTo(w * s / steps, top[s] + thick)
        path.close()
        val col = f.colors.at(b / 5f + f.phase * 0.03f)
        val minTop = top.min()
        drawPath(
            path,
            Brush.verticalGradient(listOf(col.copy(alpha = 0f), col.copy(alpha = 0.55f + 0.3f * f.pulse), col.copy(alpha = 0f)),
                startY = minTop - h * 0.05f, endY = minTop + thick + h * 0.1f),
            blendMode = BlendMode.Plus,
        )
    }
}

private fun DrawScope.lava(f: Frame) {
    val w = size.width
    val h = size.height
    for (i in 0 until 7) {
        val sp = 0.25f * (1f + i * 0.13f)
        val p = Offset(
            w * (0.5f + 0.42f * sin(f.phase * sp + i * 1.9f)),
            h * (0.5f + 0.42f * cos(f.phase * sp * 0.8f + i * 2.3f)),
        )
        val r = min(w, h) * (0.35f + 0.12f * sin(f.phase * 0.5f + i)) * (1f + 0.15f * f.pulse)
        val col = f.colors.at(i / 7f + f.phase * 0.02f)
        drawCircle(Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), p, r), radius = r, center = p,
            blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.banner(f: Frame, measurer: TextMeasurer) {
    val text = f.text.ifBlank { "PARTY" }.uppercase()
    val portrait = size.height > size.width
    // Run the text along the long side of the screen.
    val boxW = if (portrait) size.height else size.width
    val boxH = if (portrait) size.width else size.height
    val left = center.x - boxW / 2
    val top = center.y - boxH / 2
    val fontPx = boxH * 0.55f * (1f + 0.08f * f.pulse)
    val annotated: AnnotatedString = buildAnnotatedString {
        text.forEachIndexed { i, ch ->
            val col = f.colors.at(i * 0.09f + f.phase * 0.15f)
            withStyle(SpanStyle(color = col, shadow = Shadow(col, Offset.Zero, blurRadius = fontPx * 0.25f))) {
                append(ch)
            }
        }
        append("     ")
    }
    val layout = measurer.measure(
        annotated,
        style = TextStyle(fontSize = fontPx.toSp(), fontWeight = FontWeight.Black),
        softWrap = false,
        maxLines = 1,
    )
    val tw = layout.size.width.toFloat()
    val y = top + (boxH - layout.size.height) / 2f
    val scroll = (f.phase * boxW * 0.3f) % tw
    withTransform({ if (portrait) rotate(90f, center) }) {
        var x = left - scroll
        while (x < left + boxW) {
            drawText(layout, topLeft = Offset(x, y))
            x += tw
        }
    }
}
