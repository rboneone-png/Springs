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
import androidx.compose.ui.graphics.drawscope.rotate
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
private fun DiagramCard(title: String, legend: List<String>, heightDp: Int, aspect: Float? = null, controls: @Composable () -> Unit = {}, draw: DrawScope.(TextMeasurer, Pal) -> Unit) {
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
                controls()
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

// ---------- 拉伸彈簧（仿實物照片：左圓環鉤、右長開口鉤）----------
@Composable
fun ExtensionDiagram() = DiagramCard(
    "參數位置圖", listOf(
        "d 線徑｜OD 外徑｜D 中徑 = OD − d｜Na 有效圈數",
        "L0 自由長（兩鉤內側距離）｜L 工作長度｜Lb 本體長 = (Na+1)d",
        "r1 鉤環中心半徑｜r2 鉤側彎半徑（鉤環與本體之間的彎折處）｜Fi 初張力",
        "圖左為圓環鉤、圖右為長開口鉤；其他鉤型的 r2 同樣是鉤與本體交接的彎折半徑，r1 為鉤的中心半徑。L（工作長度）與 L0 量法相同，為受拉後兩鉤內側的距離",
    ), 0, aspect = 360f / 372f,
) { tm, p ->
    val k = size.width / 360f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    val wire = 4.6f * k
    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
    fun coils(xl: Float, xr: Float, ya: Float, yb: Float, n: Int) {
        val st = (yb - ya) / n
        for (i in 0 until n) { val y = ya + st * i; drawLine(p.line, o(xl, y + st * 0.9f), o(xr, y + st * 0.1f), wire, StrokeCap.Round) }
    }
    fun Path.mv(x: Float, y: Float) { val q = o(x, y); moveTo(q.x, q.y) }
    fun Path.ln(x: Float, y: Float) { val q = o(x, y); lineTo(q.x, q.y) }
    fun Path.arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) = arcTo(Rect(o(cx, cy), r * k), start, sweep, false)
    val h = 32f; val y1 = 110f; val y2 = 250f; val n = 14
    // 左：圓環鉤
    val c1 = 90f; val r = 20f; val rr = h - r; val x1l = c1 - h; val x1r = c1 + h
    val topIn = y1 - 2 * r + 2.2f; val botIn = y2 + 2 * r - 2.2f
    txt(tm, "圓環鉤", o(c1, 12f), p.dim); txt(tm, "長開口鉤", o(270f, 12f), p.dim)
    coils(x1l, x1r, y1, y2, n)
    drawCircle(p.line, r * k, o(c1, y1 - r), style = Stroke(wire)); drawCircle(p.line, r * k, o(c1, y2 + r), style = Stroke(wire))
    guide(o(c1 - r, topIn), o(18f, topIn), p.dim); guide(o(c1 - r, botIn), o(18f, botIn), p.dim)
    dim(o(26f, topIn), o(26f, botIn), p.dim)
    rotate(-90f, pivot = o(14f, (topIn + botIn) / 2)) { txt(tm, "自由長度 L0", o(14f, (topIn + botIn) / 2), p.dim) }
    guide(o(x1r, y1), o(146f, y1), p.dim); guide(o(x1r, y2), o(146f, y2), p.dim)
    dim(o(140f, y1), o(140f, y2), p.dim); txt(tm, "Lb", o(156f, (y1 + y2) / 2), p.dim)
    arrow(o(152f, 58f), o(x1r - 4, y1 + 6), p.dim); txt(tm, "線徑 d", o(166f, 50f), p.dim)
    arrow(o(c1, y1 - 2 * r - 6), o(c1, y1 - 2 * r - 34), p.dim); txt(tm, "F", o(c1 + 14, y1 - 2 * r - 24), p.dim)
    arrow(o(c1, y2 + 2 * r + 6), o(c1, y2 + 2 * r + 34), p.dim); txt(tm, "F", o(c1 + 14, y2 + 2 * r + 24), p.dim)
    guide(o(x1l, y2), o(x1l, 358f), p.dim); guide(o(x1r, y2), o(x1r, 358f), p.dim)
    dim(o(x1l, 354f), o(x1r, 354f), p.dim); txt(tm, "OD", o(x1r + 20, 354f), p.dim)
    // 右：長開口鉤
    val c2 = 270f; val rr2 = 10f; val rad = 18f; val lx = c2 - h + rr2
    val yt = y1 - rr2 - 30f; val yb = y2 + rr2 + 30f; val x2l = c2 - h; val x2r = c2 + h
    val top2 = yt - rad + 2.2f; val bot2 = yb + rad - 2.2f; val hx = lx + rad
    val kk = rr2 * 0.7071f; val q = rad * 0.7071f
    coils(x2l, x2r, y1, y2, n)
    val b2 = Path().apply {
        mv(lx, y1 + 4); ln(lx, yt); arc(lx + rad, yt, rad, 180f, 180f); ln(lx + 2 * rad, yt + 20)
        mv(lx, y2 - 4); ln(lx, yb); arc(lx + rad, yb, rad, 180f, -180f); ln(lx + 2 * rad, yb - 20)
    }
    drawPath(b2, p.line, style = Stroke(wire, cap = StrokeCap.Round, join = StrokeJoin.Round))
    guide(o(lx + 2 * rad, top2), o(340f, top2), p.dim); guide(o(lx + 2 * rad, bot2), o(340f, bot2), p.dim)
    dim(o(334f, top2), o(334f, bot2), p.dim)
    rotate(-90f, pivot = o(350f, (top2 + bot2) / 2)) { txt(tm, "自由長度 L0", o(350f, (top2 + bot2) / 2), p.dim) }
    drawCircle(p.dim, 3f * k, o(hx, yt)); drawLine(p.dim, o(hx, yt), o(hx - q, yt - q), 1.5.dp.toPx()); txt(tm, "r1", o(hx - q - 14, yt - q - 8), p.dim)
    drawCircle(p.dim, rr2 * k, o(lx, y1 + 4), style = Stroke(1.dp.toPx(), pathEffect = dash)); drawCircle(p.dim, 3f * k, o(lx, y1 + 4))
    drawLine(p.dim, o(lx, y1 + 4), o(lx - kk, y1 + 4 - kk), 2.dp.toPx())
    guide(o(lx - kk, y1 + 4 - kk), o(lx - 30, y1 - 34), p.dim); txt(tm, "r2", o(lx - 42, y1 - 40), p.dim)
    arrow(o(hx, top2 - 6), o(hx, top2 - 28), p.dim); txt(tm, "F", o(hx + 14, top2 - 18), p.dim)
    arrow(o(hx, bot2 + 6), o(hx, bot2 + 28), p.dim); txt(tm, "F", o(hx + 14, bot2 + 18), p.dim)
}

// ---------- 設計頁示意圖 ----------
private val DZ_LEG = listOf(
    listOf("外徑上限：彈簧外徑不可超過的孔徑／空間｜桿徑：彈簧內側的導桿（＝內徑下限）",
        "L1 安裝長度（力 F1）：裝入後的長度與力｜L2 工作最短長度（力 F2）：壓到最短時的長度與力", "自由長上限（選填）：彈簧未受力時允許的最大長度"),
    listOf("L1 安裝長度（兩鉤內側）與力 F1｜L2 最大工作長度與力 F2", "外徑上限：可用空間｜鉤長：每個鉤占的長度 = 係數 × 中徑（見圖）", "初張力由程式依 F1 反推，需落在建議範圍內"),
    listOf("外徑上限：可用空間｜軸徑：扭緊後彈簧內徑需大於軸徑", "L1、L2：兩支臂長｜R：受力點半徑（軸心到受力點）", "θ1 安裝角度（力 F1）→ θ2 工作角度（力 F2），θ2−θ1 為操作行程"),
)

@Composable
fun DesignDiagram(type: Int) = DiagramCard(
    "示意圖", DZ_LEG[type.coerceIn(0, 2)], 0,
    aspect = when (type) { 0 -> 360f / 360f; 1 -> 360f / 400f; else -> 360f / 400f },
) { tm, p -> when (type) { 0 -> dzComp(tm, p); 1 -> dzExt(tm, p); else -> dzTor(tm, p) } }

private fun DrawScope.dzComp(tm: TextMeasurer, p: Pal) {
    val k = size.width / 360f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    val base = 330f; val half = 40f; val bore = 58f; val pl = 50f
    fun panel(cx: Float, top: Float, title: String, f: String, ln: String) {
        txt(tm, title, o(cx, 12f), p.dim)
        drawRect(p.line, o(cx - 18, 82f), Size(36f * k, (base - 82f) * k), alpha = 0.1f)
        drawRect(p.line, o(cx - 18, 82f), Size(36f * k, (base - 82f) * k), style = Stroke(1.2.dp.toPx()))
        guide(o(cx - bore, 62f), o(cx - bore, base), p.dim); guide(o(cx + bore, 62f), o(cx + bore, base), p.dim)
        val path = Path()
        for (i in 0..9) {
            val x = if (i % 2 == 1) cx + half else cx - half; val y = top + (base - top) * i / 9
            if (i == 0) path.moveTo(x * k, y * k) else path.lineTo(x * k, y * k)
        }
        drawPath(path, p.line, style = Stroke(4.6f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(p.line, o(cx - pl, top), o(cx + pl, top), 5f * k)
        drawLine(p.line, o(cx - pl - 14, base), o(cx + pl + 14, base), 6f * k)
        arrow(o(cx + 34, top - 36), o(cx + 34, top - 6), p.dim); txt(tm, f, o(cx + 52, top - 22), p.dim)
        guide(o(cx - pl, top), o(cx - 84, top), p.dim); dim(o(cx - 78, top), o(cx - 78, base), p.dim)
        txt(tm, ln, o(cx - 94, (top + base) / 2), p.dim)
    }
    panel(125f, 110f, "安裝位置", "F1", "L1"); panel(280f, 190f, "工作位置（最短）", "F2", "L2")
    dim(o(125f - bore, 46f), o(125f + bore, 46f), p.dim); txt(tm, "外徑上限", o(125f, 34f), p.dim)
    dim(o(280f - 18, 64f), o(280f + 18, 64f), p.dim); txt(tm, "桿徑（內徑下限）", o(280f, 50f), p.dim)
}

private fun DrawScope.dzExt(tm: TextMeasurer, p: Pal) {
    val k = size.width / 360f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    val half = 40f; val ring = 16f
    fun sp(cx: Float, len: Float, title: String, f: String, ln: String, hk: Boolean) {
        val top = 60f; val bot = 60f + len; val y0 = top + 2 * ring; val y1 = bot - 2 * ring; val n = 12; val st = (y1 - y0) / n
        txt(tm, title, o(cx, 12f), p.dim)
        for (i in 0 until n) { val y = y0 + st * i; drawLine(p.line, o(cx - half, y + st * .85f), o(cx + half, y + st * .1f), 4.4f * k, StrokeCap.Round) }
        drawCircle(p.line, ring * k, o(cx, top + ring), style = Stroke(4.6f * k)); drawCircle(p.line, ring * k, o(cx, bot - ring), style = Stroke(4.6f * k))
        arrow(o(cx, top - 4), o(cx, top - 34), p.dim); txt(tm, f, o(cx + 14, top - 22), p.dim)
        arrow(o(cx, bot + 4), o(cx, bot + 34), p.dim); txt(tm, f, o(cx + 14, bot + 22), p.dim)
        guide(o(cx + ring, top), o(cx + 70, top), p.dim); guide(o(cx + ring, bot), o(cx + 70, bot), p.dim)
        dim(o(cx + 64, top), o(cx + 64, bot), p.dim); txt(tm, ln, o(cx + 80, (top + bot) / 2), p.dim)
        if (hk) {
            dim(o(cx - 30, top), o(cx - 30, y0), p.dim); txt(tm, "鉤長", o(cx - 44, (top + y0) / 2), p.dim)
            dim(o(cx - 30, y1), o(cx - 30, bot), p.dim); txt(tm, "鉤長", o(cx - 44, (y1 + bot) / 2), p.dim)
        }
    }
    sp(105f, 190f, "安裝位置", "F1", "L1", true); sp(255f, 270f, "最大工作長度", "F2", "L2", false)
    guide(o(47f, 44f), o(47f, 330f), p.dim); guide(o(163f, 44f), o(163f, 330f), p.dim)
    dim(o(47f, 346f), o(163f, 346f), p.dim); txt(tm, "外徑上限", o(105f, 364f), p.dim)
    txt(tm, "鉤長 = 係數 × 中徑 D（上下兩個鉤各一）", o(180f, 390f), p.dim)
}

private fun DrawScope.dzTor(tm: TextMeasurer, p: Pal) {
    val k = size.width / 360f
    fun o(x: Float, y: Float) = Offset(x * k, y * k)
    val cx = 150f; val cy = 200f; val rm = 59f; val wr = 9f * k
    val pi = Math.PI.toFloat(); val a2 = 60f * pi / 180f; val lf = 125f; val lm = 110f
    fun sx(a: Float) = cx + rm * cos(a); fun sy(a: Float) = cy - rm * sin(a)
    fun drx(a: Float) = -sin(a); fun dry(a: Float) = -cos(a)
    val dash = PathEffect.dashPathEffect(floatArrayOf(7f * k, 5f * k))
    fun arm(a: Float, len: Float, dashed: Boolean) {
        val x = sx(a); val y = sy(a)
        drawLine(p.line, o(x, y), o(x + drx(a) * len, y + dry(a) * len), wr, StrokeCap.Butt,
            pathEffect = if (dashed) dash else null, alpha = if (dashed) 0.4f else 1f)
    }
    val a1 = pi; val a2t = pi + a2
    val f1x = sx(a1) + drx(a1) * lm * .8f; val f1y = sy(a1) + dry(a1) * lm * .8f
    val f2x = sx(a2t) + drx(a2t) * lm * .8f; val f2y = sy(a2t) + dry(a2t) * lm * .8f
    val n2x = -dry(a2t); val n2y = drx(a2t)
    drawCircle(p.dim, 128f * k, o(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
    txt(tm, "外徑上限", o(cx, cy - 138), p.dim)
    drawCircle(p.line, rm * k, o(cx, cy), style = Stroke(wr))
    drawCircle(p.line, 26f * k, o(cx, cy), alpha = 0.12f)
    drawCircle(p.line, 26f * k, o(cx, cy), style = Stroke(1.2.dp.toPx()))
    dim(o(cx - 26, cy), o(cx + 26, cy), p.dim); txt(tm, "軸徑", o(cx, cy + 14), p.dim)
    arm(0f, lf, false)
    guide(o(cx + rm, cy - lf), o(cx + rm + 66, cy - lf), p.dim); dim(o(cx + rm + 60, cy), o(cx + rm + 60, cy - lf), p.dim)
    txt(tm, "L1", o(cx + rm + 76, cy - lf / 2), p.dim)
    arm(a1, lm, false); arm(a2t, lm, true)
    val ra = 84f
    drawArc(p.dim, 180f, -60f, false, o(cx - ra, cy - ra), Size(2 * ra * k, 2 * ra * k), style = Stroke(1.5.dp.toPx()))
    txt(tm, "θ2−θ1", o(cx - ra - 26, cy + 46), p.dim)
    arrow(o(f1x - 44, f1y), o(f1x - 6, f1y), p.dim); txt(tm, "F1", o(f1x - 56, f1y), p.dim)
    arrow(o(f2x - n2x * 44, f2y - n2y * 44), o(f2x - n2x * 6, f2y - n2y * 6), p.dim); txt(tm, "F2", o(f2x - n2x * 58, f2y - n2y * 58), p.dim)
    guide(o(cx, cy), o(f1x, f1y), p.dim); txt(tm, "R", o((cx + f1x) / 2 + 12, (cy + f1y) / 2), p.dim)
    txt(tm, "L2", o(sx(a1) + drx(a1) * lm, sy(a1) + dry(a1) * lm + 14), p.dim)
    txt(tm, "θ1 位置（實線）", o(cx - 70, cy + 160), p.dim); txt(tm, "θ2 位置（虛線）", o(cx + 70, cy + 160), p.dim)
}
