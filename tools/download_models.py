# -*- coding: utf-8 -*-
"""声迹 (Shengji ASR) - 大文件下载脚本

本仓库源码不含模型与原生库 (GB 级, 见 .gitignore)。克隆后运行本脚本,
即可把构建所需的全部大文件下载到正确位置, 然后直接 ./gradlew 构建。

用法 (在工程根目录执行):
    python tools/download_models.py            # 下载全部 (标准档 + 高精度档)
    python tools/download_models.py --std      # 只下标准档 (标准版够用)
    python tools/download_models.py --check    # 只校验已存在的文件

默认走 hf-mirror.com 镜像 (国内快), 失败自动回退 huggingface.co。
"""
import argparse
import hashlib
import os
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

HF_MIRRORS = [
    "https://hf-mirror.com/{repo}/resolve/main/{path}",
    "https://huggingface.co/{repo}/resolve/main/{path}",
]
GH_RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/{tag}/{name}"

# AAR 是上游 release 产物, 同一版本号内可能被上游重新打包 (体积会微变, 内容等价,
# 例如 v1.13.8 的 aar 已从 49,738,711 变为 50,129,134 字节), 因此只做"下限"校验,
# 只用于挡住下载中断的半截文件; 其余模型文件内容固定, 仍做精确字节校验。
LOOSE_MIN = {"app/libs/sherpa-onnx-1.13.8.aar": 40 * 1024 * 1024}


def size_ok(rel, have, size):
    if have < 0:
        return False
    if rel in LOOSE_MIN:
        return have >= LOOSE_MIN[rel]
    return have == size

JOBS = [
    # size 为上游当前参考值; 实际按 LOOSE_MIN 下限校验
    ("sherpa-onnx AAR", "app/libs/sherpa-onnx-1.13.8.aar", 50129134,
     [GH_RELEASE.format(tag="v1.13.8", name="sherpa-onnx-1.13.8.aar")]),

    ("Silero VAD", "app/src/main/assets/silero_vad.onnx", 643854,
     [GH_RELEASE.format(tag="asr-models", name="silero_vad.onnx")]),

    ("SenseVoice tokens",
     "app/src/main/assets/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/tokens.txt", 315894,
     [m.format(repo="csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17", path="tokens.txt") for m in HF_MIRRORS]),

    ("SenseVoice model",
     "app/src/main/assets/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17/model.int8.onnx", 239233841,
     [m.format(repo="csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17", path="model.int8.onnx") for m in HF_MIRRORS]),
]

NANO_REPO = "csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30"
NANO_BASE = "app/src/full/assets/sherpa-onnx-funasr-nano-int8-2025-12-30"
NANO_FILES = [
    ("encoder_adaptor.int8.onnx", 237792748),
    ("llm.int8.onnx", 600356593),
    ("embedding.int8.onnx", 155584380),
    ("Qwen3-0.6B/tokenizer.json", 11422654),
    ("Qwen3-0.6B/merges.txt", 1671853),
    ("Qwen3-0.6B/vocab.json", 2776833),
]
for _name, _size in NANO_FILES:
    JOBS.append((
        "FunASR-Nano " + _name,
        NANO_BASE + "/" + _name,
        _size,
        [m.format(repo=NANO_REPO, path=_name) for m in HF_MIRRORS],
    ))


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return "%.1f %s" % (n, unit)
        n /= 1024.0


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url, dst):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    tmp = dst + ".part"
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=60) as resp, open(tmp, "wb") as out:
        total = int(resp.headers.get("Content-Length") or 0)
        got = 0
        while True:
            chunk = resp.read(1 << 20)
            if not chunk:
                break
            out.write(chunk)
            got += len(chunk)
            if total:
                pct = got * 100 // total
                sys.stdout.write("\r    %-28s %3d%%  %s/%s"
                                 % (os.path.basename(dst), pct, human(got), human(total)))
                sys.stdout.flush()
    sys.stdout.write("\n")
    os.replace(tmp, dst)
    return got


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--std", action="store_true", help="只下载标准档所需文件 (不含高精度档)")
    ap.add_argument("--check", action="store_true", help="只校验, 不下载")
    args = ap.parse_args()

    jobs = JOBS if not args.std else [j for j in JOBS if not j[1].startswith("app/src/full/")]

    ok = missing = bad = 0
    for label, rel, size, urls in jobs:
        dst = os.path.join(ROOT, rel)
        have = os.path.getsize(dst) if os.path.isfile(dst) else -1
        if size_ok(rel, have, size):
            print("[OK]   %-40s %s" % (label, human(size)))
            ok += 1
            continue
        if args.check:
            print("[MISS] %-40s need %s, have %s"
                  % (label, human(size), human(have) if have >= 0 else "none"))
            missing += 1
            continue

        print("[GET]  %-40s %s" % (label, human(size)))
        last = None
        for url in urls:
            try:
                got = download(url, dst)
                if size_ok(rel, got, size):
                    break
                last = "size mismatch: got %d, want %d" % (got, size)
            except Exception as e:  # noqa: BLE001
                last = "%s: %s" % (type(e).__name__, e)
                print("       ! %s" % last)
        final = os.path.getsize(dst) if os.path.isfile(dst) else -1
        if size_ok(rel, final, size):
            print("       -> sha256 %s" % sha256(dst))
            ok += 1
        else:
            print("       !! FAILED (%s)" % last)
            bad += 1

    print("\n== done: %d ok, %d missing, %d failed ==" % (ok, missing, bad))
    if bad or (args.check and missing):
        sys.exit(1)


if __name__ == "__main__":
    main()