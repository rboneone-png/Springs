# 彈簧計算器 (Android)

Kotlin + Jetpack Compose + Material3。三個分頁：壓縮 / 扭力 / 拉伸，輸入即時計算。

- 公式：`app/src/main/java/com/springs/calc/SpringCalc.kt`（純 Kotlin，單位 mm/N/MPa）
- 測試：`app/src/test/.../SpringCalcTest.kt`
- 建置：Android Studio 開啟後 Run，或 `gradle :app:assembleDebug`

## 公式
- k = G·d⁴ / (8·D³·Na)；C = D/d；Wahl Kw = (4C−1)/(4C−4) + 0.615/C；τ = Kw·8FD/(πd³)
- 壓縮：F = k(L0−L)；密合高度依端部型式
- 拉伸：F = Fi + k(L−L0)
- 扭力：Na = N + (L1+L2)/(3πD)；k = E·d⁴/(3667·D·Na) [N·mm/°]；σ = Ki·32M/(πd³)
