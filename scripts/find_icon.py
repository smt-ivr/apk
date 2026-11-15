#!/usr/bin/env python3
"""Find the best icon for a website and save it as a square PNG.

Usage: find_icon.py URL OUT.png
Looks at the page's <link rel="icon">, apple-touch-icon, the web app manifest,
then common paths and Google's favicon service, and keeps the largest image.
Exits 1 if nothing usable (>= 48px) was found.
"""
import html.parser
import json
import os
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
MIN_SIZE = 48


def fetch(url, limit=3_000_000):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    with urllib.request.urlopen(req, timeout=15) as r:
        return r.geturl(), r.read(limit)


class Links(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.icons = []  # (href, declared size)
        self.manifest = None

    def handle_starttag(self, tag, attrs):
        if tag != "link":
            return
        a = {k.lower(): (v or "") for k, v in attrs}
        rel = a.get("rel", "").lower().split()
        href = a.get("href", "").strip()
        if not href:
            return
        if "manifest" in rel:
            self.manifest = href
        elif "icon" in rel or "apple-touch-icon" in rel or "apple-touch-icon-precomposed" in rel:
            size = 0
            for s in a.get("sizes", "").lower().split():
                if "x" in s:
                    try:
                        size = max(size, int(s.split("x")[0]))
                    except ValueError:
                        pass
            if "apple-touch-icon" in rel and not size:
                size = 180
            self.icons.append((href, size))


def image_size(path):
    """Largest frame width x height, using ImageMagick (handles ICO and SVG)."""
    try:
        out = subprocess.run(["identify", "-format", "%w %h\n", path], capture_output=True, text=True, timeout=20).stdout
        best = (0, 0)
        for line in out.split("\n"):
            parts = line.split()
            if len(parts) == 2:
                w, h = int(parts[0]), int(parts[1])
                if w * h > best[0] * best[1]:
                    best = (w, h)
        return best
    except Exception:
        return (0, 0)


def main():
    url, out = sys.argv[1], sys.argv[2]
    candidates = []  # absolute urls, best guesses first
    try:
        final, body = fetch(url)
        p = Links()
        p.feed(body.decode("utf-8", "replace"))
        for href, size in sorted(p.icons, key=lambda x: -x[1]):
            candidates.append(urllib.parse.urljoin(final, href))
        if p.manifest:
            murl = urllib.parse.urljoin(final, p.manifest)
            try:
                _, mbody = fetch(murl)
                icons = json.loads(mbody.decode("utf-8", "replace")).get("icons", [])
                def msize(i):
                    try:
                        return max(int(s.split("x")[0]) for s in str(i.get("sizes", "0x0")).split() if "x" in s)
                    except ValueError:
                        return 0
                for i in sorted(icons, key=lambda i: -msize(i)):
                    if i.get("src"):
                        candidates.insert(0, urllib.parse.urljoin(murl, i["src"]))
            except Exception:
                pass
    except Exception as e:
        print("page fetch failed:", e, file=sys.stderr)
        final = url
    origin = "{0.scheme}://{0.netloc}".format(urllib.parse.urlparse(final))
    host = urllib.parse.urlparse(final).netloc
    candidates += [origin + "/apple-touch-icon.png", origin + "/favicon.ico",
                   "https://www.google.com/s2/favicons?sz=256&domain=" + host]

    best, best_size = None, 0
    tmp = tempfile.mkdtemp()
    seen = set()
    for i, c in enumerate(candidates):
        if c in seen or c.startswith("data:"):
            continue
        seen.add(c)
        try:
            _, data = fetch(c)
        except Exception:
            continue
        path = os.path.join(tmp, "c%d" % i)
        with open(path, "wb") as f:
            f.write(data)
        w, h = image_size(path)
        size = min(w, h)
        print("candidate %s -> %dx%d" % (c, w, h), file=sys.stderr)
        if size > best_size and w and h and max(w, h) <= 3 * min(w, h):
            best, best_size = path, size
        if best_size >= 512:
            break
    if not best or best_size < MIN_SIZE:
        print("no usable icon", file=sys.stderr)
        sys.exit(1)
    # Largest frame, square, PNG (SVGs rendered large).
    frame = best + "[0]"
    if image_size(best)[0] and subprocess.run(["identify", best], capture_output=True).stdout.count(b"\n") > 1:
        # multi-frame ICO: pick the largest frame
        frames = subprocess.run(["identify", "-format", "%p %w\n", best], capture_output=True, text=True).stdout.split("\n")
        frames = [f.split() for f in frames if f.strip()]
        if frames:
            frame = best + "[%s]" % max(frames, key=lambda f: int(f[1]))[0]
    trimmed = os.path.join(tmp, "trimmed.png")
    subprocess.run(["convert", "-density", "384", "-background", "none", frame, "-trim", "+repage",
                    "PNG32:" + trimmed], check=True)
    w, h = image_size(trimmed)
    side = max(w, h, 1)
    subprocess.run(["convert", trimmed, "-background", "none", "-gravity", "center",
                    "-extent", "%dx%d" % (side, side), "-resize", "512x512", "PNG32:" + out], check=True)
    print("icon: %s (%dpx)" % (candidates[int(os.path.basename(best)[1:])], best_size))


if __name__ == "__main__":
    main()
