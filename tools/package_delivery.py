# -*- coding: utf-8 -*-
"""声迹 - 打包交付 APK

构建完成后运行本脚本, 把已构建的 flavor 产物复制到 output/ 并打印体积 + SHA256。
标准版 std 是主交付物(必需); 双精度档 full 可选, 未构建时自动跳过。
"""
import os, shutil, hashlib

OUT = r"D:\Project\ASR音频转语音\output"
PROJ = r"D:\AndroidProjects\SherpaAsrCn"

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