/* ===== 彈簧中心線幾何（純 JS、無相依）=====
 * 3D 建模（spring3d.js）與工程圖（index.html）共用這一份幾何，圖面才會和 3D 完全一致。
 * 用一般 <script> 載入（不是 ES module），所以 index.html 直接用檔案方式開啟時，工程圖仍可使用。
 *
 * springPath(inp, st) 回傳 { pieces, post, clipZ, meta }
 *   pieces：中心線依序的描述子 helix／line／tarc（切線圓弧）／arc3（三點圓弧）
 *   post  ：整體後處理 { rot（繞 Z 轉的角度，度）, mirror（左右鏡射，x → −x）}
 *   clipZ ：壓縮彈簧磨平端的切平面 [z0, z1]，沒有則為 null
 * 單位 mm；Z 為彈簧軸線；右旋螺旋（由 +Z 看為逆時針）。
 */
(function () {
  "use strict";
  var DEG = Math.PI / 180, TAU = 2 * Math.PI;
  /* 密合圈之間保留 1% 線徑的間隙：相鄰圈相切、或間隙小於 CAD 容差（SolidWorks 約 0.01 mm）時，舊版 CAD 會破圖 */
  var CLOSE_GAP = 1.01;
  var HOOK_SEGS = 3;                                       // 長開口鉤爬升段的分段數
  var ENDS = { cg: "閉端磨平", c: "閉端不磨", o: "開端不磨", og: "開端磨平" };

  var add = function (a, b) { return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]; };
  var sub = function (a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; };
  var mul = function (a, k) { return [a[0] * k, a[1] * k, a[2] * k]; };
  var dot = function (a, b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; };
  var cross = function (a, b) { return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]; };
  var len = function (a) { return Math.hypot(a[0], a[1], a[2]); };
  var unit = function (a) { var n = len(a); return [a[0] / n, a[1] / n, a[2] / n]; };
  var r3 = function (x) { return +Number(x).toFixed(3); };
  var num = function (x, name) {
    if (typeof x !== "number" || !isFinite(x)) throw new Error("參數 " + name + " 無效");
    return x;
  };

  /* ---------- 描述子 ---------- */
  var L = function (a, b) { return { k: "line", a: a, b: b }; };
  var TA = function (s, t, e) { return { k: "tarc", s: s, t: unit(t), e: e }; };
  var A3 = function (p0, pm, p1) { return { k: "arc3", p0: p0, pm: pm, p1: p1 }; };

  /* 螺旋切成每段最多半圈：每張曲面只覆蓋一小段，舊版 CAD（SolidWorks 2015）才讀得正確 */
  function helixPieces(R, pitch, turns, z0, angle0) {
    var out = [], left = turns, ang = angle0, z = z0;
    while (left > 1e-9) {
      var t = Math.min(0.5, left);
      out.push({ k: "helix", R: R, pitch: pitch, turns: t, z0: z, ang0: ang });
      ang += t * 360; z += pitch * t; left -= t;
    }
    return out;
  }
  var hPoint = function (h, a) {
    var g = h.ang0 * DEG + a;
    return [h.R * Math.cos(g), h.R * Math.sin(g), h.z0 + (h.pitch * a) / TAU];
  };
  var hTan = function (h, a) {
    var g = h.ang0 * DEG + a;
    return [-h.R * Math.sin(g), h.R * Math.cos(g), h.pitch / TAU];
  };
  var hStart = function (h) { return hPoint(h, 0); };
  var hEnd = function (h) { return hPoint(h, h.turns * TAU); };

  /* ---------- 圓弧 ---------- */
  /* 圓弧參數化 p(φ) = c + r(u cosφ + v sinφ)，φ: 0 → Phi；退化（共線）時回傳 null 當作直線 */
  function circleOf(d) {
    var c, r, u, v, Phi;
    if (d.k === "tarc") {
      var w = sub(d.e, d.s), n = cross(d.t, w);
      if (len(n) < 1e-9) return null;
      n = unit(n);
      var nc = cross(n, d.t), dn = dot(nc, w);
      if (Math.abs(dn) < 1e-12) return null;
      var rho = dot(w, w) / (2 * dn);
      c = add(d.s, mul(nc, rho)); r = Math.abs(rho);
      u = unit(sub(d.s, c)); v = d.t;
      Phi = Math.atan2(dot(sub(d.e, c), v), dot(sub(d.e, c), u));
    } else {
      var a = sub(d.p1, d.p0), b = sub(d.pm, d.p0), axb = cross(a, b), m = dot(axb, axb);
      if (m < 1e-12) return null;
      var off = mul(cross(sub(mul(b, dot(a, a)), mul(a, dot(b, b))), axb), 1 / (2 * m));
      c = add(d.p0, off); r = len(sub(d.p0, c));
      u = unit(sub(d.p0, c));
      var nn = unit(cross(u, unit(sub(d.pm, c))));
      v = cross(nn, u);
      Phi = Math.atan2(dot(sub(d.p1, c), v), dot(sub(d.p1, c), u));
    }
    if (Phi <= 0) Phi += TAU;
    return { c: c, r: r, u: u, v: v, Phi: Phi };
  }
  var arcPoint = function (ci, phi) {
    return add(ci.c, add(mul(ci.u, ci.r * Math.cos(phi)), mul(ci.v, ci.r * Math.sin(phi))));
  };
  /* 描述子的起點、終點與終點切線方向（單位向量） */
  function descStart(d) { return d.k === "helix" ? hStart(d) : d.k === "line" ? d.a : d.k === "tarc" ? d.s : d.p0; }
  function descEnd(d) { return d.k === "helix" ? hEnd(d) : d.k === "line" ? d.b : d.k === "tarc" ? d.e : d.p1; }
  function descEndTan(d) {
    if (d.k === "helix") return unit(hTan(d, d.turns * TAU));
    if (d.k === "line") return unit(sub(d.b, d.a));
    var ci = circleOf(d);
    if (!ci) return unit(sub(descEnd(d), descStart(d)));
    return unit(add(mul(ci.u, -Math.sin(ci.Phi)), mul(ci.v, Math.cos(ci.Phi))));
  }

  /* ---------- 壓縮彈簧 ---------- */
  function compression(inp, st) {
    var free = num(inp.l0, "L0");
    var lw = st && typeof st.lw === "number" ? st.lw : null;
    if (lw !== null && lw > free + 1e-9) throw new Error("工作長度大於自由長度，彈簧沒有被壓縮。");
    var d = num(inp.d, "d"), od = num(inp.od, "OD"), na = num(inp.na, "Na"), l0 = lw !== null ? lw : free;
    var end = inp.end || "cg", R = (od - d) / 2;
    if (!(na > 0)) throw new Error("有效圈數需大於 0");
    var closed = end === "cg" || end === "c", ground = end === "cg" || end === "og";
    var p, z0, secs;
    if (end === "cg") { p = (l0 - 2 * d) / na; z0 = 0; }
    else if (end === "c") { p = (l0 - 3 * d) / na; z0 = d / 2; }
    else if (end === "o") { p = (l0 - d) / na; z0 = d / 2; }
    else { p = l0 / (na + 1); z0 = 0; }
    if (p < d * CLOSE_GAP - 1e-9) {
      throw new Error(lw !== null
        ? "工作長度 " + r3(lw) + " mm 太短：有效圈節距 " + p.toFixed(3) + " mm 小於線徑 " + d + " mm，線圈已壓並（小於壓並高度），無法畫出。"
        : "有效圈節距 " + p.toFixed(3) + " mm 小於線徑 " + d + " mm，線圈會互相穿透。請檢查自由長 L0 與有效圈數 Na。");
    }
    if (closed) secs = [[1, d * CLOSE_GAP], [na, p], [1, d * CLOSE_GAP]];
    else secs = [[end === "og" ? na + 1 : na, p]];
    var pieces = [], z = z0, ang = 0;
    secs.forEach(function (s) {
      helixPieces(R, s[1], s[0], z, ang).forEach(function (h) { pieces.push(h); });
      z += s[0] * s[1]; ang += s[0] * 360;
    });
    return {
      pieces: pieces, post: { rot: 0, mirror: false }, clipZ: ground ? [0, l0] : null,
      meta: {
        type: 0, d: d, od: od, R: R, end: end, na: na, free: free, lw: lw, len: l0,
        note: "壓縮彈簧　" + (ENDS[end] || end) + "　" + (lw !== null ? "工作長度 L=" + r3(lw) + "（自由長 " + r3(free) + "）" : "自由長 " + r3(free)) +
          "　線徑 " + r3(d) + "　外徑 " + r3(od) + "　有效圈 " + r3(na),
      },
    };
  }

  /* ---------- 扭力彈簧 ----------
   * 自由角度 α0：由腳2 逆時針（右旋時為纏繞方向）量到腳1 的夾角；往纏繞方向扭緊 θ 時變成 α0 − θ。
   * 切線腳的自然夾角 = 180° − 360°×(N 的小數圈)（以 360° 為週期）；與設定不同時，腳2 在根部以彎折圓弧補足。 */
  function torsion(inp, st) {
    var d = num(inp.d, "d"), od = num(inp.od, "OD"), nbIn = num(inp.nb, "N");
    var l1 = num(inp.l1, "L1"), l2 = num(inp.l2, "L2"), R0 = (od - d) / 2;
    if (!(nbIn > 0)) throw new Error("本體圈數需大於 0");
    if (!(l1 > 0 && l2 > 0)) throw new Error("臂長 L1、L2 需大於 0");
    var fa = typeof inp.fa === "number" && isFinite(inp.fa) ? inp.fa : 180;
    if (fa < 0 || fa > 360) throw new Error("自由角度 α0 需在 0～360 度之間");
    /* 兩腳要與線圈相切，N 的小數圈必須對應自由角度：f = ((180° − α0)/360°) 取小數。
     * 3D 與工程圖取「最接近輸入 N、且小數圈符合 α0」的圈數 N'（最多差半圈）；計算頁的剛度、應力仍用輸入的 N。 */
    var fT = (((180 - fa) / 360) % 1 + 1) % 1;
    var nb0 = Math.floor(nbIn - fT + 0.5) + fT;
    if (nb0 <= 0.01) nb0 += 1;
    /* 工作位置：往纏繞方向扭轉 θ → 圈數增加 θ/360、線圈縮小（與計算頁的「扭緊後外徑」一致） */
    var th = st && typeof st.th === "number" ? st.th : 0;
    var nb = nb0 + th / 360, R = (R0 * nb0) / nb;
    var pcs = helixPieces(R, d * CLOSE_GAP, nb, d / 2, 0);
    var first = pcs[0], last = pcs[pcs.length - 1];
    var S = hStart(first), E = hEnd(last);
    var hz = function (v) { return unit([v[0], v[1], 0]); };       // 腳在水平面內（垂直於軸線）
    var t0 = hz(hTan(first, 0)), t1 = hz(hTan(last, last.turns * TAU));
    var leg1 = L(sub(S, mul(t0, l1)), S);
    var hL1 = Math.atan2(-t0[1], -t0[0]), h0 = Math.atan2(t1[1], t1[0]);
    var h1 = hL1 - ((fa - th) * DEG);
    var dl = ((((h1 - h0 + Math.PI) % TAU) + TAU) % TAU) - Math.PI;   // 腳2 需轉的角度 (-π, π]
    var leg2;
    if (Math.abs(dl) < 0.01) {
      leg2 = [L(E, add(E, mul(t1, l2)))];
    } else {
      if (Math.abs(dl) > 175 * DEG) throw new Error("自由角度與本體圈數（N 的小數圈）相差過大，腳無法彎折成形。請調整 N 的小數圈數或自由角度。");
      var s = dl > 0 ? 1 : -1, rb = Math.max(1.5 * d, 1);
      var nL = function (h) { return [-Math.sin(h), Math.cos(h), 0]; };
      var C = add(E, mul(nL(h0), s * rb)), Q = sub(C, mul(nL(h1), s * rb));
      var rest = Math.max(l2 - rb * Math.abs(dl), d);
      leg2 = [TA(E, [Math.cos(h0), Math.sin(h0), 0], Q), L(Q, add(Q, [Math.cos(h1) * rest, Math.sin(h1) * rest, 0]))];
    }
    /* 整體繞軸轉到「兩腳夾角的平分線朝 −Y」（以自由狀態計算，工作狀態沿用同一轉角）；左旋則左右鏡射 */
    var dFree = ((((fa + 180) % 360) + 360) % 360) - 180;                // 腳2→腳1 的有號夾角
    var bis = hL1 - fa * DEG + (dFree * DEG) / 2;                         // 夾角平分線方向
    var rot = (((-Math.PI / 2 - bis) * 180) / Math.PI) % 360;
    var left = inp.hand === 1;
    var pieces = [leg1].concat(pcs, leg2);
    var stTxt = th ? "工作位置 θ=" + r3(th) + "°（兩腳夾角 " + r3(fa - th) + "°）" : "自由狀態（α0=" + r3(fa) + "°）";
    return {
      pieces: pieces, post: { rot: rot, mirror: left }, clipZ: null,
      meta: {
        type: 1, d: d, od: od, R: R, R0: R0, nb: nb0, nbIn: nbIn, fa: fa, th: th, l1: l1, l2: l2, hand: left ? 1 : 0,
        bodyZ: [0, last.z0 + last.pitch * last.turns + d / 2],
        note: "扭力彈簧　" + (left ? "左旋" : "右旋") + "　" + stTxt + "　線徑 " + r3(d) + "　外徑 " + r3(od) + "　本體圈數 " +
          (Math.abs(nb0 - nbIn) > 1e-6 ? "N'=" + r3(nb0) + "（輸入 N=" + r3(nbIn) + "，依自由角度調整使兩腳相切）" : r3(nb0)) + "　臂長 " + r3(l1) + " / " + r3(l2),
      },
    };
  }

  /* ---------- 拉伸彈簧（密繞本體 + 兩端鉤）----------
   * 兩種鉤型（inp.hook：0＝長開口鉤〔預設〕、1＝圓環鉤）。鉤所在的平面都通過彈簧軸線。
   * 上下兩鉤取同一個平面（平行）：本體圈數取最接近輸入的 Na'（差 ≤ 0.25 圈），使線圈兩端的角度剛好讓兩鉤共面；
   * 整體轉到鉤平面 = XZ 平面（前視圖看到鉤的正面）。
   *   長開口鉤：由本體端點先做 90° 側彎（半徑 r2）→ 沿軸向的直頸 → 180° 半圓（半徑 r1，朝軸線彎回）即止（鉤部切齊 R1）。
   *   圓環鉤（依實物）：線圈末端在水平面內向軸線彎入（半徑 rb）→ 水平直線橫越線圈上方 → 90° 往上彎（半徑 r2）→
   *             短直頸 → 與圓環「在外側切點」相接；圓環（中心半徑 r1）圓心在軸線上、環面通過軸線，留開口。 */
  function hookLong(A, tOut, sgn, d, r1, r2, n1) {
    /* 由本體端點起，沿「線圈所在的圓柱面」往上爬升到鉛直：在把圓柱面攤平的平面上是一段 1/4 圓弧（半徑 r2），
     * 實際分成 HOOK_SEGS 段、每段用三點圓弧逼近（誤差遠小於線徑）。這樣頸的中心半徑 = 線圈中心半徑 R
     * （和線圈同一直徑，不超出外徑）。極端節距的小螺旋段會讓 CAD 核心的掃掠失效，所以不用螺旋段。 */
    var Z = [0, 0, sgn];
    var R = Math.hypot(A[0], A[1]), phiA = Math.atan2(A[1], A[0]);
    var onCyl = function (al) {                                    // 爬升曲線上的點（al：0 → π/2）
      var phi = phiA + sgn * (r2 / R) * Math.sin(al);
      return [R * Math.cos(phi), R * Math.sin(phi), A[2] + sgn * r2 * (1 - Math.cos(al))];
    };
    var phiN = phiA + sgn * (r2 / R);
    var rp = [Math.cos(phiN), Math.sin(phiN), 0];                  // 鉤平面（通過軸線）的水平方向 = 頸所在的半徑方向
    var Q1 = onCyl(Math.PI / 2);                                   // 爬升結束點（朝 Z）
    var Q2 = add(Q1, mul(Z, n1));                                  // 直頸結束點
    var Q3 = sub(Q2, mul(rp, 2 * r1));                             // 半圓結束點（朝 −Z，已轉向本體）：鉤部到此為止
    var M = add(add(Q2, mul(rp, -r1)), mul(Z, r1));                // 半圓最遠點
    var hasNeck = n1 > 1e-6, NS = HOOK_SEGS, up = [], dn = [];
    for (var i = 0; i < NS; i++) {
      var a0 = (i * Math.PI) / (2 * NS), a1 = ((i + 1) * Math.PI) / (2 * NS), am = (a0 + a1) / 2;
      up.push(A3(onCyl(a0), onCyl(am), onCyl(a1)));                // 由本體往外
      dn.unshift(A3(onCyl(a1), onCyl(am), onCyl(a0)));             // 由外往本體（反向）
    }
    var fwd = up.slice();
    if (hasNeck) fwd.push(L(Q1, Q2));
    fwd.push(A3(Q2, M, Q3));
    var rev = [A3(Q3, M, Q2)];
    if (hasNeck) rev.push(L(Q2, Q1));
    rev = rev.concat(dn);
    return { fwd: fwd, rev: rev };
  }
  /* 圓環鉤（環立在線圈端）：環面通過彈簧軸線、圓心在軸線上，中心半徑 r1，留開口。
   * 路徑：線圈端點 → 水平圓弧（半徑 rb）向內彎，彎到「行進方向正對軸線」→ 直線橫越線圈端的正中央（通過軸線）→
   *       90° 往上彎（半徑 r2）→ 直頸 → 與圓環在外側的切點相接 → 圓環（朝軸線方向捲過頂端）。
   * 橫越線圈的直線、直頸與圓環都在同一個通過軸線的平面內。rb = min(r2, 0.48·R)：圓弧要能彎到正對軸線，需 rb ≤ R/2。 */
  function ringGeom(R, r2) {
    var rb = Math.min(r2, 0.48 * R);
    var cth = -rb / (R - rb), th = Math.acos(Math.max(-1, Math.min(1, cth)));   // 圓弧轉過的角度（> 90°）
    var kx = R - rb + rb * Math.cos(th), ky = rb * Math.sin(th);                 // 圓弧終點（本體半徑方向 x、切線方向 y）
    return { rb: rb, th: th, kx: kx, ky: ky, mu: Math.atan2(-ky, -kx) / DEG };   // mu：橫越線與「本體半徑方向」的夾角（度）
  }

  function hookRing(A, tOut, sgn, d, r1, r2, n1) {
    var Z = [0, 0, sgn], Th = unit([tOut[0], tOut[1], 0]), rh = unit([A[0], A[1], 0]);
    var R = Math.hypot(A[0], A[1]), g = ringGeom(R, r2), rb = g.rb;
    var K = add(sub(A, mul(rh, rb)), add(mul(rh, rb * Math.cos(g.th)), mul(Th, rb * Math.sin(g.th))));   // 水平圓弧終點
    K[2] = A[2];
    var dK = Math.hypot(K[0], K[1]), u = [-K[0] / dK, -K[1] / dK, 0];               // 橫越方向：由 K 指向軸線
    var sb = r1 - r2;                                                                // 往上彎的起點（離軸線，沿 u）
    var Pb = [u[0] * sb, u[1] * sb, A[2]];
    var Q1 = add(add(Pb, mul(u, r2)), mul(Z, r2));                                   // 往上彎結束點（朝 Z）
    var Q2 = add(Q1, mul(Z, n1));                                                    // 直頸結束點 = 圓環外側切點（離軸線 r1）
    var C = sub(Q2, mul(u, r1));                                                     // 圓環圓心（在軸線上）
    var gap = Math.acos(Math.max(-1, 1 - (1.15 * d) / r1));                          // 開口角：尖端與直頸至少隔 1.15 d
    var Aend = TAU - gap;
    var pt = function (a) { return add(C, add(mul(u, r1 * Math.cos(a)), mul(Z, r1 * Math.sin(a)))); };
    var hasLine = len(sub(Pb, K)) > 1e-3, hasNeck = n1 > 1e-6;
    var Pk = hasLine ? K : Pb;
    var arc0 = TA(A, tOut, Pk), arcV = TA(Pb, u, Q1);
    var fwd = [arc0];
    if (hasLine) fwd.push(L(K, Pb));
    fwd.push(arcV);
    if (hasNeck) fwd.push(L(Q1, Q2));
    fwd.push(A3(pt(0), pt(Aend / 2), pt(Aend)));
    var rev = [A3(pt(Aend), pt(Aend / 2), pt(0))];
    if (hasNeck) rev.push(L(Q2, Q1));
    rev.push(TA(Q1, mul(Z, -1), Pb));
    if (hasLine) rev.push(L(Pb, K));
    rev.push(TA(Pk, mul(descEndTan(arc0), -1), A));
    return { fwd: fwd, rev: rev };
  }

  function extension(inp, st) {
    var d = num(inp.d, "d"), od = num(inp.od, "OD"), naIn = num(inp.na, "Na");
    var l0 = num(inp.l0, "L0"), r1 = num(inp.r1, "r1"), r2 = num(inp.r2, "r2");
    var ring = inp.hook === 1;
    var R = (od - d) / 2;
    if (!(naIn > 0)) throw new Error("有效圈數需大於 0");
    if (!(r1 > d / 2)) throw new Error("鉤的中心半徑 r1 需大於 d/2");
    if (!(r2 >= d)) throw new Error("鉤的側彎半徑 r2 需大於或等於線徑 d（" + r3(d) + " mm）");
    var rg = ring ? ringGeom(R, r2) : null;
    if (ring && !(rg.rb >= d * 0.9)) throw new Error("線圈中徑太小：圓環鉤的彎入半徑 " + rg.rb.toFixed(2) + " mm 小於線徑 " + r3(d) + " mm。請加大外徑或縮小線徑。");
    if (ring && r1 - r2 < -Math.hypot(rg.kx, rg.ky) + 1e-3) throw new Error("圓環鉤的 r1 太小：需大於 r2 − " + Math.hypot(rg.kx, rg.ky).toFixed(2) + " mm，請加大 r1 或縮小 r2。");
    /* 兩鉤共面：線圈起點角 = κ、末端角 = κ + 360·Na'，其中 κ 為鉤的平面相對線圈端點的角度；Na' = k/2 − κ/180（k 為整數）取最接近輸入 Na */
    var kDeg = ring ? rg.mu : (r2 / R) / DEG;   // 長開口鉤：頸在線圈圓柱面上，離開端點的角度 = r2/R（弧度）
    var k = Math.round((naIn + kDeg / 180) * 2);
    if (k / 2 - kDeg / 180 < 0.1) k += 1;
    var na = k / 2 - kDeg / 180;
    var pitch0 = d * CLOSE_GAP;
    var bodyLen = na * pitch0 + d;                 // 自由狀態的實際本體長度（含圈間間隙）
    var h = (l0 - bodyLen) / 2;                    // 每個鉤：本體端面到鉤孔內側（環／半圓內側）的長度
    var n1 = h - r1 + d - r2;                      // 往上彎之後的直頸長度（鉤孔內側 = 環／半圓內側）
    if (n1 < -1e-6) throw new Error("自由長 L0 太短，容納不下" + (ring ? "圓環鉤" : "長開口鉤") + "（需要每鉤長度 ≥ r1 + r2 − d = " + (r1 + r2 - d).toFixed(2) + " mm，目前 " + h.toFixed(2) + " mm）。請加大 L0，或縮小 r1、r2。");
    if (ring) {
      if (h < 2 * r1 + 0.3) throw new Error("自由長 L0 太短，圓環會碰到橫越線圈的那段線（每鉤長度需 ≥ 2·r1 + 0.3 = " + (2 * r1 + 0.3).toFixed(2) + " mm，目前 " + h.toFixed(2) + " mm）。");
    } else if (h < r1 + 0.3) {
      throw new Error("自由長 L0 太短，鉤部會碰到本體（每鉤長度需 ≥ r1 + 0.3 = " + (r1 + 0.3).toFixed(2) + " mm，目前 " + h.toFixed(2) + " mm）。");
    }
    /* 工作長度 L（兩鉤內側距離）：鉤不變形，本體各圈等距拉開 */
    var Lw = st && typeof st.lw === "number" ? st.lw : l0;
    if (Lw < l0 - 1e-9) throw new Error("工作長度小於自由長度，彈簧沒有被拉開。");
    var pitch = pitch0 + (Lw - l0) / na;
    var bz0 = h;                                    // 本體底面；下鉤孔內側為 z = 0
    var body = helixPieces(R, pitch, na, bz0 + d / 2, kDeg);
    var first = body[0], last = body[body.length - 1];
    var S = hStart(first), E = hEnd(last);
    var tS = unit(hTan(first, 0)), tE = unit(hTan(last, last.turns * TAU));
    var mk = ring ? function (A, t, s) { return hookRing(A, t, s, d, r1, r2, Math.max(n1, 0)); }
                  : function (A, t, s) { return hookLong(A, t, s, d, r1, r2, Math.max(n1, 0)); };
    var top = mk(E, tE, +1), bot = mk(S, mul(tS, -1), -1);
    return {
      pieces: bot.rev.concat(body, top.fwd), post: { rot: 0, mirror: false }, clipZ: null,
      meta: {
        type: 2, d: d, od: od, R: R, na: na, naIn: naIn, hook: ring ? 1 : 0, free: l0, lw: Lw !== l0 ? Lw : null, r1: r1, r2: r2, bodyZ: [bz0, bz0 + na * pitch + d],
        note: "拉伸彈簧（" + (ring ? "圓環鉤（環立在線圈端）" : "長開口鉤") + "）　" + (Lw !== l0 ? "工作長度 L=" + r3(Lw) + "（自由長 " + r3(l0) + "）" : "自由長（鉤內）" + r3(l0)) +
          "　線徑 " + r3(d) + "　外徑 " + r3(od) + "　有效圈 " + (Math.abs(na - naIn) > 1e-6 ? "Na'=" + r3(na) + "（輸入 " + r3(naIn) + "，使兩鉤共面）" : r3(na)) + "　r1 " + r3(r1) + "　r2 " + r3(r2),
      },
    };
  }
  function springPath(inp, st) {
    if (inp.type === 1) return torsion(inp, st);
    if (inp.type === 2) return extension(inp, st);
    return compression(inp, st);
  }

  /* ---------- 取樣（給工程圖／檢視用）---------- */
  function post(p, po) {
    var c = Math.cos(po.rot * DEG), s = Math.sin(po.rot * DEG);
    var x = p[0] * c - p[1] * s, y = p[0] * s + p[1] * c;
    return [po.mirror ? -x : x, y, p[2]];
  }
  /* 回傳 chunks：依序排列的小段折線（每段約 chunkDeg 度），點座標已套用整體後處理 */
  function samplePath(path, stepDeg, chunkDeg) {
    stepDeg = stepDeg || 6; chunkDeg = chunkDeg || 45;
    var chunks = [];
    path.pieces.forEach(function (d) {
      var f;
      if (d.k === "line") { chunks.push([d.a, d.b]); return; }
      if (d.k === "helix") f = function (phi) { return hPoint(d, phi); };
      var span = d.k === "helix" ? d.turns * TAU : null;
      if (d.k !== "helix") {
        var ci = circleOf(d);
        if (!ci) { chunks.push([descStart(d), descEnd(d)]); return; }
        span = ci.Phi; f = function (phi) { return arcPoint(ci, phi); };
      }
      var nC = Math.max(1, Math.ceil(span / (chunkDeg * DEG) - 1e-9));
      var nP = Math.max(2, Math.ceil(span / nC / (stepDeg * DEG)) + 1);
      for (var i = 0; i < nC; i++) {
        var pts = [];
        for (var j = 0; j < nP; j++) pts.push(f(span * (i + j / (nP - 1)) / nC));
        chunks.push(pts);
      }
    });
    return chunks.map(function (c) { return c.map(function (p) { return post(p, path.post); }); });
  }

  window.SpringPath = {
    setHookSegs: function (n) { HOOK_SEGS = n; }, CLOSE_GAP: CLOSE_GAP, ENDS: ENDS, springPath: springPath, samplePath: samplePath,
    r3: r3, hPoint: hPoint, hTan: hTan, hStart: hStart, hEnd: hEnd, descStart: descStart, descEnd: descEnd,
  };
})();


/* ===== 計算核心（SI：mm、N、MPa、N·mm） ===== */
const PI=Math.PI;
const MATS=[
 {n:"琴鋼線 (Music wire)",G:79300,E:206000,A:2211,m:.145,steel:true},
 {n:"油回火鋼線",G:77200,E:203400,A:1855,m:.187,steel:true},
 {n:"不鏽鋼 302/304",G:69000,E:193000,A:1867,m:.146,steel:true},
 {n:"磷青銅",G:41400,E:103400,A:932,m:.064,steel:false},
 {n:"鉻矽鋼線",G:77200,E:203400,A:1974,m:.108,steel:true}];
const sut=(m,d)=>m.A/Math.pow(d,m.m);
const ENDS={cg:{n:"閉端磨平",nt:a=>a+2,ls:(a,d)=>(a+2)*d},c:{n:"閉端不磨",nt:a=>a+2,ls:(a,d)=>(a+3)*d},
 o:{n:"開端不磨",nt:a=>a,ls:(a,d)=>(a+1)*d},og:{n:"開端磨平",nt:a=>a+1,ls:(a,d)=>(a+1)*d}};
const wahl=c=>(4*c-1)/(4*c-4)+.615/c;
const shear=(f,D,d)=>wahl(D/d)*8*f*D/(PI*d**3);
const rate=(G,d,D,na)=>G*d**4/(8*D**3*na);
const initialTension=(d,D)=>{const c=D/d,tau=33.5*6.894757/Math.exp(.105*c),tol=6.894757*(4-(c-3)/6.5),f=t=>Math.max(PI*d**3*t/(8*D),0);return {fi:f(tau),lo:f(tau-tol),hi:f(tau+tol)}};
const allowableLoad=(d,D,k,l0,fi,S,r1,r2)=>{const fb=.45*S/shear(1,D,d),fhb=.65*S/hookBending(1,d,D,r1),fht=.45*S/hookTorsion(1,d,D,r2);
 const L=[["本體",fb],["鉤環彎曲",fhb],["鉤側彎扭轉",fht]].sort((a,b)=>a[1]-b[1]);return {f:L[0][1],gov:L[0][0],lMax:l0+Math.max((L[0][1]-fi)/k,0)}};
const hookBending=(f,d,D,r1)=>{const c1=Math.max(2*r1/d,1.05);const ka=(4*c1*c1-c1-1)/(4*c1*(c1-1));return f*(ka*16*D/(PI*d**3)+4/(PI*d*d))};
const hookTorsion=(f,d,D,r2)=>{const c2=Math.max(2*r2/d,1.05);return (4*c2-1)/(4*c2-4)*8*f*D/(PI*d**3)};
const strengthShear=(t,s)=>{const r=t/s;return {r,v:r<=.45?0:r<=.65?1:2}};
const strengthBending=(t,s)=>{const r=t/s;return {r,v:r<=.65?0:r<=.78?1:2}};
const VL=["✔ 合格","⚠ 需預壓除應力","✘ 超限"];

function compression(d,od,na,l0,lw,m,end){
 const D=od-d,c=D/d,k=rate(m.G,d,D,na),e=ENDS[end],ls=e.ls(na,d);
 const F=k*(l0-lw),Fs=k*(l0-ls);const w=[];
 if(c<4)w.push("旋繞比 C<4，難以成形");if(c>12)w.push("旋繞比 C>12，易纏繞/不穩定");
 if(l0/D>4)w.push("自由長/中徑 >4，注意挫曲，建議加導桿或導套");
 if(lw<ls)w.push("工作長度小於密合高度");if(lw>l0)w.push("工作長度大於自由長度");
 return {D,c,k,nt:e.nt(na),ls,F,tau:shear(F,D,d),Fs,taus:shear(Fs,D,d),w};
}
function fatigue(tmax,tmin,d,m,peen){
 const S=sut(m,d),ssu=.67*S,ssa=peen?398:241,ssm=peen?534:379;
 const sse=Math.min(ssa/Math.max(1-(ssm/ssu)**2,.05),.5*ssu);
 const tm=(tmax+tmin)/2,ta=(tmax-tmin)/2,w=[];
 if(!m.steel)w.push("疲勞係數以鋼線試驗為基礎，此材質結果僅供參考");
 if(tmax>.45*S)w.push("最大應力超過靜態降伏約 0.45Sut，可能永久變形");
 if(ta<=0)w.push("循環應力幅為 0，無疲勞");
 const ssf=tmax>0?.45*S/tmax:Infinity;
 if(ta<=0||tm>=ssu)return {sut:S,sse,ssf,nf:Infinity,N:ta<=0?Infinity:1e3,below:ta>0,w};
 const nf=.5*(ssu/tm)**2*(ta/sse)*(-1+Math.sqrt(1+(2*tm*sse/(ssu*ta))**2));
 const eq=ta/(1-(tm/ssu)**2),s1=.9*ssu;let N,below=false;
 if(eq>=s1){N=1e3;below=true}else if(eq<=sse)N=Infinity;else N=Math.pow(10,3+4*Math.log(s1/eq)/Math.log(s1/sse));
 return {sut:S,sse,ssf,nf,N,below,w};
}
function fatigueBending(smax,smin,d,m,peen){
 const S=sut(m,d),sr=(peen?.67:.45)*S,se=(sr/2)/(1-(sr/2/S)**2);
 const sm=(smax+smin)/2,sa=(smax-smin)/2,w=[];
 if(!m.steel)w.push("疲勞係數以鋼線試驗為基礎，此材質結果僅供參考");
 if(smax>.78*S)w.push("最大彎曲應力超過建議上限 0.78Sut，可能永久變形");
 if(sa<=0)w.push("循環應力幅為 0，無疲勞");
 const ssf=smax>0?.78*S/smax:Infinity;
 if(sa<=0||sm>=S)return {sut:S,sse:se,ssf,nf:Infinity,N:sa<=0?Infinity:1e3,below:sa>0,w};
 const nf=.5*(S/sm)**2*(sa/se)*(-1+Math.sqrt(1+(2*sm*se/(S*sa))**2));
 const eq=sa/(1-(sm/S)**2),s1=.9*S;let N,below=false;
 if(eq>=s1){N=1e3;below=true}else if(eq<=se)N=Infinity;else N=Math.pow(10,3+3*Math.log(s1/eq)/Math.log(s1/se));
 return {sut:S,sse:se,ssf,nf,N,below,w};
}
function torsion(d,od,nb,l1,l2,ang,arm,m){
 const D=od-d,c=D/d,na=nb+(l1+l2)/(3*PI*D),kd=m.E*d**4/(3667*D*na),M=kd*ang;
 const ki=(4*c*c-c-1)/(4*c*(c-1)),sig=ki*32*M/(PI*d**3);
 const dc=D*nb/(nb+ang/360)+d,w=[];
 if(c<4)w.push("旋繞比 C<4，難以成形");if(c>14)w.push("旋繞比 C>14，不穩定");if(ang/360>nb)w.push("扭轉角過大");
 return {D,c,na,kd,M,F:arm>0?M/arm:0,sig,dc,w};
}
function extension(d,od,na,l0,lw,fi,fmax,m,r1,r2){
 const D=od-d,c=D/d,k=rate(m.G,d,D,na),F=fi+k*(lw-l0),w=[];
 if(c<4)w.push("旋繞比 C<4，難以成形");if(c>12)w.push("旋繞比 C>12，初張力難控制");
 if(lw<l0)w.push("工作長度小於自由長度，無拉力");if(F<fi)w.push("負荷小於初張力，彈簧尚未拉開");
 if(2*r1/d<=1||2*r2/d<=1)w.push("鉤環半徑過小（C1、C2 必須大於 1）");
 const fw=Math.max(F,0);
 return {D,c,k,body:(na+1)*d,F,tau:shear(fw,D,d),taui:shear(fi,D,d),taumax:shear(fmax,D,d),
  hb:hookBending(fw,d,D,r1),ht:hookTorsion(fw,d,D,r2),hbm:hookBending(fmax,d,D,r1),htm:hookTorsion(fmax,d,D,r2),w};
}
function extensionFatigue(d,D,k,l0,lw,lmin,fi,r1,r2,m,peen){
 const fo=l=>fi+k*Math.max(l-l0,0),fM=fo(lw),fm=fo(lmin);
 const body=fatigue(shear(fM,D,d),shear(fm,D,d),d,m,peen);
 const hb=fatigueBending(hookBending(fM,d,D,r1),hookBending(fm,d,D,r1),d,m,peen);
 const ht=fatigue(hookTorsion(fM,d,D,r2),hookTorsion(fm,d,D,r2),d,m,peen);
 const all=[["本體",body],["鉤環彎曲",hb],["鉤側彎扭轉",ht]];
 all.sort((a,b)=>a[1].N-b[1].N||a[1].nf-b[1].nf);
 const w=[...new Set([...body.w,...hb.w,...ht.w])];if(lmin>=lw)w.push("循環最小長度需小於工作長度");
 return {body,hb,ht,N:all[0][1].N,below:all[0][1].below,gov:all[0][0],nf:Math.min(body.nf,hb.nf,ht.nf),w};
}

/* ===== 規格設計（依空間、型式、操作位置與力量，搜尋建議的彈簧規格） ===== */
const STD_D=[.2,.25,.3,.35,.4,.45,.5,.55,.6,.7,.8,.9,1,1.1,1.2,1.4,1.5,1.6,1.8,2,2.2,2.5,2.8,3,3.2,3.5,4,4.5,5,5.5,6,6.5,7,8,9,10,11,12,13,14,15,16];
const roundTo=(x,s)=>Math.round(x/s)*s;
const VR={compression:{cLo:4,cHi:12},extension:{cLo:4,cHi:12},torsion:{cLo:4,cHi:14}};
const cmpD=(x,y)=>x===y?0:(x>y?-1:1); // 由大到小（含 Infinity）
function pickDesign(out){
 const pass=out.filter(c=>c.fails.length===0);
 // 排序：預估壽命由最佳到最差（再比疲勞安全係數、最後線材用量少者優先）
 const byLife=(a,b)=>cmpD(a.v.N,b.v.N)||cmpD(a.v.nf,b.v.nf)||a.weight-b.weight;
 const src=pass.length?pass.sort(byLife):out.slice().sort((a,b)=>a.fails.length-b.fails.length||byLife(a,b));
 const seen=new Set(),list=[];
 for(const c of src){if(seen.has(c.d))continue;seen.add(c.d);list.push(c);if(list.length>=6)break}
 list.sort(pass.length?byLife:(a,b)=>a.fails.length-b.fails.length||byLife(a,b));
 return {cands:list,pass:pass.length,total:out.length,ok:pass.length>0};
}
function designCompression(sp){
 const m=sp.mat,E=ENDS[sp.end],kreq=(sp.f2-sp.f1)/(sp.l1-sp.l2),out=[];
 for(const d of STD_D){const S=sut(m,d);
  const Dlo=Math.max((sp.idMin||0)+d,VR.compression.cLo*d),Dhi=Math.min(sp.odMax-d,VR.compression.cHi*d);
  for(let D=Math.ceil(Dlo*4-1e-9)/4;D<=Dhi+1e-9;D+=.25){
   const na=roundTo(m.G*d**4/(8*D**3*kreq),.25);if(na<2)continue;
   const k=rate(m.G,d,D,na),L0=sp.l1+sp.f1/k,F2=sp.f1+k*(sp.l1-sp.l2),ls=E.ls(na,d),Fs=k*(L0-ls);
   const t2=shear(F2,D,d),t1=shear(sp.f1,D,d),st=strengthShear(t2,S),ft=fatigue(t2,t1,d,m,sp.peen),err=Math.abs(F2/sp.f2-1);
   const fails=[],warns=[];
   if(err>sp.tol)fails.push(`力量誤差 ${(err*100).toFixed(1)}% 超過容許 ${(sp.tol*100).toFixed(0)}%`);
   if(sp.l0Max&&L0>sp.l0Max)fails.push("自由長超過空間上限");
   if(Fs<1.15*F2)fails.push("壓並力不足（需 ≥ 1.15×工作力）");
   if(st.v===2)fails.push("剪應力超過 0.65Sut");else if(st.v===1)warns.push("剪應力 >0.45Sut，需預壓除應力");
   if(sp.nTarget&&ft.N<sp.nTarget)fails.push("預估壽命低於目標");
   if(L0/D>4)warns.push("自由長/中徑 >4，建議加導桿");
   if(!m.steel)warns.push("疲勞係數以鋼線為基礎，僅供參考");
   out.push({d,D,na,fails,warns,level:st.v,weight:PI*d*d/4*PI*D*E.nt(na),
    v:{OD:D+d,ID:D-d,nt:E.nt(na),L0,ls,k,F1:sp.f1,F2,Fs,tau2:t2,ratio:st.r,sut:S,nf:ft.nf,N:ft.N,below:ft.below,C:D/d}});
  }}
 return Object.assign({kreq},pickDesign(out));
}
function designExtension(sp){
 const m=sp.mat,kreq=(sp.f2-sp.f1)/(sp.l2-sp.l1),out=[];
 for(const d of STD_D){const S=sut(m,d);
  const Dlo=Math.max((sp.idMin||0)+d,VR.extension.cLo*d),Dhi=Math.min(sp.odMax-d,VR.extension.cHi*d);
  for(let D=Math.ceil(Dlo*4-1e-9)/4;D<=Dhi+1e-9;D+=.25){
   const na=roundTo(m.G*d**4/(8*D**3*kreq),.25);if(na<3)continue;
   const k=rate(m.G,d,D,na),L0=(na+1)*d+2*sp.hookF*D,Fi=sp.f1-k*(sp.l1-L0),F2=sp.f1+k*(sp.l2-sp.l1),it=initialTension(d,D);
   const r1=D/2,r2=2*d,tb=shear(F2,D,d),hb=hookBending(F2,d,D,r1),ht=hookTorsion(F2,d,D,r2);
   const sB=strengthShear(tb,S),sH=strengthBending(hb,S),sT=strengthShear(ht,S),lv=Math.max(sB.v,sH.v,sT.v);
   const ef=Fi>0&&L0<=sp.l1?extensionFatigue(d,D,k,L0,sp.l2,sp.l1,Fi,r1,r2,m,sp.peen):{N:0,below:true,gov:"-"};
   const err=Math.abs(F2/sp.f2-1),fails=[],warns=[];
   if(L0>sp.l1)fails.push("自由長大於安裝長度（無法裝入）");
   if(Fi<=0)fails.push("所需初張力為負值（F1 過小）");
   else if(Fi<it.lo*.85||Fi>it.hi*1.15)fails.push("初張力偏離建議範圍過多");
   else if(Fi<it.lo||Fi>it.hi)warns.push("初張力略超出建議範圍");
   if(err>sp.tol)fails.push(`力量誤差 ${(err*100).toFixed(1)}% 超過容許 ${(sp.tol*100).toFixed(0)}%`);
   if(lv===2)fails.push("應力超過材質安全範圍");else if(lv===1)warns.push("部分應力需預壓除應力");
   if(sp.nTarget&&ef.N<sp.nTarget)fails.push("預估壽命低於目標");
   if(!m.steel)warns.push("疲勞係數以鋼線為基礎，僅供參考");
   out.push({d,D,na,fails,warns,level:lv,weight:PI*d*d/4*PI*D*(na+3),
    v:{OD:D+d,Lb:(na+1)*d,L0,k,Fi,FiLo:it.lo,FiHi:it.hi,F1:sp.f1,F2,tau:tb,hb,ht,rB:sB.r,rH:sH.r,rT:sT.r,N:ef.N,below:ef.below,gov:ef.gov,nf:ef.nf===undefined?0:ef.nf,C:D/d,sut:S}});
  }}
 return Object.assign({kreq},pickDesign(out));
}
function designTorsion(sp){
 const m=sp.mat,M1=sp.f1*sp.armR,M2=sp.f2*sp.armR,kreq=(M2-M1)/(sp.th2-sp.th1),out=[],arms=sp.l1+sp.l2;
 for(const d of STD_D){const S=sut(m,d);
  const Dlo=Math.max((sp.shaftD||0)+d,VR.torsion.cLo*d),Dhi=Math.min(sp.odMax-d,VR.torsion.cHi*d);
  for(let D=Math.ceil(Dlo*4-1e-9)/4;D<=Dhi+1e-9;D+=.25){
   const nb=roundTo(m.E*d**4/(3667*D*kreq)-arms/(3*PI*D),.25);if(nb<2)continue;
   const na=nb+arms/(3*PI*D),k=m.E*d**4/(3667*D*na),M2a=M1+k*(sp.th2-sp.th1),th0=sp.th1-M1/k,thTot=sp.th2-th0;
   const c=D/d,ki=(4*c*c-c-1)/(4*c*(c-1)),sg=M=>ki*32*M/(PI*d**3),s2=sg(M2a),s1=sg(M1);
   const st=strengthBending(s2,S),ft=fatigueBending(s2,s1,d,m,sp.peen),idW=D*nb/(nb+thTot/360)-d,body=(nb+1)*d,err=Math.abs(M2a/M2-1);
   const fails=[],warns=[];
   if(err>sp.tol)fails.push(`扭矩誤差 ${(err*100).toFixed(1)}% 超過容許 ${(sp.tol*100).toFixed(0)}%`);
   if(sp.lenMax&&body>sp.lenMax)fails.push("本體長度超過軸向空間上限");
   if(sp.shaftD&&idW<sp.shaftD*1.02)fails.push("扭緊後內徑小於軸徑（會抱死）");
   if(st.v===2)fails.push("彎曲應力超過 0.78Sut");else if(st.v===1)warns.push("彎曲應力 >0.65Sut，需除應力");
   if(sp.nTarget&&ft.N<sp.nTarget)fails.push("預估壽命低於目標");
   if(!m.steel)warns.push("疲勞係數以鋼線為基礎，僅供參考");
   out.push({d,D,na:nb,fails,warns,level:st.v,weight:PI*d*d/4*PI*D*nb,
    v:{OD:D+d,idW,nb,naEq:na,body,k,M1,M2:M2a,F2:M2a/sp.armR,sigma:s2,ratio:st.r,thTot,N:ft.N,below:ft.below,nf:ft.nf,C:c,sut:S}});
  }}
 return Object.assign({kreq},pickDesign(out));
}
/* ===== 表面處理與熱處理（只做資料、提示與檢核，不改動應力／壽命的計算結果） ===== */
const SURF=[{n:"無（素材，不上油）"},{n:"上油（防鏽油）"},{n:"電鍍鋅（含鈍化）",plate:true},{n:"鋅鎳合金電鍍",plate:true},{n:"電鍍鎳",plate:true},{n:"電鍍鉻",plate:true},
 {n:"磷化（磷酸鋅／錳）",steelOnly:true},{n:"發黑（氧化處理）",steelOnly:true},{n:"達克羅／鋅鋁塗層"},{n:"塗裝（粉體／環氧／電著）"},{n:"PTFE／特氟龍塗層"},
 {n:"鈍化（不鏽鋼）",ssOnly:true},{n:"電解拋光（不鏽鋼）",ssOnly:true},{n:"其他（見規範欄）"}];
const HTS=[{n:"無"},{n:"消除應力（低溫回火）"},{n:"去氫烘烤（電鍍後）"},{n:"淬火＋回火（熱成形／大線徑）"},{n:"時效硬化（沉澱硬化材）"},{n:"氮化（表面硬化）"},{n:"其他熱處理（自訂）"}];
/* 消除應力建議 [溫度°C, 時間min, 下限°C, 上限°C]，依 MATS 順序；來源為彈簧業通用做法（SMI 手冊、線材商資料），非保證值。
   鉻矽鋼線未查到可靠來源，僅為依同類鋼線推估（infer） */
const SR=[[230,60,190,260],[290,30,260,320],[300,30,230,370],[200,60,150,230],[315,60,250,425]];
const BAKE=[200,240,190,220];           // 去氫烘烤：°C、min、下限、上限（參考 ASTM B850，實際依強度等級與客戶規範）
const CARBON=[0,1,4];                   // 碳鋼／合金鋼線：有氫脆風險（不鏽鋼 302/304 與銅合金無）
function htSuggest(mi,hk){
 if(hk===1){const s=SR[mi]||SR[0];return {T:s[0],t:s[1],lo:s[2],hi:s[3],infer:mi===4}}
 if(hk===2)return {T:BAKE[0],t:BAKE[1],lo:BAKE[2],hi:BAKE[3],infer:false};
 /* 淬火＋回火：溫度、時間皆指「回火」；淬火（約 790～900°C 油淬）請寫在表面規範欄或備註。回火範圍參考線材商資料：高碳鋼約 370～480°C，合金鋼約 370～540°C */
 if(hk===3)return mi===4?{T:450,t:60,lo:370,hi:540,infer:false}:{T:430,t:60,lo:370,hi:480,infer:false};
 /* 時效硬化、氮化：未查到可靠的時間資料，數值為依一般做法推估，請以材料或製程規範為準 */
 if(hk===4)return {T:480,t:60,lo:300,hi:570,infer:true};
 if(hk===5)return {T:450,t:240,lo:400,hi:520,infer:true};
 return null}
const fmtMin=t=>t>=60?`${t} min（${(t/60).toFixed(1)} h）`:`${t} min`;
function treatText(inp){
 const sf=SURF[inp.surf||0]||SURF[0],ht=HTS[inp.ht||0]||HTS[0];
 const hasHt=(inp.ht||0)>0,ok=typeof inp.htT==="number"&&typeof inp.htt==="number"&&!isNaN(inp.htT)&&!isNaN(inp.htt);
 return {surf:sf.n,surfn:(inp.surfn||"").trim(),ht:ht.n+(hasHt&&ok?` ${inp.htT}°C × ${fmtMin(inp.htt)}`:hasHt?"（未填溫度／時間）":""),
  htShort:ht.n.replace(/（.*?）/g,"")+(hasHt&&ok?` ${inp.htT}°C×${inp.htt}min`:hasHt?"（未填）":"")}}
/* 工程圖規格欄：表面處理、（規範）、熱處理 */
function treatRows(inp){
 const t=treatText(inp),r=[["表面處理",t.surf]];
 if(t.surfn)r.push(["　表面規範",t.surfn.length>22?t.surfn.slice(0,22)+"…":t.surfn]);
 r.push(["熱處理",t.htShort]);return r}
/* 檢核：回傳 {warns, notes}；mi = 材質索引 */
function treatCheck(inp,mi){
 const m=MATS[mi]||MATS[0],sf=SURF[inp.surf||0]||SURF[0],hk=inp.ht||0,w=[],n=[];
 const T=inp.htT,tt=inp.htt,okTT=typeof T==="number"&&typeof tt==="number"&&!isNaN(T)&&!isNaN(tt)&&T>0&&tt>0;
 const carbon=CARBON.includes(mi);
 if(sf.plate&&carbon&&hk!==2)w.push("電鍍後有氫脆風險：高強度彈簧鋼線電鍍後應盡快做去氫烘烤。請把熱處理設為「去氫烘烤」並填溫度與時間（建議值見下方）。");
 if(sf.plate)n.push("電鍍會降低疲勞壽命（建議搭配珠擊）；本程式的疲勞與壽命估算未計入表面處理的影響。");
 if(sf.steelOnly&&!carbon)w.push("磷化／發黑適用碳鋼與合金鋼線，不適用於此材質。");
 if(sf.ssOnly&&mi!==2)w.push("鈍化／電解拋光僅適用不鏽鋼。");
 if(hk===2&&!sf.plate)w.push("去氫烘烤通常用在電鍍之後；目前表面處理不是電鍍類。");
 if(hk===2&&!carbon)w.push("此材質（不鏽鋼／銅合金）通常不需去氫烘烤。");
 if(hk===3&&![1,4].includes(mi))w.push("淬火＋回火用於熱成形或大線徑的彈簧鋼；此材質為預硬化／冷拉線材，一般不需要，淬火還會改變材料狀態。");
 if(hk===3)n.push("溫度與時間請填「回火」；淬火條件（約 790～900°C 油淬）請記在表面規範欄或備註。");
 if(hk===4)w.push("時效硬化只適用沉澱硬化材質（例如 17-7PH 不鏽鋼、鈹銅）；本程式的材質清單沒有這類材質，請確認材質後再使用。");
 if(hk===5&&![1,4].includes(mi))w.push("氮化通常用於合金鋼線（如鉻矽鋼閥簧）；此材質一般不氮化。");
 if(hk===5)n.push("氮化溫度不可高於線材的回火溫度，否則會軟化。");
 if(hk>0&&!okTT)w.push("請填入熱處理的溫度與時間。");
 const s=htSuggest(mi,hk);
 if(okTT&&s){
  if(T<s.lo||T>s.hi)w.push(`熱處理溫度 ${T}°C 超出一般建議範圍 ${s.lo}～${s.hi}°C。`);
  if(hk===1&&mi===3&&T>230)w.push("磷青銅高於約 230°C 會軟化，建議只做輕度加熱。");
  if(hk===2&&tt<240)w.push("去氫烘烤時間偏短：一般建議至少數小時（≥4 h），確切時間依 ASTM B850 強度等級與客戶規範。");
 }
 if(hk===1&&okTT&&sf.plate)n.push("電鍍件的消除應力通常安排在電鍍前（較低溫度），電鍍後再做去氫烘烤。");
 if(hk>0&&s&&s.infer)n.push(hk===1?"鉻矽鋼線的建議值為依同類鋼線推估，請以線材商或客戶規範為準。":"此項建議值為依一般做法推估（未查到可靠的時間資料），請以材料或製程規範為準。");
 return {warns:w,notes:n,sug:s}}
if(typeof module!=="undefined")module.exports={designCompression,designExtension,designTorsion,allowableLoad,initialTension,MATS,compression,fatigue,fatigueBending,torsion,extension,extensionFatigue,strengthShear,strengthBending,sut};


/* ===== 工程圖產生器（A4 橫向；左：規格、右：示意圖）===== */
const DWG=(()=>{
const FONT="'Noto Sans TC','Microsoft JhengHei','PingFang TC','Heiti TC',sans-serif";
const STD=[.05,.1,.2,.25,.4,.5,1,1.5,2,2.5,4,5,10,20,50];
const esc=s=>String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;");
const n2=x=>(+(+x).toFixed(2));
const fx=(v,n=2)=>Number.isFinite(v)?v.toFixed(n):"∞";
function conv(imp){return {imp,L:v=>imp?v/25.4:v,F:v=>imp?v/4.4482216:v,S:v=>imp?v*145.0377:v,R:v=>imp?v*25.4/4.4482216:v,T:v=>imp?v/(4.4482216*25.4):v,
 uL:imp?"in":"mm",uF:imp?"lbf":"N",uS:imp?"psi":"MPa",uR:imp?"lbf/in":"N/mm",uT:imp?"lbf·in":"N·mm",uTR:imp?"lbf·in/°":"N·mm/°"}}
const SUP=e=>String(e).replace(/\d/g,x=>"⁰¹²³⁴⁵⁶⁷⁸⁹"[x]);
function cyc(N,below,inf){return !Number.isFinite(N)?inf:below?"< 10³ 次":"約 "+(()=>{const e=Math.floor(Math.log10(N)),m=N/10**e;return m.toFixed(1)+"×10"+SUP(e)})()+" 次"}

/* ---------- 規格資料 ---------- */
function specComp(inp){
 const m=MATS[inp.mat||0],c=conv(inp.imp),E=ENDS[inp.end||"cg"],d=inp.d,od=inp.od,D=od-d,S=sut(m,d),k=rate(m.G,d,D,inp.na);
 const L=(v,g=2)=>c.L(v).toFixed(g)+" "+c.uL,rows=[{sec:"尺寸"},["線徑 d",L(d,3)],["外徑 OD",L(od)],["內徑 ID",L(od-2*d)],["中徑 D",L(D)],["旋繞比 C",fx(D/d)],
  ["有效圈數 Na",fx(inp.na)],["總圈數 Nt",fx(E.nt(inp.na))],["端部型式",E.n],["自由長 L0",L(inp.l0)],["壓並高度 Ls",L(E.ls(inp.na,d))],{sec:"性能"},["彈簧常數 k",fx(c.R(k),3)+" "+c.uR]];
 const lws=(inp.lw||[]).filter(x=>x>0).slice(0,3);let first=null;
 lws.forEach((lw,i)=>{const r=compression(d,od,inp.na,inp.l0,lw,m,inp.end||"cg"),st=strengthShear(r.tau,S);if(!first)first={r,lw};
  rows.push([`工作長度 L${i+1}`,L(lw)],["　負荷 F",fx(c.F(r.F))+" "+c.uF],["　剪應力 τ",fx(c.S(r.tau),1)+" "+c.uS],["　τ/Sut 判定",fx(st.r)+" "+VL[st.v]])});
 if(first){const lmax=inp.lmax>0?inp.lmax:inp.l0,tmin=lmax<inp.l0?shear(first.r.k*(inp.l0-lmax),D,d):0,ft=fatigue(first.r.tau,tmin,d,m,!!inp.peen);rows.push(["預估壽命（L1）",cyc(ft.N,ft.below,"無限壽命 (>10⁷ 次)")])}
 rows.push({sec:"材料與處理"},["材質",m.n],["抗拉強度 Sut",fx(c.S(S),0)+" "+c.uS],["珠擊處理",inp.peen?"有":"無"],...treatRows(inp));
 return {name:"壓縮彈簧",code:"C",rows,sum:`Ø${c.L(d).toFixed(2)}×Ø${c.L(od).toFixed(1)}×${c.L(inp.l0).toFixed(1)}`,mat:m.n,dims:{d,od,l0:inp.l0,nt:E.nt(inp.na),na:inp.na},end:inp.end||"cg"}
}
function specTor(inp){
 const m=MATS[inp.mat||0],c=conv(inp.imp),d=inp.d,od=inp.od,D=od-d,S=sut(m,d),nb=inp.nb,arms=inp.l1+inp.l2;
 const L=(v,g=2)=>c.L(v).toFixed(g)+" "+c.uL,b=torsion(d,od,nb,inp.l1,inp.l2,0,inp.armR,m);
 const rows=[{sec:"尺寸"},["線徑 d",L(d,3)],["外徑 OD",L(od)],["內徑 ID",L(od-2*d)],["中徑 D",L(D)],["旋繞比 C",fx(D/d)],["本體圈數 N",fx(nb)],["等效圈數 Na",fx(b.na)],
  ["臂長 L1",L(inp.l1)],["臂長 L2",L(inp.l2)],["受力點半徑 R",L(inp.armR)],["自由角度 α0（兩腳夾角）",fx(typeof inp.fa==="number"?inp.fa:180,1)+" 度"],["旋向",inp.hand===1?"左旋":"右旋"],["本體長度 (N+1)d",L((nb+1)*d)],{sec:"性能"},["扭力常數 k",fx(c.T(b.kd),3)+" "+c.uTR]];
 const angs=(inp.ang||[]).filter(x=>x>0).slice(0,3);let first=null;
 angs.forEach((a,i)=>{const r=torsion(d,od,nb,inp.l1,inp.l2,a,inp.armR,m),st=strengthBending(r.sig,S);if(!first)first={r,a};
  rows.push([`扭轉角 θ${i+1}`,fx(a,1)+" 度"],["　工作時兩腳夾角",fx((typeof inp.fa==="number"?inp.fa:180)-a,1)+" 度"],["　扭矩 M",fx(c.T(r.M),2)+" "+c.uT],["　受力點負荷 F",fx(c.F(r.F))+" "+c.uF],["　彎曲應力 σ",fx(c.S(r.sig),1)+" "+c.uS],["　σ/Sut 判定",fx(st.r)+" "+VL[st.v]])});
 if(first){const am=inp.amin||0,smin=first.a>0?first.r.sig*am/first.a:0,ft=fatigueBending(first.r.sig,smin,d,m,!!inp.peen);rows.push(["預估壽命（θ1）",cyc(ft.N,ft.below,"無限壽命 (>10⁶ 次)")])}
 rows.push({sec:"材料與處理"},["材質",m.n],["抗拉強度 Sut",fx(c.S(S),0)+" "+c.uS],["珠擊處理",inp.peen?"有":"無"],...treatRows(inp));
 return {name:"扭力彈簧",code:"T",rows,sum:`Ø${c.L(d).toFixed(2)}×Ø${c.L(od).toFixed(1)}×N${fx(nb)}`,mat:m.n,dims:{d,od,nb,l1:inp.l1,l2:inp.l2,th:angs[0]||90,fa:typeof inp.fa==="number"?inp.fa:180,hand:inp.hand===1?1:0}}
}
function specExt(inp){
 const m=MATS[inp.mat||0],c=conv(inp.imp),d=inp.d,od=inp.od,D=od-d,S=sut(m,d),k=rate(m.G,d,D,inp.na);
 const L=(v,g=2)=>c.L(v).toFixed(g)+" "+c.uL,fm=inp.fm>0?inp.fm:inp.fi;
 const rows=[{sec:"尺寸"},["線徑 d",L(d,3)],["外徑 OD",L(od)],["內徑 ID",L(od-2*d)],["中徑 D",L(D)],["旋繞比 C",fx(D/d)],["有效圈數 Na",fx(inp.na)],["本體長度 Lb",L((inp.na+1)*d)],
  ["自由長 L0（鉤內）",L(inp.l0)],["鉤型",inp.hook===1?"圓環鉤":"長開口鉤"],["鉤環中心半徑 r1",L(inp.r1)],["鉤側彎半徑 r2",L(inp.r2)],{sec:"性能"},["彈簧常數 k",fx(c.R(k),3)+" "+c.uR],["初張力 Fi",fx(c.F(inp.fi))+" "+c.uF]];
 const lws=(inp.lw||[]).filter(x=>x>0).slice(0,3);let first=null;
 lws.forEach((lw,i)=>{const r=extension(d,od,inp.na,inp.l0,lw,inp.fi,fm,m,inp.r1,inp.r2),a=strengthShear(r.tau,S),b=strengthBending(r.hb,S);if(!first)first={lw};
  rows.push([`工作長度 L${i+1}`,L(lw)],["　負荷 F",fx(c.F(r.F))+" "+c.uF],["　本體 τ",fx(c.S(r.tau),1)+" "+c.uS],["　τ/Sut 判定",fx(a.r)+" "+VL[a.v]],["　鉤環彎曲 σ",fx(c.S(r.hb),1)+" "+c.uS],["　σ/Sut 判定",fx(b.r)+" "+VL[b.v]])});
 if(first){const lmin=inp.lmin>0?inp.lmin:inp.l0,ef=extensionFatigue(d,D,k,inp.l0,first.lw,lmin,inp.fi,inp.r1,inp.r2,m,!!inp.peen);rows.push([`預估壽命（取最短：${ef.gov}）`,cyc(ef.N,ef.below,"無限壽命")])}
 rows.push({sec:"材料與處理"},["材質",m.n],["抗拉強度 Sut",fx(c.S(S),0)+" "+c.uS],["珠擊處理",inp.peen?"有":"無"],...treatRows(inp));
 return {name:"拉伸彈簧",code:"E",rows,sum:`Ø${c.L(d).toFixed(2)}×Ø${c.L(od).toFixed(1)}×${c.L(inp.l0).toFixed(1)}`,mat:m.n,dims:{d,od,na:inp.na,l0:inp.l0,r1:inp.r1}}
}
function spec(inp){return inp.type===1?specTor(inp):inp.type===2?specExt(inp):specComp(inp)}

/* ---------- 繪圖基本元件（單位：mm，圖紙座標） ---------- */
const ln=(x1,y1,x2,y2,w=.25,ex="")=>`<line x1="${n2(x1)}" y1="${n2(y1)}" x2="${n2(x2)}" y2="${n2(y2)}" stroke="#000" stroke-width="${w}" ${ex}/>`;
const tx=(x,y,t,sz=3,a="start",ex="")=>{const fl=(ex.match(/fill="([^"]+)"/)||[])[1]||"#000";return `<text x="${n2(x)}" y="${n2(y)}" font-family="${FONT}" style="font-size:${sz}px;text-anchor:${a};dominant-baseline:auto;fill:${fl}" ${ex.replace(/fill="[^"]*"/,"")}>${esc(t)}</text>`};
const rect=(x,y,w,h,sw=.25,fill="none")=>`<rect x="${n2(x)}" y="${n2(y)}" width="${n2(w)}" height="${n2(h)}" fill="${fill}" stroke="#000" stroke-width="${sw}"/>`;
const arrow=(x,y,ang)=>{const a=ang*Math.PI/180,l=2.4,w=.75,bx=x-Math.cos(a)*l,by=y-Math.sin(a)*l,nx=-Math.sin(a)*w,ny=Math.cos(a)*w;return `<polygon points="${n2(x)},${n2(y)} ${n2(bx+nx)},${n2(by+ny)} ${n2(bx-nx)},${n2(by-ny)}" fill="#000"/>`};
function dimH(x1,x2,y,t,e1,e2){let s="";if(e1!==undefined)s+=ln(x1,e1,x1,y+(y>e1?1.5:-1.5),.18);if(e2!==undefined)s+=ln(x2,e2,x2,y+(y>e2?1.5:-1.5),.18);
 return s+ln(x1,y,x2,y,.2)+arrow(x1,y,180)+arrow(x2,y,0)+tx((x1+x2)/2,y-1,t,3,"middle")}
function dimV(x,y1,y2,t,e1,e2){let s="";if(e1!==undefined)s+=ln(e1,y1,x+(x>e1?1.5:-1.5),y1,.18);if(e2!==undefined)s+=ln(e2,y2,x+(x>e2?1.5:-1.5),y2,.18);
 return s+ln(x,y1,x,y2,.2)+arrow(x,y1,-90)+arrow(x,y2,90)+tx(x-1.2,(y1+y2)/2,t,3,"middle",`transform="rotate(-90 ${n2(x-1.2)} ${n2((y1+y2)/2)})"`)}
const wireSeg=(x1,y1,x2,y2,w)=>{const o=Math.max(w,.4);return `<line x1="${n2(x1)}" y1="${n2(y1)}" x2="${n2(x2)}" y2="${n2(y2)}" stroke="#000" stroke-width="${n2(o)}" stroke-linecap="round"/>`+(o>1.1?`<line x1="${n2(x1)}" y1="${n2(y1)}" x2="${n2(x2)}" y2="${n2(y2)}" stroke="#fff" stroke-width="${n2(o-.6)}" stroke-linecap="round"/>`:"")};
const pickScale=(w,h,maxW,maxH)=>{const lim=Math.min(maxW/Math.max(w,1e-6),maxH/Math.max(h,1e-6));let s=STD[0];for(const v of STD)if(v<=lim)s=v;return s};
const scaleTxt=s=>s>=1?(Number.isInteger(s)?s+":1":s+":1"):("1:"+n2(1/s));
const center=(x1,y1,x2,y2)=>ln(x1,y1,x2,y2,.15,'stroke-dasharray="4 1 .6 1"');

/* ---------- 工程圖視圖：由 3D 中心線（springpath.js）直接投影，和 3D 模型是同一份幾何 ----------
 * 線材投影 = 中心線投影後以線徑描粗；每小段依「離觀看者的距離」由遠到近繪製，前後交疊處自然有遮擋。
 * 版面：視圖、尺寸線、圖名、說明文字各自占固定區塊，互不重疊，也不碰框線。 */
const VIEWS={
 front:{n:"前視圖",pt:p=>[p[0],p[2]],dist:p=>p[1]},                 // 由 −Y 看向 +Y：右＝+X、上＝+Z
 top:{n:"俯視圖（由軸線上方看）",pt:p=>[p[0],p[1]],dist:p=>-p[2]},      // 由 +Z 往下看：右＝+X、上＝+Y
 right:{n:"右視圖",pt:p=>[p[1],p[2]],dist:p=>-p[0]}                    // 由 +X 看向 −X：右＝+Y、上＝+Z
};
function drawProj(sp,A,c,inp){
 const SPH=window.SpringPath;let path;
 const fail=m=>({svg:wrap("無法由 3D 幾何產生投影："+m,56).map((l,i)=>tx(A.x+4,A.y+24+i*4.2,l,3)).join(""),scale:1});
 if(!SPH)return fail("springpath.js 未載入");
 try{path=SPH.springPath(inp,null)}catch(e){return fail(e.message)}
 const m=path.meta,d=m.d,type=inp.type||0,chunks=SPH.samplePath(path,6,45);
 let tPath=null;
 if(type===1&&sp.dims.th>0){try{tPath=SPH.samplePath(SPH.springPath(inp,{th:sp.dims.th}),6,45)}catch(e){tPath=null}}
 const ids=type===0?["front"]:type===1?["top","front"]:["front","right"];
 const views=ids.map(id=>{const V=VIEWS[id],pr=chunks.map(ch=>({pts:ch.map(V.pt),dist:ch.reduce((a,p)=>a+V.dist(p),0)/ch.length}));
  const dash=id==="top"&&tPath?tPath.map(ch=>ch.map(V.pt)):[];
  let x0=1e9,x1=-1e9,y0=1e9,y1=-1e9;const eat=p=>{x0=Math.min(x0,p[0]-d/2);x1=Math.max(x1,p[0]+d/2);y0=Math.min(y0,p[1]-d/2);y1=Math.max(y1,p[1]+d/2)};
  pr.forEach(q=>q.pts.forEach(eat));dash.forEach(q=>q.forEach(eat));
  const clip=type===0&&path.clipZ&&id==="front";if(clip){y0=path.clipZ[0];y1=path.clipZ[1]}
  return {id,V,pr,dash,x0,x1,y0,y1,clip,pad:{l:6,r:id==="front"?22:6,t:6,b:16}}});
 /* 版面：視圖區 R0（不含標題列與下方說明區）；每個視圖另留邊距給尺寸線與圖名 */
 const R0={x:A.x+2,y:A.y+8,w:A.w-4,h:A.h-8-24},gap=4;
 const sumW=views.reduce((a,v)=>a+v.x1-v.x0,0),sumPad=views.reduce((a,v)=>a+v.pad.l+v.pad.r,0)+gap*(views.length-1);
 const maxH=Math.max(...views.map(v=>v.y1-v.y0)),padV=views[0].pad.t+views[0].pad.b;
 /* 並排或上下堆疊：兩種排法各算一次標準比例，取比例較大的（圖較大）。 */
 const sumH=views.reduce((a,v)=>a+v.y1-v.y0,0),maxW=Math.max(...views.map(v=>v.x1-v.x0)),padH=Math.max(...views.map(v=>v.pad.l+v.pad.r));
 const sumPadV=views.reduce((a,v)=>a+v.pad.t+v.pad.b,0)+gap*(views.length-1);
 const sSide=pickScale(sumW,maxH,R0.w-sumPad,R0.h-padV),sStack=views.length>1?pickScale(maxW,sumH,R0.w-padH,R0.h-sumPadV):0;
 const stack=sStack>sSide,s=stack?sStack:sSide;
 let o="",cx=R0.x+(R0.w-(sumW*s+sumPad))/2,cyv=R0.y+(R0.h-(sumH*s+sumPadV))/2;const w=Math.max(d*s,.4);
 views.forEach(v=>{
  const W=(v.x1-v.x0)*s,H=(v.y1-v.y0)*s;
  const L=stack?R0.x+(R0.w-(W+v.pad.l+v.pad.r))/2+v.pad.l:cx+v.pad.l,T=stack?cyv+v.pad.t:R0.y+v.pad.t+((R0.h-padV)-H)/2;
  const X=px=>L+(px-v.x0)*s,Y=py=>T+(v.y1-py)*s,pd=pts=>"M"+pts.map(p=>n2(X(p[0]))+","+n2(Y(p[1]))).join(" L");
  if(v.id==="top")o+=center(X(0)-(m.od/2)*s-3,Y(0),X(0)+(m.od/2)*s+3,Y(0))+center(X(0),Y(0)-(m.od/2)*s-3,X(0),Y(0)+(m.od/2)*s+3);
  else o+=center(X(0),T-3,X(0),T+H+3);
  const cid="cz"+v.id;
  if(v.clip)o+=`<defs><clipPath id="${cid}"><rect x="${n2(L-2)}" y="${n2(Y(path.clipZ[1]))}" width="${n2(W+4)}" height="${n2((path.clipZ[1]-path.clipZ[0])*s)}"/></clipPath></defs><g clip-path="url(#${cid})">`;
  v.pr.slice().sort((a,b)=>b.dist-a.dist).forEach(q=>{
   o+=`<path d="${pd(q.pts)}" fill="none" stroke="#000" stroke-width="${n2(w)}" stroke-linecap="butt" stroke-linejoin="round"/>`;
   if(w>1.1)o+=`<path d="${pd(q.pts)}" fill="none" stroke="#fff" stroke-width="${n2(w-.6)}" stroke-linecap="butt" stroke-linejoin="round"/>`});
  if(v.clip)o+="</g>";
  v.dash.forEach(q=>{o+=`<path d="${pd(q)}" fill="none" stroke="#000" stroke-width=".3" stroke-dasharray="3 1.5"/>`});
  /* 尺寸線（落在各視圖自己的邊距內） */
  if(v.id==="front"){
   const rx=L+W,bottom=T+H;
   if(type===0)o+=dimV(rx+14,T,bottom,c.L(m.len).toFixed(2),rx+1,rx+1);
   else if(type===2)o+=dimV(rx+14,Y(m.free),Y(0),c.L(m.free).toFixed(2),rx+1,rx+1);
   else{const hz=m.bodyZ[1]-m.bodyZ[0];o+=dimV(rx+14,Y(m.bodyZ[1]),Y(m.bodyZ[0]),c.L(hz).toFixed(2),rx+1,rx+1)}
   o+=dimH(X(-m.od/2),X(m.od/2),bottom+8,"Ø"+c.L(m.od).toFixed(2),type===0?bottom+1:Y(m.bodyZ[0]),type===0?bottom+1:Y(m.bodyZ[0]));
  }
  o+=tx(L+W/2,T+H+14.5,v.V.n,2.8,"middle");
  cx+=v.pad.l+W+v.pad.r+gap;cyv+=v.pad.t+H+v.pad.b+gap;
 });
 /* 說明區（兩欄，固定在視圖區下方） */
 const Ls=x=>c.L(x).toFixed(2),lg=[`線徑 d = ${c.L(d).toFixed(3)} ${c.uL}`];
 if(type===0)lg.push(`有效圈數 Na = ${+m.na.toFixed(2)}　總圈數 Nt = ${+sp.dims.nt.toFixed(2)}`,`端部：${(window.SpringPath.ENDS||{})[m.end]||m.end}`,`自由長 L0 = ${Ls(m.free)} ${c.uL}`);
 else if(type===1)lg.push(`本體圈數 N = ${+m.nb.toFixed(2)}　旋向：${m.hand?"左旋":"右旋"}`,`臂長 L1 = ${Ls(m.l1)}　L2 = ${Ls(m.l2)} ${c.uL}`,`自由角度 α0 = ${m.fa.toFixed(1)}°（扭緊時變小）`,`θ = ${sp.dims.th.toFixed(1)}°（虛線＝工作位置，夾角 ${(m.fa-sp.dims.th).toFixed(1)}°）`);
 else lg.push(`有效圈數 Na = ${+m.na.toFixed(2)}　鉤型：${m.hook?"圓環鉤":"長開口鉤"}`,`鉤中心半徑 r1 = ${Ls(m.r1)} ${c.uL}`,`鉤側彎半徑 r2 = ${Ls(m.r2)} ${c.uL}`,`自由長（鉤內）L0 = ${Ls(m.free)} ${c.uL}`);
 lg.forEach((t,i)=>{o+=tx(A.x+3+(i>=3?80:0),A.y+A.h-16+(i%3)*4.6,t,2.8,"start")});
 return {svg:o,scale:s}}
/* ---------- 圖紙 ---------- */
function wrap(t,n){const o=[];let cur="",w=0;for(const ch of t){const cw=ch.charCodeAt(0)>255?2:1;if(w+cw>n){o.push(cur);cur="";w=0}cur+=ch;w+=cw}if(cur)o.push(cur);return o}
function render(inp){
 const sp=spec(inp),c=conv(!!inp.imp),A={x:124,y:12,w:161,h:156};
 const dr=drawProj(sp,A,c,inp);
 const dt=new Date(),pad=n=>String(n).padStart(2,"0"),date=`${dt.getFullYear()}-${pad(dt.getMonth()+1)}-${pad(dt.getDate())}`,no=`SP-${sp.code}-${date.replace(/-/g,"")}-${pad(dt.getHours())}${pad(dt.getMinutes())}`;
 let o=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 297 210" width="297mm" height="210mm" style="background:#fff">`;
 o+=`<rect x="0" y="0" width="297" height="210" fill="#fff"/>`+rect(5,5,287,200,.35)+rect(10,10,277,190,.7);
 // 左：規格
 o+=rect(10,10,112,8,.4,"#e6e6e6")+tx(66,15.7,"規格 SPECIFICATION",3.6,"middle","font-weight=\"bold\"");
 let y=18;const rh=Math.min(5.3,148/sp.rows.length);
 for(const r of sp.rows){if(y+rh>166)break;
  if(r.sec){o+=rect(10,y,112,rh,.2,"#f1f1f1")+tx(12,y+3.8,r.sec,3,"start","font-weight=\"bold\"")}
  else{o+=ln(10,y+rh,122,y+rh,.12)+ln(62,y,62,y+rh,.12)+tx(12,y+3.8,r[0],2.9)+tx(64,y+3.8,r[1],2.9)}
  y+=rh}
 o+=ln(10,y,122,y,.4);
 const notes=["未註明公差者，依雙方約定；本圖尺寸為計算值，未含公差。","本圖由「彈簧計算器」自動產生，疲勞與強度為教科書估算值，製造前請覆核。","珠擊處理："+(inp.peen?"有。":"無。")+" 材質："+sp.mat+"。"];
 o+=rect(10,168,112,32,.4)+tx(12,172.2,"技術要求 NOTES",3,"start","font-weight=\"bold\"");let ny=176;
 notes.forEach((t,i)=>{const ls=wrap(`${i+1}. ${t}`,50);ls.forEach((l,j)=>{o+=tx(12,ny,l,2.5);ny+=3.3})});
 // 右：示意圖
 o+=rect(122,10,165,158,.4)+tx(124.5,15,"示意圖 DRAWING",3,"start","font-weight=\"bold\"")+`<g>${dr.svg}</g>`;
 // 標題欄
 const T=(x,yy,w,h,k,v,big)=>rect(x,yy,w,h,.3)+tx(x+1.2,yy+3,k,2.2,"start",'fill="#555"')+tx(x+w/2,yy+h-2.6,v,big?4.6:3.2,"middle",big?'font-weight="bold"':"");
 o+=T(122,168,95,11,"品名 TITLE",sp.name+"　"+sp.sum,true)+T(217,168,70,11,"圖號 DWG NO.",no)+
    T(122,179,60,10.5,"材質 MATERIAL",sp.mat)+T(182,179,35,10.5,"比例 SCALE",scaleTxt(dr.scale))+T(217,179,35,10.5,"單位 UNIT",c.uL)+T(252,179,35,10.5,"日期 DATE",date)+
    T(122,189.5,95,10.5,"備註 REMARK","非正式製造圖，僅供設計參考")+T(217,189.5,70,10.5,"繪製 BY","彈簧計算器");
 return o+"</svg>";
}
/* 由計算頁輸入組出 inp（SI：mm、N、MPa）；imp 只影響顯示單位 */
return {render,spec};
})();


