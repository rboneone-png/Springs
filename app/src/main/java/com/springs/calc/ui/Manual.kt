package com.springs.calc.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** 操作說明書內容（與網頁版相同，改動時兩邊需同步） */
data class ManSection(
    val id: String, val title: String, val figs: List<String>, val intro: String, val stepsTitle: String,
    val steps: List<String>, val head: List<String>, val rows: List<List<String>>, val qa: List<List<String>>, val notes: List<String>,
)

val MANUAL = listOf(
    ManSection("start", "1. 開始之前：畫面導覽", listOf("wire"), "本程式提供壓縮、扭力、拉伸三種彈簧的計算，以及由需求反推規格的「設計」功能。輸入欄位填妥後會即時算出結果，不需要按計算鈕。每個計算頁下方有「產生工程圖」與「3D 模型／下載 STEP」按鈕，另可選擇表面處理與熱處理。下圖標出畫面各區域，圖中編號與下方說明一一對應。", "圖例（編號對應上圖）", listOf("分頁標題：顯示目前所在的計算頁。", "說明按鈕：隨時回到本說明書，關閉後會回到原本的畫面，已輸入的數值不會消失。", "公制／英制切換：切換後所有輸入值與結果自動換算。公制單位為 mm、N、MPa；英制為 in、lbf、psi。", "參數位置圖：標出每個參數在彈簧上的位置，點標題可收合；電腦寬螢幕時固定在頁面右側。", "下拉選單：材質、端部型式、旋向（扭力）、鉤型（拉伸）、表面處理、熱處理等選項。材質會影響剪切模數 G、彈性模數 E 與抗拉強度 Sut。", "數值輸入欄：輸入後即時計算；格式錯誤時欄位會出現紅框；選填欄位空白代表不限制或使用預設。", "新增／刪除：可同時計算多個工作長度（壓縮、拉伸）或多個扭轉角（扭力），每筆各有一張結果卡片。", "珠擊處理開關：啟用前會跳出確認，只有零件確實經過珠擊處理時才選用。", "計算結果卡片：包含應力、強度判定、安全係數與預估壽命；紅字或 ⚠ 為警告。", "底部分頁：切換壓縮／扭力／拉伸／設計；手機上也可以左右滑動切換。"), emptyList(), emptyList(), emptyList(), listOf("本程式為估算工具，結果不是保證值；重要用途請以實測為準。")),
    ManSection("comp", "2. 壓縮彈簧計算", listOf("comp"), "用途：已知彈簧尺寸，計算彈簧常數、各工作長度的負荷與應力、壓並高度，並估算疲勞壽命。", "操作步驟", listOf("選擇材質與端部型式（端部型式決定總圈數與壓並高度）。", "輸入線徑 d、外徑 OD、有效圈數 Na、自由長 L0（位置見上圖）。", "輸入工作長度 L。要比較多個長度時，按「新增工作長度」，每一筆各出一張結果卡片。", "選填：「壽命測試按壓回覆位置」（循環最大長度，空白＝自由長）與「珠擊處理」，用於疲勞壽命估算。", "查看「彈簧基本參數」卡片（k、壓並高度、壓並應力），再查看各工作長度的結果卡片。按「產生工程圖」或「3D 模型／下載 STEP」可輸出圖面與模型（見第 6、7 節）；表面處理與熱處理見第 9 節。"), listOf("符號", "圖中位置／意義", "單位", "備註"), listOf(listOf("d", "線徑：鋼線直徑", "mm", ""), listOf("OD", "外徑：彈簧最外側直徑", "mm", ""), listOf("D", "中徑 = OD − d（線材中心的直徑）", "mm", "自動計算"), listOf("C", "旋繞比 = D / d", "—", "建議 4～12"), listOf("Na", "有效圈數：參與變形的圈數，不含兩端閉合圈", "圈", ""), listOf("Nt", "總圈數：含端圈", "圈", "依端部型式自動計算"), listOf("L0", "自由長：未受力時的總長", "mm", ""), listOf("Ls", "壓並高度：彈簧完全壓死時的高度", "mm", "自動計算"), listOf("L", "工作長度：工作時被壓縮到的長度", "mm", "可新增多筆"), listOf("k", "彈簧常數：每壓縮 1 mm 所需的力", "N/mm", "k = G·d⁴ / (8·D³·Na)"), listOf("F", "工作負荷 = k × (L0 − L)", "N", ""), listOf("τ", "剪應力（含 Wahl 修正係數）", "MPa", ""), listOf("Sut", "抗拉強度，依材質與線徑估算", "MPa", "")), emptyList(), listOf("自由長 / 中徑 > 4 時會提示挫曲風險，建議加導桿或導套。", "工作長度小於壓並高度或大於自由長度時會出現警告。")),
    ManSection("tors", "3. 扭力彈簧計算", listOf("tors"), "用途：已知彈簧尺寸與臂長，計算各扭轉角下的扭矩、受力點負荷、彎曲應力與疲勞壽命。", "操作步驟", listOf("選擇材質。", "輸入線徑 d、外徑 OD、本體圈數 N、受力點半徑 R、兩支臂長 L1、L2（位置見上圖）、自由角度 α0（兩支腳在自由狀態的夾角）與旋向（右旋或左旋）。", "輸入扭轉角 θ（度）。要比較多個角度時，按「新增扭轉角」。", "選填：「壽命測試按壓回覆的位置角度」（預載，0＝完全放鬆）與「珠擊處理」。", "選填：表面處理與熱處理（見第 9 節）。", "查看基本參數與各扭轉角的結果卡片；按「產生工程圖」或「3D 模型／下載 STEP」可輸出圖面與模型（見第 6、7 節）。"), listOf("符號", "圖中位置／意義", "單位", "備註"), listOf(listOf("N", "本體圈數：彈簧本體的圈數", "圈", ""), listOf("α0", "自由角度：自由狀態下兩支腳的夾角（右旋時由腳2 逆時針量到腳1，預設 180°）；往纏繞方向扭緊時變小，工作時夾角 = α0 − θ", "度", "兩腳要與線圈相切時，α0 = 180° − 360° × N 的小數圈（週期 360°）；與輸入的 N 不一致時，3D 與工程圖改用最接近的 N'（最多差半圈），剛度與應力仍依輸入的 N 計算"), listOf("旋向", "右旋或左旋（由軸線方向看的纏繞方向）。左旋為右旋的鏡像，兩腳位置與扭緊方向跟著鏡射", "—", "3D、工程圖、STEP 都依旋向輸出"), listOf("L1、L2", "兩支臂長：由本體切點量到臂端", "mm", ""), listOf("R", "受力點半徑：軸心到受力點的距離", "mm", "F = M / R"), listOf("θ", "扭轉角：從自由位置轉動的角度", "度", "可新增多筆"), listOf("Na", "等效圈數 = N + (L1+L2)/(3πD)", "圈", "自動計算，含臂長影響"), listOf("k", "扭力常數：每轉 1° 的扭矩", "N·mm/°", "k = E·d⁴ / (3667·D·Na)"), listOf("M", "扭矩 = k × θ", "N·mm", ""), listOf("F", "受力點負荷 = M / R", "N", ""), listOf("σ", "彎曲應力（含曲率修正）", "MPa", ""), listOf("扭緊後外徑", "轉到工作角度後的彈簧外徑", "mm", "檢查與軸、孔的間隙")), emptyList(), listOf("扭轉角超過本體圈數對應的角度時會提示過大。")),
    ManSection("ext", "4. 拉伸彈簧計算", listOf("ext"), "用途：已知彈簧尺寸與鉤型，計算各工作長度的負荷、本體與鉤的應力，並估算疲勞壽命（取最先損壞處）。", "操作步驟", listOf("選擇材質。", "輸入線徑 d、外徑 OD、有效圈數 Na、自由長 L0（兩鉤內側距離）。", "初張力 Fi：預設依線徑與外徑自動計算（取教科書建議範圍中值）。你可以直接改成自己的值；按「自動」鈕即可隨時改回自動值，欄位下方會顯示目前模式與建議範圍。", "最大負荷：預設取「最大工作長度」對應的負荷，同樣可手動修改，按「自動」恢復；下方會顯示材質容許上限與對應的最大工作長度。", "選擇鉤型（長開口鉤或圓環鉤，只影響 3D 與工程圖），並輸入鉤的中心半徑 r1、鉤側彎半徑 r2（位置見上圖）。", "輸入工作長度 L（可新增多筆）。", "查看基本參數（含最大負荷下的本體與鉤應力、容許最大負荷）與各工作長度的結果。按「產生工程圖」或「3D 模型／下載 STEP」可輸出圖面與模型（見第 6、7 節）。"), listOf("符號", "圖中位置／意義", "單位", "備註"), listOf(listOf("L0", "自由長：兩鉤內側的距離（未受力）", "mm", ""), listOf("Lb", "本體長 = (Na + 1) × d", "mm", "自動計算"), listOf("Fi", "初張力：線圈開始拉開前需克服的力", "N", "預設自動，可手動改"), listOf("k", "彈簧常數", "N/mm", "同壓縮彈簧公式"), listOf("F", "工作負荷 = Fi + k × (L − L0)", "N", ""), listOf("鉤型", "長開口鉤（預設）或圓環鉤。只影響 3D 與工程圖，不影響計算。長開口鉤：上下兩鉤共面、互相平行；圓環鉤：圓環立在線圈端，環面通過軸線、圓心在軸線上，連接圓環的線橫越線圈端的正中央", "—", "為了讓上下兩鉤共面，3D 與圖面的有效圈數取最接近輸入 Na 的 Na'（差 ≤ 0.25 圈），計算仍依輸入的 Na"), listOf("r1", "鉤的中心半徑（長開口鉤：端部半圓的半徑；圓環鉤：圓環的半徑）", "mm", "鉤的彎曲應力用"), listOf("r2", "鉤側彎半徑：鉤與本體交接處的彎折半徑", "mm", "鉤的扭轉應力用"), listOf("最大負荷", "用於檢查最大受力下的應力", "N", "預設自動，可手動改"), listOf("容許最大負荷", "本體 τ≤0.45Sut、鉤環彎曲 σ≤0.65Sut、鉤側彎 τ≤0.45Sut，取最小者", "N", "僅供參考的上限")), emptyList(), listOf("預估壽命取本體、鉤環彎曲、鉤側彎扭轉三處中最短者，並標出是哪一處控制。", "自動初張力是教科書建議值，實際請依製造能力與圖面決定。")),
    ManSection("design", "5. 設計：由需求反推規格", listOf("dzc", "dze", "dzt"), "用途：已知可用空間、彈簧型式、操作位置與力量，由程式搜尋並建議彈簧尺寸規格。上圖依序為壓縮、拉伸、扭力的需求示意圖，右側（手機為上方）的圖會跟著你選的型式切換。", "操作步驟", listOf("選擇彈簧型式與材質。", "輸入可用空間（外徑上限、內徑下限／桿徑、自由長上限等）。", "輸入兩個操作點：安裝位置（L1、F1）與工作位置（L2、F2）；扭力則輸入 θ1、F1 與 θ2、F2。", "選填：目標壽命、力量容許誤差、珠擊處理。", "查看「設計結果摘要」：所需彈簧常數、評估組合數與符合條件的組合數。", "查看最多 6 個方案，依預估壽命由最佳排到最差（壽命相同時比疲勞安全係數）；每種線徑只列壽命最佳的一組。", "找不到完全符合的規格時，會列出最接近的方案並標出不符項目，可放寬空間、誤差或目標壽命後再試。", "選定方案後，請到對應的計算頁輸入該規格做完整驗算。方案卡片也可直接產生工程圖或 3D 模型／STEP（拉伸用長開口鉤；扭力的自由角度取你在設計頁填的 α0）。"), listOf("欄位", "說明"), listOf(listOf("外徑上限", "彈簧外徑不可超過的孔徑或空間"), listOf("內徑下限／桿徑", "彈簧內側導桿的直徑（壓縮）"), listOf("自由長上限", "彈簧未受力時允許的最大長度（選填）"), listOf("L1、F1", "安裝位置的長度與力（拉伸的 L1 為兩鉤內側距離）"), listOf("L2、F2", "壓縮為工作最短長度與力；拉伸為最大工作長度與力"), listOf("鉤長係數", "拉伸：每個鉤占的長度 = 係數 × 中徑；一般圓環鉤用 1，長開口鉤可用 1.5 左右，實際依圖面"), listOf("軸徑、軸向長度上限", "扭力：扭緊後彈簧內徑需大於軸徑；本體長度不可超過軸向空間"), listOf("R、L1、L2", "扭力：受力點半徑與兩支臂長"), listOf("θ1、θ2", "扭力：安裝角度與工作角度（度）"), listOf("自由角度 α0", "扭力：兩腳在自由狀態的夾角（度），只用在工程圖與 3D，預設 180°"), listOf("目標壽命", "空白＝不要求；填入後壽命低於目標的規格會被判為不符"), listOf("力量容許誤差", "實際力量與需求力量的允許差距，預設 5%")), emptyList(), listOf("設計假設：線徑取常用規格系列；中徑以 0.25 mm 掃描；圈數取 0.25 圈的倍數。", "壓縮：壓並力需 ≥ 1.15 倍工作力、旋繞比 4～12、剪應力 ≤ 0.65 Sut。", "拉伸：自由長 = (Na+1)·d + 2×鉤內長度；初張力由 F1 反推，需落在建議範圍；r1 = D/2、r2 = 2d。", "扭力：臂長計入等效圈數；扭緊後內徑需大於軸徑；彎曲應力 ≤ 0.78 Sut。")),
    ManSection("dwg", "6. 產生工程圖（出圖）", emptyList(), "用途：設計或計算完成後，一鍵產生 A4 橫向的工程圖面。圖面左側是規格（尺寸、性能、材料與處理，含表面處理與熱處理）與技術要求，右側是由 3D 模型的彈簧中心線直接投影出來的視圖（與 3D 模型是同一份幾何），視圖下方有說明區，最下方是標題欄（品名、圖號、材質、比例、單位、日期）。", "操作步驟", listOf("在壓縮、扭力或拉伸頁輸入完整規格（線徑、外徑、圈數、長度等），按頁面上的「產生工程圖」。", "設計頁：每個方案卡片下方都有「產生此方案的工程圖」，會用該方案的尺寸出圖。", "畫面會顯示圖面。圖上的長度、外徑、線徑等尺寸是實際數值，比例會自動選擇適當的標準比例（例如 2:1、1:1、1:2）。視圖依型式配置：壓縮＝前視圖；拉伸＝前視圖＋右視圖；扭力＝俯視圖＋前視圖（虛線為工作位置）。視圖、尺寸線與文字各占固定區塊，不會互相重疊，版面會自動選並排或上下堆疊。", "網頁版：按「列印／存成 PDF」用瀏覽器列印功能存成 PDF（紙張請選 A4 橫向、邊界選「無」）；也可下載 SVG 或 PNG。", "APP：按右上「列印／存成 PDF」，用系統列印功能選「儲存為 PDF」；圖面可用手指縮放檢視。", "切換公制／英制後再出圖，圖面與規格會以目前的單位顯示。"), listOf("圖面區域", "內容"), listOf(listOf("左側：規格", "尺寸（線徑、外徑、內徑、中徑、圈數、自由長…）、性能（彈簧常數、各工作點負荷與應力、強度判定、預估壽命）、材料與處理"), listOf("左下：技術要求", "公差說明、免責聲明、珠擊處理與材質"), listOf("右側：示意圖", "3D 中心線的正投影視圖，標註自由長（扭力為本體長）與外徑；線徑、圈數、臂長、自由角度 α0、旋向、鉤型、r1、r2 等寫在視圖下方的說明區"), listOf("右下：標題欄", "品名與主要尺寸、圖號（含日期時間）、材質、比例、單位、日期")), emptyList(), listOf("圖面尺寸是計算值，未含公差；不是正式製造圖，送廠前請補上公差、端部與鉤型的製造細節與製造要求。", "視圖是幾何的真實正投影（線材以線徑描粗，前後交疊處有遮擋）。為了讓扭簧兩腳與線圈相切、拉伸彈簧兩鉤共面，3D 與圖面的圈數可能微調為 N'／Na'（見第 7 節）。")),
    ManSection("3d", "7. 3D 模型與 STEP 輸出", emptyList(), "每個計算頁與設計頁方案卡片的「3D 模型／下載 STEP」按鈕，會用你輸入的規格建出彈簧的 3D 實體，可旋轉檢視並下載 STEP 或 STL。全部在你的瀏覽器內計算，不會把規格傳到任何伺服器。第一次使用要載入約 22 MB 的 3D 核心，之後會使用快取。", "操作步驟", listOf("輸入完整規格後按「3D 模型／下載 STEP」，畫面會顯示 3D 彈簧。", "視窗上方的下拉選單可切換「自由狀態」與你輸入的每個工作長度 L（壓縮、拉伸）或扭轉角 θ（扭力）。壓縮小於壓並高度、拉伸小於自由長度時無法畫出，會說明原因。", "操作：滑鼠左鍵拖曳旋轉、右鍵（或中鍵）拖曳平移、滾輪縮放；觸控裝置單指旋轉、雙指平移與縮放；「重設視角」回到預設視角。", "按「下載 STEP (AP214)」取得實體 STEP 檔。SolidWorks 2015 等舊版 CAD 請用 AP214，讀不好再試「STEP (AP203)」。「下載 STL」是 3D 列印用的網格；檔名會帶目前的狀態（例如 L40、th90）。"), listOf("項目", "說明"), listOf(listOf("壓縮", "四種端部型式；磨平端在 z=0 與 L0 處平切。"), listOf("扭力", "右旋或左旋，兩腳在水平面內；工作狀態的圈數增加 θ/360、線圈縮小，與計算頁的「扭緊後外徑」一致。"), listOf("扭力 N'", "兩腳要與線圈相切，N 的小數圈必須對應自由角度 α0。3D 與工程圖改用最接近輸入 N 的 N'（最多差半圈）；剛度與應力仍依輸入的 N 計算。"), listOf("拉伸", "長開口鉤（半圓切齊 r1）或圓環鉤（環立在線圈端，連接線橫越線圈中央）；上下兩鉤共面平行。"), listOf("拉伸 Na'", "為了讓上下兩鉤共面，3D 與工程圖的有效圈數取最接近輸入 Na 的 Na'（差 ≤ 0.25 圈）；計算仍依輸入的 Na。"), listOf("密繞間隙", "密繞的圈與圈之間保留 1% 線徑（d=2 約 0.02 mm）的間隙，避免相鄰圈相切造成舊版 CAD 破圖。"), listOf("檔案格式", "STEP 預設 AP214（另有 AP203 備用）；線材以半圓輪廓、螺旋每半圈一段輸出，是針對 SolidWorks 2015 的調整。")), emptyList(), listOf("3D 功能要透過網址（http／https）開啟網頁，不能直接雙擊 index.html（file://）開啟；計算與工程圖不受影響。", "3D 模型是依計算值建立的幾何，不含公差與各廠專屬的鉤形、端部細節，送廠前請覆核。", "APP 的「3D 模型／下載 STEP」按鈕會用手機瀏覽器開啟網頁版並帶入目前規格，需要連網；3D 檢視與下載在網頁中操作。")),
    ManSection("read", "8. 如何判讀結果", emptyList(), "結果卡片上的「強度判定」與「預估壽命」是判斷規格是否可用的主要依據。", "", emptyList(), listOf("顯示", "條件", "意義"), listOf(listOf("✔ 合格", "剪應力 / Sut ≤ 0.45（彎曲應力 ≤ 0.65）", "在建議範圍內"), listOf("⚠ 需預壓除應力", "剪應力 / Sut ≤ 0.65（彎曲應力 ≤ 0.78）", "需做預壓或除應力處理才適用"), listOf("✘ 超限", "高於上述範圍", "超過材質安全範圍，建議改規格"), listOf("預估壽命", "依 S-N 曲線估算（壓縮／拉伸本體以 10⁷ 次、扭力／鉤環彎曲以 10⁶ 次為無限壽命界線）", "顯示循環次數；「無限壽命」表示低於疲勞極限"), listOf("疲勞安全係數", "疲勞極限 / 實際應力幅（Gerber 修正）", "大於 1 較安全"), listOf("靜態安全係數", "降伏強度 / 最大應力", "大於 1 較安全"), listOf("珠擊處理", "提高疲勞極限的表面處理", "僅在零件確實處理過時選用")), emptyList(), listOf("壽命為教科書方法的估算值，實際壽命受表面品質、溫度、環境、端部與製造分散影響，不是保證值。", "磷青銅等非鋼線材質，疲勞係數僅供參考，卡片會標示。")),
    ManSection("treat", "9. 表面處理與熱處理", emptyList(), "三種彈簧的計算頁與設計頁都有「表面處理」與「熱處理」欄位。這兩項只用來出圖、檢核與提示，不會改變應力、安全係數與壽命的計算結果。", "操作步驟", listOf("在「表面處理」選擇電鍍、磷化、發黑、塗裝等；需要時在「表面規範／厚度」填規範（例如 ASTM B633 Fe/Zn 8，8~12 µm）。", "在「熱處理」選擇消除應力（低溫回火）、去氫烘烤（電鍍後）或其他熱處理，並填入溫度（°C）與時間（分鐘）。", "按「套用建議值」可依目前材質與熱處理型式帶入常用溫度與時間；欄位下方會顯示建議範圍。", "查看結果區的「表面處理與熱處理」卡片：會列出選擇內容與檢核提示。工程圖的規格欄會一併列出。"), listOf("項目", "說明"), listOf(listOf("上油與無", "「無（素材，不上油）」是不做任何處理；「上油（防鏽油）」是表面塗防鏽油，兩者分開選。"), listOf("淬火＋回火", "熱成形或大線徑彈簧用。溫度與時間填「回火」：高碳鋼約 370～480°C、合金鋼約 370～540°C；淬火（約 790～900°C 油淬）請記在表面規範欄。冷拉預硬線材一般不需要。"), listOf("時效硬化", "沉澱硬化材質（17-7PH 不鏽鋼、鈹銅等）用；程式內的材質清單沒有這類材質，建議值是推估，請以材料規範為準。"), listOf("氮化", "合金鋼線（如鉻矽鋼閥簧）的表面硬化；氮化溫度不可高於線材回火溫度。建議值是推估，請以製程規範為準。"), listOf("消除應力", "彈簧成形後的低溫回火，釋放繞製殘留應力。建議值依材質：琴鋼線約 230°C × 60 min、油回火鋼線約 290°C × 30 min、不鏽鋼 302/304 約 300°C × 30 min、磷青銅約 200°C × 60 min（不可高於約 230°C）。"), listOf("去氫烘烤", "高強度鋼線電鍍後，為降低氫脆風險所做的烘烤。建議約 190～220°C、至少數小時（預設 200°C × 240 min），確切時間依 ASTM B850 強度等級與客戶規範。"), listOf("氫脆提示", "選擇電鍍鋅、鋅鎳、鎳、鉻時，若是碳鋼或合金鋼線且未設定去氫烘烤，會出現警告；不鏽鋼與銅合金通常不需要。")), emptyList(), listOf("建議值是業界通用做法與線材商資料的整理，不是保證值；鉻矽鋼線的建議值為依同類鋼線推估。實際溫度、時間請依線材商、電鍍廠與客戶規範。", "電鍍會降低疲勞壽命，建議搭配珠擊；本程式的疲勞與壽命估算沒有計入表面處理的影響。")),
    ManSection("faq", "10. 常見問題", emptyList(), "", "", emptyList(), emptyList(), emptyList(), listOf(listOf("3D 的圈數和我輸入的不一樣？", "扭力彈簧要讓兩腳與線圈相切、拉伸彈簧要讓上下兩鉤共面，3D 與工程圖會把圈數微調為 N'／Na'（扭力最多半圈、拉伸最多 0.25 圈），3D 視窗下方的狀態列會標出。計算頁的剛度與應力仍依你輸入的圈數。"), listOf("為什麼不能直接雙擊開啟 3D？", "3D 核心要用瀏覽器的模組載入，必須透過網址（http／https）開啟。放到網站伺服器或用本機伺服器即可；計算與工程圖可以直接雙擊開啟。"), listOf("SolidWorks 2015 讀不到 STEP 怎麼辦？", "先用「下載 STEP (AP214)」；仍有問題再試「STEP (AP203)」。STEP 已針對舊版 CAD 調整（AP214 格式、半圓輪廓、螺旋分段、圈間留縫）。"), listOf("3D 模型可以看工作位置嗎？", "可以。按「3D 模型」後，視窗上方的下拉選單可切換「自由狀態」與你輸入的每個工作長度 L（壓縮、拉伸）或扭轉角 θ（扭力）。工作長度小於壓並高度、拉伸小於自由長度時無法畫出，會說明原因。下載的 STEP／STL 是目前顯示的那個狀態。3D 視窗操作：左鍵拖曳旋轉、右鍵（或中鍵）拖曳平移、滾輪縮放；觸控是單指旋轉、雙指平移與縮放；「重設視角」回到預設視角。"), listOf("一開始畫面就出現警告？", "預設值只是示範數字，可能超過建議範圍。把欄位改成你的規格後警告會跟著更新。"), listOf("自動初張力與結果裡的「初張力應力」為什麼不同？", "自動值依建議的初應力（未含修正係數）反推；結果卡片的應力有含 Wahl 修正，數值會略高。"), listOf("切換公制／英制後數字變了？", "數值是自動換算，實際大小不變；來回切換不會失真。"), listOf("設計頁找不到符合的規格？", "放寬外徑上限、力量容許誤差或目標壽命，或調整操作點的位置與力量後再試。"), listOf("關閉程式後輸入會保留嗎？", "不會。輸入只存在目前畫面，關閉或重新整理後需重填。"), listOf("手機怎麼切換分頁？", "點底部分頁，或在畫面上左右滑動。"), listOf("工程圖可以直接拿去製造嗎？", "不行。圖面是依你輸入的計算值自動產生，沒有公差與製造細節，只供設計溝通與初步參考；正式圖面請由設計人員補齊後出圖。")), emptyList()),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("操作說明書") },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } })
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MANUAL.forEach { ManualSectionCard(it) }
        }
    }
}

@Composable
private fun ManualFigure(key: String) = when (key) {
    "wire" -> WireframeFigure()
    "comp" -> CompressionDiagram(true)
    "tors" -> TorsionDiagram(true)
    "ext" -> ExtensionDiagram(true)
    "dzc" -> DesignDiagram(0, true)
    "dze" -> DesignDiagram(1, true)
    else -> DesignDiagram(2, true)
}

@Composable
private fun ManualSectionCard(s: ManSection) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.title, style = MaterialTheme.typography.titleMedium)
            if (s.intro.isNotEmpty()) Text(s.intro, style = MaterialTheme.typography.bodyMedium)
            s.figs.forEach { ManualFigure(it) }
            if (s.stepsTitle.isNotEmpty()) Text(s.stepsTitle, style = MaterialTheme.typography.titleSmall)
            s.steps.forEachIndexed { i, t ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    if (s.id == "start") {
                        Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                    } else Text("${i + 1}.", style = MaterialTheme.typography.bodyMedium)
                    Text(t, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            if (s.head.isNotEmpty()) ManualTable(s.head, s.rows)
            s.qa.forEach { Text("Q：${it[0]}\nA：${it[1]}", style = MaterialTheme.typography.bodyMedium) }
            s.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ManualTable(head: List<String>, rows: List<List<String>>) {
    val w = when (head.size) { 4 -> listOf(1f, 3f, 1f, 2f); 3 -> listOf(1.4f, 2.6f, 2f); else -> listOf(1.2f, 3f) }
    @Composable
    fun line(cells: List<String>, bold: Boolean) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cells.forEachIndexed { i, c ->
                Text(c, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold || i == 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(w[i.coerceAtMost(w.size - 1)]))
            }
        }
        HorizontalDivider()
    }
    Column { line(head, true); rows.forEach { line(it, false) } }
}
