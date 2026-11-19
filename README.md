# 彈簧計算器 (Android)

Kotlin + Jetpack Compose + Material3。三個分頁：壓縮 / 扭力 / 拉伸，輸入即時計算。

- 公式：`app/src/main/java/com/springs/calc/SpringCalc.kt`（純 Kotlin，單位 mm/N/MPa）
- 測試：`app/src/test/.../SpringCalcTest.kt`
- 建置：Android Studio 開啟後 Run，或 `gradle :app:assembleDebug`

## 公式
- k = G·d⁴ / (8·D³·Na)；C = D/d；Wahl Kw = (4C−1)/(4C−4) + 0.615/C；τ = Kw·8FD/(πd³)
- 壓縮：F = k(L0−L)；密合高度依端部型式
- 壓縮疲勞：Ssu=0.67Sut（Sut=A/d^m）、Zimmerli Ssa/Ssm=241/379 MPa（珠擊 398/534）、Gerber 修正、S-N 對數內插 10³～10⁷；為估算值，非保證壽命
- 扭力疲勞：線材彎曲，Sr=0.45Sut（珠擊 0.67Sut）、Se=(Sr/2)/(1−(Sr/2Sut)²)、Gerber 修正、S-N 對數內插 10³～10⁶；估算值
- 拉伸：F = Fi + k(L−L0)
- 拉伸鉤環：σ = F[Ka·16D/(πd³)+4/(πd²)]，τ = Kb·8FD/(πd³)
- 扭力：Na = N + (L1+L2)/(3πD)；k = E·d⁴/(3667·D·Na) [N·mm/°]；σ = Ki·32M/(πd³)

## 網頁版（HTML）
`web/index.html` 為單一檔案、無外部相依，雙擊即可在任何瀏覽器（含手機）離線使用；功能與 APP 相同
（三種彈簧、公英制切換、多筆工作長度/扭轉角、強度判定、疲勞壽命、參數位置圖、左右滑動切換）。
每次 CI 發布都會把它以 `SpringCalc-web-v<版本>.html` 附在 Release。

## 操作說明書
程式內建圖文對照的操作說明書：網頁版點右上「說明」，APP 點上方「說明」。內容（含畫面導覽線框圖與各彈簧參數位置圖）APP 與網頁版相同。
