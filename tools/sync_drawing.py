#!/usr/bin/env python3
"""把網頁版的中心線幾何（springpath.js）+ 計算核心 + 工程圖產生器抽出，複製到 APP 的 assets/drawing.js，讓 APP 與網頁版用同一份程式出圖。
用法：python3 tools/sync_drawing.py（CI 在編譯前會自動執行）"""
import pathlib, re, sys
root = pathlib.Path(__file__).resolve().parent.parent
h = (root / 'web/index.html').read_text(encoding='utf-8')
calc = re.search(r'<script id="calc">([\s\S]*?)</script>', h)
drw = re.search(r'<script id="drawing">([\s\S]*?)</script>', h)
if not calc or not drw:
    sys.exit('找不到 calc / drawing script 區塊')
out = root / 'app/src/main/assets/drawing.js'
out.parent.mkdir(parents=True, exist_ok=True)
path = (root / 'web/springpath.js').read_text(encoding='utf-8')  # 工程圖與 3D 共用的中心線幾何（window.SpringPath）
out.write_text(path + '\n' + calc.group(1) + '\n' + drw.group(1) + '\n', encoding='utf-8')
print('wrote', out)
