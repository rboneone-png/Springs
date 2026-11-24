/* ===== 彈簧 3D 模型與 STEP 輸出（純前端；replicad / OpenCascade WASM + three.js）=====
 * 輸入 inp 為計算器內部使用的 SI 單位（mm），欄位同 inpFromTab() 的輸出。
 * 全部計算在使用者瀏覽器內完成，不連線任何外部服務。
 */
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import {
  setOC, makeHelix, makeLine, makeTangentArc, makeThreePointArc,
  makeCylinder, assembleWire, genericSweep, measureVolume, getOC,
} from "./vendor/replicad/replicad.js";
import opencascade from "./vendor/opencascade/replicad_single.js";

const DEG = 180 / Math.PI;
/* 密合圈之間保留 1% 線徑的間隙（d=2 約 0.02 mm）：相鄰圈剛好相切、或間隙小於 CAD 的建模容差
 * （SolidWorks 約 0.01 mm）時，舊版 CAD 會把兩圈判成相交而「破圖」。 */
const CLOSE_GAP = 1.01;
const ENDS = {
  cg: "閉端磨平", c: "閉端不磨", o: "開端不磨", og: "開端磨平",
};

let ocReady = null;
function initOC() {
  if (!ocReady) {
    ocReady = opencascade({
      locateFile: () => new URL("./vendor/opencascade/replicad_single.wasm", import.meta.url).href,
    }).then((oc) => { setOC(oc); });
  }
  return ocReady;
}

/* ---------- 幾何小工具 ---------- */
const P = (v) => [v.x, v.y, v.z];
const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const mul = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const unit = (a) => { const n = Math.hypot(a[0], a[1], a[2]); return [a[0] / n, a[1] / n, a[2] / n]; };
const r3 = (x) => +Number(x).toFixed(3);                              // 顯示用：最多 3 位小數
const num = (x, name) => {
  if (typeof x !== "number" || !isFinite(x)) throw new Error(`參數 ${name} 無效`);
  return x;
};

/* 一段螺旋：turns 圈、節距 pitch；起點角度 angle0（度）、起點高度 z0 */
/* 螺旋切成每段最多半圈：每張曲面只覆蓋一小段，舊版 CAD（SolidWorks 2015）才讀得正確 */
function helixPieces(R, pitch, turns, z0, angle0) {
  const out = [];
  let left = turns, ang = angle0, z = z0;
  while (left > 1e-9) {
    const t = Math.min(0.5, left);
    let w = makeHelix(pitch, pitch * t, R);
    if (Math.abs(ang % 360) > 1e-9) w = w.rotate(ang % 360, [0, 0, 0], [0, 0, 1]);
    out.push(w.translate(0, 0, z));
    ang += t * 360; z += pitch * t; left -= t;
  }
  return out;
}

/* 沿 spine 掃出直徑 d 的圓線材。
 * 輪廓用「兩個半圓弧」而不是一個整圓：整圓會產生週期曲面，STEP 內變成首尾不夾緊的 B-spline，
 * 舊版 CAD 讀進來會破圖；兩個半圓弧則各是標準的非週期曲面。 */
function sweepWire(spine, d) {
  const s = P(spine.startPoint), t = unit(P(spine.tangentAt(0)));
  const ref = Math.abs(t[2]) < 0.9 ? [0, 0, 1] : [1, 0, 0];
  const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
  const u = unit(cross(t, ref)), v = cross(t, u), r = d / 2;
  const at = (a, b) => add(s, add(mul(u, r * a), mul(v, r * b)));
  const profile = assembleWire([
    makeThreePointArc(at(1, 0), at(0, 1), at(-1, 0)),
    makeThreePointArc(at(-1, 0), at(0, -1), at(1, 0)),
  ]);
  return genericSweep(profile, spine, { transitionMode: "transformed" });
}

/* ---------- 壓縮彈簧 ---------- */
function compressionShape(inp) {
  const d = num(inp.d, "d"), od = num(inp.od, "OD"), na = num(inp.na, "Na"), l0 = num(inp.l0, "L0");
  const end = inp.end || "cg", R = (od - d) / 2;
  if (!(na > 0)) throw new Error("有效圈數需大於 0");
  const closed = end === "cg" || end === "c", ground = end === "cg" || end === "og";
  let p, z0, secs;
  if (end === "cg") { p = (l0 - 2 * d) / na; z0 = 0; }
  else if (end === "c") { p = (l0 - 3 * d) / na; z0 = d / 2; }
  else if (end === "o") { p = (l0 - d) / na; z0 = d / 2; }
  else { p = l0 / (na + 1); z0 = 0; }
  if (p < d * CLOSE_GAP - 1e-9) {
    throw new Error(`有效圈節距 ${p.toFixed(3)} mm 小於線徑 ${d} mm，線圈會互相穿透。請檢查自由長 L0 與有效圈數 Na。`);
  }
  if (closed) secs = [[1, d * CLOSE_GAP], [na, p], [1, d * CLOSE_GAP]];
  else secs = [[end === "og" ? na + 1 : na, p]];

  let z = z0, ang = 0;
  const wires = [];
  for (const [turns, pitch] of secs) {
    wires.push(...helixPieces(R, pitch, turns, z, ang));
    z += turns * pitch; ang += turns * 360;
  }
  let solid = sweepWire(assembleWire(wires), d);
  if (ground) {
    const cut = makeCylinder(od, l0, [0, 0, 0], [0, 0, 1]);
    solid = solid.intersect(cut);
  }
  return {
    shape: solid, name: "Compression spring",
    note: `壓縮彈簧　${ENDS[end] || end}　線徑 ${r3(d)}　外徑 ${r3(od)}　有效圈 ${r3(na)}　自由長 ${r3(l0)}`,
  };
}

/* ---------- 扭力彈簧（自由狀態；兩腳在水平面內沿螺旋切線方向伸出）---------- */
function torsionShape(inp) {
  const d = num(inp.d, "d"), od = num(inp.od, "OD"), nb = num(inp.nb, "N");
  const l1 = num(inp.l1, "L1"), l2 = num(inp.l2, "L2"), R = (od - d) / 2;
  if (!(nb > 0)) throw new Error("本體圈數需大於 0");
  if (!(l1 > 0 && l2 > 0)) throw new Error("臂長 L1、L2 需大於 0");
  const pcs = helixPieces(R, d * CLOSE_GAP, nb, d / 2, 0);
  const first = pcs[0], last = pcs[pcs.length - 1];
  const S = P(first.startPoint), E = P(last.endPoint);
  const hz = (v) => unit([v[0], v[1], 0]);                          // 腳在水平面內（垂直於軸線）
  const t0 = hz(P(first.tangentAt(0))), t1 = hz(P(last.tangentAt(1)));
  const leg1 = makeLine(sub(S, mul(t0, l1)), S);
  const leg2 = makeLine(E, add(E, mul(t1, l2)));
  const solid = sweepWire(assembleWire([leg1, ...pcs, leg2]), d);
  return {
    shape: solid, name: "Torsion spring",
    note: `扭力彈簧（自由狀態）　線徑 ${r3(d)}　外徑 ${r3(od)}　本體圈數 ${r3(nb)}　臂長 ${r3(l1)} / ${r3(l2)}`,
  };
}

/* ---------- 拉伸彈簧（密繞本體 + 兩端圓環鉤）----------
 * 鉤的路徑（由本體端點起算）：90° 側彎（半徑 r2）→ 直頸 → 圓環（中心半徑 r1，留開口）。
 * 圓環所在平面與軸線平行，並從軸線平面偏移 r2；這是簡化的機械鉤形，不是各廠專屬鉤形。
 */
function hookEdges(anchor, tAnchor, rHat, sgn, d, r1, r2, neck) {
  /* sgn=+1：上鉤（往 +z）；sgn=-1：下鉤（往 -z）。回傳的邊以「離開本體」的方向建立，
   * 下鉤另備 ringReverse 供組成與螺旋同向的路徑。 */
  const Tout = sgn > 0 ? tAnchor : mul(tAnchor, -1);              // 離開本體的切線方向
  const Tdir = unit([Tout[0], Tout[1], 0]);
  const Z = [0, 0, sgn];
  const q1 = add(add(anchor, mul(Tdir, r2)), mul(Z, r2));          // 側彎結束點
  const q2 = add(q1, mul(Z, neck));                                // 直頸結束點
  const cen = sub(q2, mul(rHat, r1));                              // 圓環中心
  const gap = Math.acos(Math.max(-1, 1 - (1.15 * d) / r1));        // 開口角：尖端與直頸至少隔 1.15 d
  const A = 2 * Math.PI - gap;
  const pt = (a) => add(cen, add(mul(rHat, r1 * Math.cos(a)), [0, 0, sgn * r1 * Math.sin(a)]));
  return {
    arc1: makeTangentArc(anchor, Tout, q1),
    neck: neck > 1e-6 ? makeLine(q1, q2) : null,
    ring: makeThreePointArc(pt(0), pt(A / 2), pt(A)),
    ringReverse: makeThreePointArc(pt(A), pt(A / 2), pt(0)),
    q1, q2,
  };
}

function extensionShape(inp) {
  const d = num(inp.d, "d"), od = num(inp.od, "OD"), na = num(inp.na, "Na");
  const l0 = num(inp.l0, "L0"), r1 = num(inp.r1, "r1"), r2 = num(inp.r2, "r2");
  const R = (od - d) / 2;
  if (!(na > 0)) throw new Error("有效圈數需大於 0");
  if (!(r1 > d / 2 && r2 > 0)) throw new Error("鉤環半徑 r1 需大於 d/2，r2 需大於 0");
  const pitch = d * CLOSE_GAP;
  const bodyLen = na * pitch + d;                // 實際本體長度（含圈間間隙）
  const h = (l0 - bodyLen) / 2;                  // 每個鉤：本體端面到鉤孔內側的長度
  const neck = h - r1 - r2 + d;                  // 側彎與圓環之間的直頸長度
  if (neck < -1e-6) {
    throw new Error(`自由長 L0 太短，容納不下鉤環（需要每鉤長度 ≥ r1 + r2 − d = ${(r1 + r2 - d).toFixed(2)} mm，目前 ${h.toFixed(2)} mm）。請加大 L0，或縮小 r1、r2。`);
  }
  if (h < 2 * r1 + 0.3) {
    throw new Error(`自由長 L0 太短，鉤環會碰到本體（每鉤長度需 ≥ 2·r1 = ${(2 * r1).toFixed(2)} mm，目前 ${h.toFixed(2)} mm）。`);
  }
  const bz0 = h;                                  // 本體底面；下鉤孔內側為 z = 0
  const body = helixPieces(R, pitch, na, bz0 + d / 2, 0);
  const S = P(body[0].startPoint), E = P(body[body.length - 1].endPoint);
  const tS = unit(P(body[0].tangentAt(0))), tE = unit(P(body[body.length - 1].tangentAt(1)));
  const rS = unit([S[0], S[1], 0]), rE = unit([E[0], E[1], 0]);
  const top = hookEdges(E, tE, rE, +1, d, r1, r2, Math.max(neck, 0));
  const bot = hookEdges(S, tS, rS, -1, d, r1, r2, Math.max(neck, 0));
  /* 下鉤：由圓環尖端 → 圓環 → 直頸 → 側彎 → 本體起點（與螺旋方向一致） */
  const botEdges = [bot.ringReverse];
  if (bot.neck) botEdges.push(makeLine(bot.q2, bot.q1));
  botEdges.push(makeTangentArc(bot.q1, [0, 0, 1], S));
  const topEdges = [top.arc1];
  if (top.neck) topEdges.push(top.neck);
  topEdges.push(top.ring);
  const solid = sweepWire(assembleWire([...botEdges, ...body, ...topEdges]), d);
  return {
    shape: solid, name: "Extension spring",
    note: `拉伸彈簧（圓環鉤）　線徑 ${r3(d)}　外徑 ${r3(od)}　有效圈 ${r3(na)}　自由長（鉤內）${r3(l0)}　r1 ${r3(r1)}　r2 ${r3(r2)}`,
  };
}

export function buildSpring(inp) {
  if (inp.type === 1) return torsionShape(inp);
  if (inp.type === 2) return extensionShape(inp);
  return compressionShape(inp);
}

/* ---------- 檢視器（three.js） ---------- */
let viewer = null;
function disposeViewer() {
  if (!viewer) return;
  cancelAnimationFrame(viewer.raf);
  viewer.ro.disconnect();
  viewer.controls.dispose();
  viewer.scene.traverse((o) => { o.geometry?.dispose?.(); o.material?.dispose?.(); });
  viewer.renderer.dispose();
  viewer.renderer.domElement.remove();
  viewer = null;
}

function showMesh(container, mesh, edgesLines) {
  disposeViewer();
  const dark = matchMedia("(prefers-color-scheme: dark)").matches;
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
  container.appendChild(renderer.domElement);
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(dark ? 0x10181a : 0xeef2f4);
  const geo = new THREE.BufferGeometry();
  geo.setAttribute("position", new THREE.Float32BufferAttribute(mesh.vertices, 3));
  geo.setAttribute("normal", new THREE.Float32BufferAttribute(mesh.normals, 3));
  geo.setIndex(new THREE.BufferAttribute(new Uint32Array(mesh.triangles), 1));
  geo.computeBoundingSphere();
  const mat = new THREE.MeshStandardMaterial({ color: 0x9aa7b1, metalness: 0.75, roughness: 0.35 });
  const obj = new THREE.Mesh(geo, mat);
  scene.add(obj);
  scene.add(new THREE.HemisphereLight(0xffffff, 0x445566, 1.1));
  const l1 = new THREE.DirectionalLight(0xffffff, 2.2); l1.position.set(1, -1.5, 2); scene.add(l1);
  const l2 = new THREE.DirectionalLight(0xffffff, 0.9); l2.position.set(-2, 1, -1); scene.add(l2);
  const c = geo.boundingSphere.center, r = geo.boundingSphere.radius;
  const camera = new THREE.PerspectiveCamera(35, 1, r / 50, r * 50);
  camera.up.set(0, 0, 1);
  camera.position.set(c.x + r * 2.6, c.y - r * 2.6, c.z + r * 1.6);
  const controls = new OrbitControls(camera, renderer.domElement);
  controls.target.copy(c);
  controls.enableDamping = true;
  const resize = () => {
    const w = container.clientWidth || 600, h = container.clientHeight || 400;
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  };
  const ro = new ResizeObserver(resize); ro.observe(container); resize();
  viewer = { renderer, scene, camera, controls, ro, raf: 0 };
  const loop = () => { viewer.raf = requestAnimationFrame(loop); controls.update(); renderer.render(scene, camera); };
  loop();
  void edgesLines;
}

/* 由網格計算外形尺寸與體積（有向四面體體積和）；OCC 的包圍盒偏鬆，不用來顯示尺寸 */
function meshStats(mesh) {
  const v = mesh.vertices, t = mesh.triangles;
  const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < v.length; i += 3) for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], v[i + k]); hi[k] = Math.max(hi[k], v[i + k]); }
  let vol = 0;
  for (let i = 0; i < t.length; i += 3) {
    const a = t[i] * 3, b = t[i + 1] * 3, c = t[i + 2] * 3;
    vol += (v[a] * (v[b + 1] * v[c + 2] - v[b + 2] * v[c + 1])
      - v[a + 1] * (v[b] * v[c + 2] - v[b + 2] * v[c])
      + v[a + 2] * (v[b] * v[c + 1] - v[b + 1] * v[c])) / 6;
  }
  return { sx: hi[0] - lo[0], sy: hi[1] - lo[1], sz: hi[2] - lo[2], vol: Math.abs(vol), lo, hi };
}

/* ---------- 對外介面 ---------- */
let current = null;
const $ = (s) => document.querySelector(s);

export async function open(inp) {
  const status = $("#s3dstat"), body = $("#s3dbody"), dlStep = $("#s3dstep"), dlStl = $("#s3dstl"), dlStep203 = $("#s3dstep203");
  current = null; dlStep.disabled = true; dlStl.disabled = true; dlStep203.disabled = true;
  status.textContent = "載入 3D 核心…（第一次約 20 MB，之後會使用快取）";
  status.className = "small";
  try {
    await initOC();
    status.textContent = "建立 3D 模型中…";
    await new Promise((r) => setTimeout(r, 30));            // 讓畫面先更新
    const t0 = performance.now();
    const res = buildSpring(inp);
    const tol = Math.max(inp.d / 60, 0.01);
    const mesh = res.shape.mesh({ tolerance: tol, angularTolerance: 0.2 });
    if (!mesh.triangles.length) throw new Error("產生的實體沒有任何面，請檢查輸入參數。");
    showMesh(body, mesh);
    current = { ...res, tol };
    dlStep.disabled = false; dlStl.disabled = false; dlStep203.disabled = false;
    const q = meshStats(mesh);
    let vol = q.vol;                                          // 網格體積（粗網格偏小）；優先用 OCC 精確體積
    try { const v = measureVolume(res.shape); if (isFinite(v) && v > 0) vol = v; } catch (e) { /* 保留網格體積 */ }
    status.textContent = `${res.note}　外形 ${q.sx.toFixed(2)} × ${q.sy.toFixed(2)} × ${q.sz.toFixed(2)} mm　體積 ${vol.toFixed(1)} mm³（${((performance.now() - t0) / 1000).toFixed(1)} 秒）`;
  } catch (e) {
    console.error(e);
    disposeViewer(); body.textContent = "";
    status.textContent = "無法建立 3D 模型：" + (e && e.message ? e.message : String(e));
    status.className = "w";
  }
}

function fname(ext) {
  const d = new Date(), p = (n) => String(n).padStart(2, "0");
  const tag = (current?.name || "spring").toLowerCase().replace(/\s+/g, "-");
  return `${tag}-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}.${ext}`;
}
function save(blob, name) {
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob); a.download = name;
  document.body.appendChild(a); a.click();
  setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 500);
}
/* 自行呼叫 OpenCascade 寫 STEP：replicad 內建的 exportSTEP 寫死為 AP242，SolidWorks 2015 讀不了。
 * write.step.schema：1=AP214CD、2=AP214DIS、3=AP203、4=AP214IS、5=AP242DIS。預設用 AP214IS。 */
const STEP_SCHEMA = { AP214: 4, AP203: 3, AP242: 5 };
function stepBlob(shape, schema) {
  const oc = getOC();
  const writer = new oc.STEPControl_Writer();
  oc.Interface_Static.SetIVal("write.step.schema", STEP_SCHEMA[schema] ?? 4);
  oc.Interface_Static.SetCVal("write.step.unit", "MM");
  const progress = new oc.Message_ProgressRange();
  writer.Transfer(shape.wrapped, oc.STEPControl_StepModelType.STEPControl_AsIs, true, progress);
  const done = writer.Write("blob.step");
  writer.delete(); progress.delete();
  if (done !== oc.IFSelect_ReturnStatus.IFSelect_RetDone) throw new Error("寫入 STEP 檔失敗");
  const file = oc.FS.readFile("/blob.step");
  oc.FS.unlink("/blob.step");
  return new Blob([file], { type: "application/STEP" });
}
export function downloadSTEP(schema = "AP214") {
  if (!current) return;
  save(stepBlob(current.shape, schema), fname("step"));
}
export function getStepBlob(schema = "AP214") { return current ? stepBlob(current.shape, schema) : null; }
export function downloadSTL() {
  if (!current) return;
  /* 二進位 STL；容差過細檔案會暴增（0.01 mm 約 100 MB），取弦高誤差約 d/60 */
  save(current.shape.blobSTL({ tolerance: current.tol, angularTolerance: 0.3, binary: true }), fname("stl"));
}
export function close() { disposeViewer(); current = null; }
