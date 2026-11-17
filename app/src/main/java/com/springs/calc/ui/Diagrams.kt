package com.springs.calc.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

private class Pal(val line: Color, val dim: Color, val text: Color)

@Composable
private fun DiagramCard(title: String, legend: List<String>, heightDp: Int, aspect: Float? = null, draw: DrawScope.(TextMeasurer, Pal) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val tm = rememberTextMeasurer()
    val pal = Pal(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (open) {
                Canvas(Modifier.fillMaxWidth().then(if (aspect != null) Modifier.aspectRatio(aspect) else Modifier.height(heightDp.dp))) { draw(tm, pal) }
                legend.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

// ---------- 繪圖工具 ----------
private fun DrawScope.dim(a: Offset, b: Offset, c: Color) {
    val sw = 1.5.dp.toPx()
    drawLine(c, a, b, sw)
    val v = b - a; val len = v.getDistance(); if (len < 1f) return
    val u = v / len; val n = Offset(-u.y, u.x); val s = 7.dp.toPx()
    drawLine(c, a, a + u * s + n * (s * 0.4f), sw); drawLine(c, a, a + u * s - n * (s * 0.4f), sw)
    drawLine(c, b, b - u * s + n * (s * 0.4f), sw); drawLine(c, b, b - u * s - n * (s * 0.4f), sw)
}

private fun DrawScope.arrow(a: Offset, b: Offset, c: Color) {
    val sw = 1.5.dp.toPx()
    drawLine(c, a, b, sw)
    val v = b - a; val len = v.getDistance(); if (len < 1f) return
    val u = v / len; val n = Offset(-u.y, u.x); val s = 7.dp.toPx()
    drawLine(c, b, b - u * s + n * (s * 0.4f), sw); drawLine(c, b, b - u * s - n * (s * 0.4f), sw)
}

private fun DrawScope.guide(a: Offset, b: Offset, c: Color) =
    drawLine(c, a, b, 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))

private fun DrawScope.txt(tm: TextMeasurer, s: String, at: Offset, c: Color) {
    val st = TextStyle(color = c, fontSize = 13.sp)
    val sz = tm.measure(s, st).size
    drawText(tm, s, Offset(at.x - sz.width / 2f, at.y - sz.height / 2f), st)
}

private fun DrawScope.wireSection(tm: TextMeasurer, p: Pal, c: Offset) {
    val r = 9.dp.toPx()
    drawCircle(p.line, r, c, style = Stroke(2.dp.toPx()))
    drawCircle(p.line, r * 0.15f, c)
    txt(tm, "d", Offset(c.x, c.y + r + 12.dp.toPx()), p.dim)
}

// ---------- 壓縮彈簧 ----------
@Composable
fun CompressionDiagram() = DiagramCard(
    "參數位置圖", listOf(
        "d 線徑｜OD 外徑｜D 中徑 = OD − d",
        "L0 自由長（未受力）｜L 工作長度（受壓後）",
        "Na 有效圈數（不含兩端閉合圈）",
    ), 290,
) { tm, p ->
    val w = size.width; val h = size.height; val wire = 5.dp.toPx()
    val cx = w * 0.40f; val half = w * 0.17f; val top = h * 0.28f; val bot = h * 0.78f
    val outer = half + wire / 2   // 線材外緣 = OD/2
    // 端圈（閉合磨平）：寬度與外徑一致
    drawLine(p.line, Offset(cx - outer, top), Offset(cx + outer, top), wire, StrokeCap.Butt)
    drawLine(p.line, Offset(cx - outer, bot), Offset(cx + outer, bot), wire, StrokeCap.Butt)
    val n = 7; val path = Path()
    for (i in 0..n) {
        val y = top + (bot - top) * i / n
        val x = if (i % 2 == 0) cx - half else cx + half
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, p.line, style = Stroke(wire, cap = StrokeCap.Round, join = StrokeJoin.Round))
    val xL0 = w * 0.82f; val xL = w * 0.68f; val lBot = bot - (bot - top) * 0.30f
    guide(Offset(cx + outer, top), Offset(xL0 + 6.dp.toPx(), top), p.dim)
    guide(Offset(cx + outer, bot), Offset(xL0 + 6.dp.toPx(), bot), p.dim)
    guide(Offset(cx + half, lBot), Offset(xL + 6.dp.toPx(), lBot), p.dim)
    dim(Offset(xL0, top), Offset(xL0, bot), p.dim); txt(tm, "L0", Offset(xL0 + 16.dp.toPx(), (top + bot) / 2), p.dim)
    dim(Offset(xL, top), Offset(xL, lBot), p.dim); txt(tm, "L", Offset(xL + 14.dp.toPx(), (top + lBot) / 2), p.dim)
    val yOD = h * 0.10f
    guide(Offset(cx - outer, yOD), Offset(cx - outer, top), p.dim)
    guide(Offset(cx + outer, yOD), Offset(cx + outer, top), p.dim)
    dim(Offset(cx - outer, yOD), Offset(cx + outer, yOD), p.dim)
    txt(tm, "OD", Offset(cx, yOD + 14.dp.toPx()), p.dim)
    val yD = h * 0.90f
    guide(Offset(cx - half, bot), Offset(cx - half, yD), p.dim); guide(Offset(cx + half, bot), Offset(cx + half, yD), p.dim)
    dim(Offset(cx - half, yD), Offset(cx + half, yD), p.dim)
    txt(tm, "D", Offset(cx + half + 16.dp.toPx(), yD), p.dim)
    wireSection(tm, p, Offset(w * 0.09f, h * 0.50f))
}

// ---------- 扭力彈簧 ----------
@Composable
fun TorsionDiagram() = DiagramCard(
    "參數位置圖（俯視）", listOf(
        "d 線徑｜OD 外徑｜D 中徑 = OD − d｜N 本體圈數",
        "L1、L2 兩支臂長（由本體切點量到臂端）",
        "θ 扭轉角｜R 受力點半徑（由彈簧中心到負荷點，F 垂直於臂）",
    ), 330,
) { tm, p ->
    val w = size.width; val h = size.height
    val cx = w * 0.34f; val cy = h * 0.50f; val r = h * 0.22f; val wire = h * 0.04f; val rm = r - wire / 2
    drawCircle(p.line, r, Offset(cx, cy), style = Stroke(2.dp.toPx()))
    drawCircle(p.line, r - wire, Offset(cx, cy), style = Stroke(2.dp.toPx()))
    drawCircle(p.dim, rm, Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
    val p1 = Offset(cx + rm, cy); val tip1 = Offset(p1.x, cy - h * 0.44f)
    drawLine(p.line, p1, tip1, wire)
    val a = Math.toRadians(50.0).toFloat()
    val p2 = Offset(cx + rm * cos(a), cy - rm * sin(a)); val dir2 = Offset(-sin(a), -cos(a))
    val tip2 = p2 + dir2 * (h * 0.30f)
    drawLine(p.line, p2, tip2, wire)
    txt(tm, "L2", tip2 + dir2 * 14.dp.toPx(), p.dim)
    // 扭轉角 θ：由 leg1 方向轉到虛線方向
    guide(p1, p1 + dir2 * (h * 0.30f), p.dim)
    val ar = 40.dp.toPx()
    drawArc(p.dim, -140f, 50f, false, Offset(p1.x - ar, p1.y - ar), Size(ar * 2, ar * 2), style = Stroke(1.5.dp.toPx()))
    txt(tm, "θ", p1 + Offset(-0.42f, -0.91f) * (ar + 14.dp.toPx()), p.dim)
    // L1、R、F
    val xR = p1.x + 24.dp.toPx(); val xL1 = p1.x + 52.dp.toPx()
    val fy = cy - (cy - tip1.y) * 0.8f
    guide(Offset(p1.x, tip1.y), Offset(xL1 + 6.dp.toPx(), tip1.y), p.dim)
    dim(Offset(xL1, cy), Offset(xL1, tip1.y), p.dim); txt(tm, "L1", Offset(xL1 + 16.dp.toPx(), (cy + tip1.y) / 2), p.dim)
    guide(Offset(p1.x, fy), Offset(xR + 6.dp.toPx(), fy), p.dim)
    dim(Offset(xR, cy), Offset(xR, fy), p.dim); txt(tm, "R", Offset(xR + 12.dp.toPx(), (cy + fy) / 2), p.dim)
    arrow(Offset(p1.x - 46.dp.toPx(), fy), Offset(p1.x - 4.dp.toPx(), fy), p.dim)
    txt(tm, "F", Offset(p1.x - 54.dp.toPx(), fy), p.dim)
    // OD、D
    val yOD = cy + r + 20.dp.toPx(); val yD = yOD + 22.dp.toPx()
    dim(Offset(cx - r, yOD), Offset(cx + r, yOD), p.dim); txt(tm, "OD", Offset(cx + r + 18.dp.toPx(), yOD), p.dim)
    dim(Offset(cx - rm, yD), Offset(cx + rm, yD), p.dim); txt(tm, "D", Offset(cx + rm + 14.dp.toPx(), yD), p.dim)
    wireSection(tm, p, Offset(w * 0.07f, cy))
}

// ---------- 拉伸彈簧 ----------
@Composable
fun ExtensionDiagram() = DiagramCard(
    "參數位置圖", listOf(
        "d 線徑｜OD 外徑｜D 中徑 = OD − d｜Na 有效圈數",
        "L0 自由長（兩鉤內側距離）｜L 工作長度｜Lb 本體長 = (Na+1)d",
        "r1 鉤環中心半徑｜r2 鉤側彎半徑（鉤環與本體之間的彎折處）｜Fi 初張力",
    ), 0, aspect = 360f / 590f,
) { tm, p ->
    // 以 360×590 的設計座標繪製，再依寬度等比縮放（與網頁版 SVG 相同）
    val k = size.width / 360f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    val wire = 5f * k; val big = 9f * k
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
    val cx = 150f; val h = 40f; val hr = 24f; val rr = h - hr; val y1 = 100f; val y2 = 210f; val n = 10
    val step = (y2 - y1) / n
    for (i in 0 until n) {
        val y = y1 + step * i
        drawLine(p.line, o(cx - h, y + 6), o(cx + h, y + 1 + step * 0.5f), wire * 0.8f, StrokeCap.Round)
    }
    drawCircle(p.line, hr * k, o(cx, y1 - hr), style = Stroke(wire))
    drawCircle(p.line, hr * k, o(cx, y2 + hr), style = Stroke(wire))
    val topIn = y1 - 2 * hr; val botIn = y2 + 2 * hr
    // L0（兩鉤內側距離）、Lb（本體長）
    guide(o(cx + hr, topIn), o(305f, topIn), p.dim); guide(o(cx + hr, botIn), o(305f, botIn), p.dim)
    dim(o(295f, topIn), o(295f, botIn), p.dim); txt(tm, "L0", o(322f, (topIn + botIn) / 2), p.dim)
    guide(o(cx + h, y1), o(255f, y1), p.dim); guide(o(cx + h, y2), o(255f, y2), p.dim)
    dim(o(245f, y1), o(245f, y2), p.dim); txt(tm, "Lb", o(268f, (y1 + y2) / 2), p.dim)
    // r1
    drawLine(p.dim, o(cx, y1 - hr), o(cx - hr * 0.7f, y1 - hr - hr * 0.7f), 1.5.dp.toPx())
    txt(tm, "r1", o(cx - hr - 6, y1 - hr - hr * 0.7f - 8), p.dim)
    // 鉤側彎（上下各一）與「A」放大標記
    val bend = Path().apply {
        moveTo(o(cx + h, y1).x, o(cx + h, y1).y)
        arcTo(Rect(o(cx + h - rr, y1), rr * k), 0f, -90f, false)
        lineTo(o(cx + hr, y1 - hr).x, o(cx + hr, y1 - hr).y)
        moveTo(o(cx + h, y2).x, o(cx + h, y2).y)
        arcTo(Rect(o(cx + h - rr, y2), rr * k), 0f, 90f, false)
        lineTo(o(cx + hr, y2 + hr).x, o(cx + hr, y2 + hr).y)
    }
    drawPath(bend, p.line, style = Stroke(wire, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(p.dim, 22f * k, o(cx + h - 6, y1 - 8), style = Stroke(1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * k, 3f * k))))
    txt(tm, "A", o(cx + h + 26, y1 - 26), p.dim)
    // 拉力 F
    arrow(o(cx, topIn - 4), o(cx, topIn - 38), p.dim); txt(tm, "F", o(cx + 14, topIn - 26), p.dim)
    arrow(o(cx, botIn + 4), o(cx, botIn + 38), p.dim); txt(tm, "F", o(cx + 14, botIn + 26), p.dim)
    // 外徑 OD
    guide(o(cx - h, y2), o(cx - h, 320f), p.dim); guide(o(cx + h, y2), o(cx + h, 320f), p.dim)
    dim(o(cx - h, 316f), o(cx + h, 316f), p.dim); txt(tm, "OD", o(cx + h + 20, 316f), p.dim)
    wireSection(tm, p, o(30f, 165f))
    // 局部放大 A：鉤環 r1、鉤側彎 r2
    drawRoundRect(p.dim, o(20f, 335f), Size(320f * k, 238f * k), CornerRadius(12f * k), style = Stroke(1.dp.toPx(), pathEffect = dash))
    txt(tm, "A 鉤環局部放大（r1、r2 位置）", o(180f, 352f), p.dim)
    for ((y, a) in listOf(505f to 1f, 525f to 0.45f, 545f to 0.25f))
        drawLine(p.line, o(60f, y), o(260f, y), big, StrokeCap.Butt, alpha = a)
    drawCircle(p.line, 60f * k, o(160f, 445f), style = Stroke(big))
    val bend2 = Path().apply {
        moveTo(o(260f, 505f).x, o(260f, 505f).y)
        arcTo(Rect(o(220f, 505f), 40f * k), 0f, -90f, false)
        lineTo(o(220f, 445f).x, o(220f, 445f).y)
    }
    drawPath(bend2, p.line, style = Stroke(big, join = StrokeJoin.Round))
    drawCircle(p.dim, 3.5f * k, o(160f, 445f))
    drawLine(p.dim, o(160f, 445f), o(117.6f, 402.6f), 1.5.dp.toPx()); txt(tm, "r1", o(100f, 392f), p.dim)
    drawCircle(p.dim, 40f * k, o(220f, 505f), style = Stroke(1.dp.toPx(), pathEffect = dash))
    drawCircle(p.dim, 3.5f * k, o(220f, 505f))
    drawLine(p.dim, o(220f, 505f), o(248.3f, 476.7f), 2.dp.toPx())
    guide(o(248.3f, 476.7f), o(276f, 452f), p.dim); txt(tm, "r2", o(292f, 446f), p.dim)
    txt(tm, "r1：鉤環中心半徑　r2：鉤側彎半徑", o(180f, 562f), p.dim)
}
