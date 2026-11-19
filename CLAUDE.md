# 專案規則
- 本專案有兩個版本：Android APP（`app/`）與單檔網頁版（`web/index.html`）。
- **任何功能、文字、公式、圖示的更新，APP 與網頁版必須同步修改**，並同一次提交推送。
- 推送後 CI 會自動編譯 APP 發布開發版；網頁版由 `pages.yml` 部署到 GitHub Pages。
- 正式版需手動觸發 `Android CI` workflow 並填版本號（x.y.z）。
- 操作說明書（網頁版 `web/index.html` 內的 `MAN` 資料、APP 的 `ui/Manual.kt`）內容相同；新增或修改功能時，說明書也要同步更新，兩邊文字需一致。
