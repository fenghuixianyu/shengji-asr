# -*- coding: utf-8 -*-
"""声迹 - 打包交付 APK

构建完成后运行本脚本, 把已构建的 flavor 产物复制到 output/ 并打印体积 + SHA256。
标准版 std 是主交付物(必需); 双精度档 full 可选, 未构建时自动跳过。
"""
import argparse
import os
import shutil
import hashlib

# 工程根 = 本脚本所在目录的上一级；输出目录默认取工程同级的 output/（交付工作区），
# 可用 --out 覆盖。这样工程放在任意路径都能直接用，不写死盘符。
PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ap = argparse.ArgumentParser()
ap.add_argument("--out", default=os.path.abspath(os.path.join(PROJ, os.pardir, "output")),
                help="交付产物输出目录 (默认: 工程同级的 output/)")
args = ap.parse_args()
OUT = args.out

pairs = [
    (os.path.join(PROJ, r"app\build\outputs\apk\std\debug\app-std-debug.apk"),
     os.path.join(OUT, "声迹-标准版.apk"), True),
    (os.path.join(PROJ, r"app\build\outputs\apk\full\debug\app-full-debug.apk"),
     os.path.join(OUT, "声迹-双精度档.apk"), False),
]

os.makedirs(OUT, exist_ok=True)
for src, dst, required in pairs:
    if not os.path.isfile(src):
        print(("!! missing: " if required else "[skip] 未构建(可选): ") + src)
        continue
    shutil.copyfile(src, dst)
    h = hashlib.sha256()
    with open(dst, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    print("%-28s %7.1f MB  %s" % (os.path.basename(dst), os.path.getsize(dst) / 1024 / 1024, h.hexdigest().upper()))

print("--- output dir ---")
for n in sorted(os.listdir(OUT)):
    print("   ", n)