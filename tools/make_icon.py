# -*- coding: utf-8 -*-
"""声迹 App 图标: 从原始方图抠出字形 + 生成自适应图标资源 (+ 可选部署到工程)。

原始图需自备 (仓库不含), 默认放在 assets/icon/source_icon.png, 也可用 --src 指定。
生成结果写入 assets/icon/<密度>/, 并生成预览图。

    python tools/make_icon.py --src path/to/source.png
    python tools/make_icon.py --src path/to/source.png --deploy   # 同时拷进工程 res/
"""
import argparse
import os
import shutil

import cv2
import numpy as np
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "assets", "icon")
PROJ_RES = r"D:\AndroidProjects\SherpaAsrCn\app\src\main\res"

# 源图为 1254x1254 蓝色渐变圆角卡片, 卡片内边距 112px (按需调整)
CARD = (112, 112, 1144, 1142)
RF = 0.2132  # 卡片圆角半径 / 边长
DPI = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def rmask(w, h, rf=RF, ss=4, inset=1.0):
    m = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(m).rounded_rectangle(
        [inset * ss, inset * ss, w * ss - 1 - inset * ss, h * ss - 1 - inset * ss],
        radius=int(min(w, h) * rf * ss), fill=255)
    return np.asarray(m.resize((w, h), Image.LANCZOS)).astype(np.float32) / 255.0


def build(src, out=OUT):
    os.makedirs(out, exist_ok=True)
    A = np.asarray(Image.open(src).convert("RGB")).astype(np.float32)
    card = A[CARD[1]:CARD[3], CARD[0]:CARD[2]].copy()
    ch, cw, _ = card.shape

    rm = rmask(cw, ch)
    dist_in = cv2.distanceTransform((rm > 0.5).astype(np.uint8), cv2.DIST_L2, 5)
    wh = card.min(axis=2)

    # 背景: 2 次多项式拟合 (只在卡片内部、非字形区采样)
    u = np.linspace(0, 1, cw)[None, :].repeat(ch, 0)
    v = np.linspace(0, 1, ch)[:, None].repeat(cw, 1)
    terms = [np.ones_like(u), u, v, u * u, u * v, v * v]
    sel = (wh < 105) & (dist_in > 26)
    M = np.stack([t[sel] for t in terms], axis=1)
    bg = np.zeros_like(card)
    for c in range(3):
        sol, *_ = np.linalg.lstsq(M, card[..., c][sel], rcond=None)
        bg[..., c] = sum(s * t for s, t in zip(sol, terms))
    bg = np.clip(bg, 0, 255)

    # 字形 alpha
    bwh = bg.min(axis=2)
    alpha = np.clip((wh - bwh) / np.maximum(250.0 - bwh, 1.0), 0, 1)
    alpha *= np.clip((dist_in - 22.0) / 10.0, 0, 1)   # 掐掉卡片圆角白边
    alpha[alpha < 0.05] = 0.0

    Aa = alpha[..., None]
    glyph = np.where(Aa > 0.05, (card - bg * (1 - Aa)) / np.maximum(Aa, 1e-3), 0.0)
    glyph = np.clip(glyph, 0, 255)
    core = alpha > 0.92
    mrgb = glyph[core].mean(axis=0)
    wmix = np.clip(alpha * 1.8 - 0.8, 0, 1)[..., None]
    glyph = np.clip(glyph * wmix + mrgb[None, None, :] * (1 - wmix), 0, 255)

    ys, xs = np.where(alpha > 0.5)
    gx0, gx1, gy0, gy1 = int(xs.min()), int(xs.max()), int(ys.min()), int(ys.max())
    gw, gh = gx1 - gx0 + 1, gy1 - gy0 + 1
    print("glyph core rgb", mrgb.round(1), "bbox", (gx0, gy0, gx1, gy1), "size", (gw, gh))

    fg_src = Image.fromarray(np.dstack([glyph, alpha * 255]).astype(np.uint8), "RGBA")
    fg_src.save(os.path.join(out, "fg_src.png"))
    glyph_crop = fg_src.crop((gx0, gy0, gx1 + 1, gy1 + 1))

    comp = np.clip(bg * (1 - Aa) + glyph * Aa, 0, 255).astype(np.uint8)
    card_clean = Image.fromarray(comp, "RGB")
    card_clean.save(os.path.join(out, "icon_card_clean.png"))

    def make_fg(size):
        safe = size * 66.0 / 108.0
        s = safe / np.hypot(gw, gh)
        nw, nh = max(1, int(round(gw * s))), max(1, int(round(gh * s)))
        sub = glyph_crop.resize((nw, nh), Image.LANCZOS)
        o = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        o.paste(sub, ((size - nw) // 2, (size - nh) // 2), sub)
        return o

    def make_mono(size):
        f = make_fg(size)
        a = np.asarray(f)[..., 3]
        rgb = np.zeros((size, size, 3), np.uint8)
        return Image.fromarray(np.dstack([rgb, a]), "RGBA")

    def make_legacy(size):
        img = card_clean.resize((size, size), Image.LANCZOS).convert("RGBA")
        m = Image.new("L", (size * 4, size * 4), 0)
        ImageDraw.Draw(m).rounded_rectangle(
            [0, 0, size * 4 - 1, size * 4 - 1], radius=int(size * 4 * RF), fill=255)
        img.putalpha(m.resize((size, size), Image.LANCZOS))
        return img

    def make_bg(size):
        return Image.fromarray(bg.astype(np.uint8), "RGB").resize((size, size), Image.LANCZOS)

    for name, k in DPI.items():
        d = os.path.join(out, name)
        os.makedirs(d, exist_ok=True)
        make_fg(int(108 * k)).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
        make_mono(int(108 * k)).save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)
        make_bg(int(108 * k)).save(os.path.join(d, "ic_launcher_background.png"), optimize=True)
        make_legacy(int(48 * k)).save(os.path.join(d, "ic_launcher.png"), optimize=True)
        make_legacy(int(48 * k)).save(os.path.join(d, "ic_launcher_round.png"), optimize=True)

    # 预览
    def show(shape, size=176):
        base = make_bg(432).convert("RGBA")
        base.alpha_composite(make_fg(432))
        big = base.resize((size * 4, size * 4), Image.LANCZOS)
        m = Image.new("L", (size * 4, size * 4), 0)
        d = ImageDraw.Draw(m)
        if shape == "circle":
            d.ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
        else:
            d.rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1],
                                radius=int(size * 4 * 0.42), fill=255)
        o = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
        o.paste(big, (0, 0), m)
        return o.resize((size, size), Image.LANCZOS)

    sheet = Image.new("RGB", (176 * 3 + 60, 176 + 20), (245, 246, 248))
    for i, s in enumerate(["circle", "squircle"]):
        im = show(s)
        sheet.paste(im, (12 + i * (176 + 12), 10), im)
    lg = make_legacy(176)
    sheet.paste(lg, (12 + 2 * (176 + 12), 10), lg)
    sheet.save(os.path.join(out, "preview_sheet.png"))
    print("OK ->", out)


def deploy(out=OUT):
    if not os.path.isdir(PROJ_RES):
        print("!! project res not found:", PROJ_RES)
        return
    for dpi in DPI:
        dst = os.path.join(PROJ_RES, "mipmap-" + dpi)
        os.makedirs(dst, exist_ok=True)
        for fn in ("ic_launcher.png", "ic_launcher_round.png",
                   "ic_launcher_foreground.png", "ic_launcher_monochrome.png",
                   "ic_launcher_background.png"):
            shutil.copy2(os.path.join(out, dpi, fn), os.path.join(dst, fn))
        print("deployed", dpi)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", default=os.path.join(OUT, "source_icon.png"),
                    help="原始方图 (默认 assets/icon/source_icon.png)")
    ap.add_argument("--deploy", action="store_true", help="生成后拷进工程 res/mipmap-*/")
    args = ap.parse_args()
    if not os.path.isfile(args.src):
        raise SystemExit("source image not found: %s\n把原始方图放到该路径, 或用 --src 指定。" % args.src)
    build(args.src)
    if args.deploy:
        deploy()


if __name__ == "__main__":
    main()