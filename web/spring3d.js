/* ===== 彈簧 3D 模型與 STEP 輸出（純前端；replicad / OpenCascade WASM + three.js）=====
 * 輸入 inp 為計算器內部使用的 SI 單位（mm），欄位同 inpFromTab() 的輸出。
 * 中心線幾何由 springpath.js 提供（與工程圖共用同一份）；這裡負責把它掃成實體、輸出 STEP/STL、顯示 3D。
 * 全部計算在使用者瀏覽器內完成，不連線任何外部服務。
 */
import * as THREE from "three";
import { OrbitControls } from "three/addons/controls/OrbitControls.js";
import {
  setOC, makeHelix, makeLine, makeTangentArc, makeThreePointArc,
  makeCylinder, assembleWire, genericSweep, measureVolume, getOC,
} from "./vendor/replicad/replicad.js";
import opencascade from "./vendor/opencascade/replicad_single.js";

const SP = window.SpringPath;                 // springpath.js 以一般 <script> 先載入
if (!SP) throw new Error("springpath.js 未載入");
const r3 = SP.r3;                             // 顯示用：最多 3 位小數

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
const mul = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const unit = (a) => { const n = Math.hypot(a[0], a[1], a[2]); return [a[0] / n, a[1] / n, a[2] / n]; };
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];

/* 沿 spine 掃出直徑 d 的圓線材。
 * 輪廓用「兩個半圓弧」而不是一個整圓：整圓會產生週期曲面，STEP 內變成首尾不夾緊的 B-spline，
 * 舊版 CAD 讀進來會破圖；兩個半圓弧則各是標準的非週期曲面。 */
function sweepWire(spine, d) {
  const s = P(spine.startPoint), t = unit(P(spine.tangentAt(0)));
  const ref = Math.abs(t[2]) < 0.9 ? [0, 0, 1] : [1, 0, 0];
  const u = unit(cross(t, ref)), v = cross(t, u), r = d / 2;
  const at = (a, b) => add(s, add(mul(u, r * a), mul(v, r * b)));
  const profile = assembleWire([
    makeThreePointArc(at(1, 0), at(0, 1), at(-1, 0)),
    makeThreePointArc(at(-1, 0), at(0, -1), at(1, 0)),
  ]);
  return genericSweep(profile, spine, { transitionMode: "transformed" });
}

/* 中心線描述子 → OpenCascade 邊 */
function edgeOf(d) {
  if (d.k === "helix") {
    let w = makeHelix(d.pitch, d.pitch * d.turns, d.R);
    if (Math.abs(d.ang0 % 360) > 1e-9) w = w.rotate(d.ang0 % 360, [0, 0, 0], [0, 0, 1]);
    return w.translate(0, 0, d.z0);
  }
  if (d.k === "line") return makeLine(d.a, d.b);
  if (d.k === "tarc") return makeTangentArc(d.s, d.t, d.e);
  return makeThreePointArc(d.p0, d.pm, d.p1);
}

const NAMES = ["Compression spring", "Torsion spring", "Extension spring"];

/* 可選的顯示狀態：自由狀態 + 每個工作長度 L（壓縮／拉伸）或每個扭轉角 θ（扭力） */
export function stateList(inp) {
  const fa = typeof inp.fa === "number" && isFinite(inp.fa) ? inp.fa : 180;
  if (inp.type === 1) {
    return [{ label: `自由狀態（α0 = ${r3(fa)}°）`, tag: "free" }].concat(
      (inp.ang || []).map((a, i) => ({ label: `θ${i + 1} = ${r3(a)}°（兩腳夾角 ${r3(fa - a)}°）`, th: a, tag: `th${r3(a)}` })));
  }
  const free = { label: `自由長 L0 = ${r3(inp.l0)} mm`, tag: "free" };
  return [free].concat((inp.lw || []).map((x, i) => ({ label: `L${i + 1} = ${r3(x)} mm`, lw: x, tag: `L${r3(x)}` })));
}

export function buildSpring(inp, st) {
  const path = SP.springPath(inp, st);
  const m = path.meta;
  let solid = sweepWire(assembleWire(path.pieces.map(edgeOf)), m.d);
  if (path.clipZ) {                                   // 壓縮彈簧磨平端：在 z0、z1 兩處平切
    const [z0, z1] = path.clipZ;
    solid = solid.intersect(makeCylinder(m.od, z1 - z0, [0, 0, z0], [0, 0, 1]));
  }
  if (path.post.rot) solid = solid.rotate(path.post.rot, [0, 0, 0], [0, 0, 1]);
  if (path.post.mirror) solid = solid.mirror("YZ");   // 左旋 = 右旋的鏡像
  return { shape: solid, name: NAMES[inp.type || 0], note: m.note, meta: m };
}
/* ---------- 檢視器（three.js） ---------- */
let viewer = null;
/* 操作：左鍵旋轉、右鍵（或中鍵）平移、滾輪縮放；觸控：單指旋轉、雙指平移＋縮放。不需切換模式。 */
function applyMode(controls) {
  controls.mouseButtons = { LEFT: THREE.MOUSE.ROTATE, MIDDLE: THREE.MOUSE.PAN, RIGHT: THREE.MOUSE.PAN };
  controls.touches = { ONE: THREE.TOUCH.ROTATE, TWO: THREE.TOUCH.DOLLY_PAN };
}/* 回到預設視角（置中、斜上方） */
export function resetView() {
  if (!viewer) return;
  viewer.camera.position.copy(viewer.home.pos);
  viewer.controls.target.copy(viewer.home.target);
  viewer.camera.up.set(0, 0, 1);
  viewer.controls.update();
}
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
  controls.screenSpacePanning = true;
  applyMode(controls);
  const resize = () => {
    const w = container.clientWidth || 600, h = container.clientHeight || 400;
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  };
  const ro = new ResizeObserver(resize); ro.observe(container); resize();
  viewer = { renderer, scene, camera, controls, ro, raf: 0, home: { pos: camera.position.clone(), target: c.clone() } };
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

let curInp = null, states = [];
/* 開啟檢視：列出「自由狀態」與每個工作長度／扭轉角，預設顯示自由狀態；切換選單即重建模型 */
export async function open(inp) {
  const sel = $("#s3dstate");
  curInp = inp; states = stateList(inp);
  sel.innerHTML = states.map((s, i) => `<option value="${i}">${s.label}</option>`).join("");
  sel.hidden = states.length < 2;
  sel.onchange = () => showState(+sel.value);
  await showState(0);
}

async function showState(idx) {
  const inp = curInp, st = states[idx] || states[0];
  const status = $("#s3dstat"), body = $("#s3dbody"), dlStep = $("#s3dstep"), dlStl = $("#s3dstl"), dlStep203 = $("#s3dstep203");
  current = null; dlStep.disabled = true; dlStl.disabled = true; dlStep203.disabled = true;
  status.textContent = "載入 3D 核心…（第一次約 20 MB，之後會使用快取）";
  status.className = "small";
  try {
    await initOC();
    status.textContent = "建立 3D 模型中…";
    await new Promise((r) => setTimeout(r, 30));            // 讓畫面先更新
    const t0 = performance.now();
    const res = buildSpring(inp, st);
    const tol = Math.max(inp.d / 60, 0.01);
    const mesh = res.shape.mesh({ tolerance: tol, angularTolerance: 0.2 });
    if (!mesh.triangles.length) throw new Error("產生的實體沒有任何面，請檢查輸入參數。");
    showMesh(body, mesh);
    current = { ...res, tol, tag: st.tag };
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
  const tag = (current?.name || "spring").toLowerCase().replace(/\s+/g, "-") + (current?.tag && current.tag !== "free" ? "-" + current.tag : "");
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

export function getViewer() { return viewer; }
