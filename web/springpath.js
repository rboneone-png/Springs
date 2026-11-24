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
