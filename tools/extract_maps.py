#!/usr/bin/env python3
"""Extract Listening map/plan/diagram figures from the C19/C20/C21 source
PDFs and link them to question groups in the seed JSON files.

Pipeline (all discovery is by regex, never by hardcoded page numbers):
  1. `pdfimages -png` each source PDF into a work dir.
  2. Find candidate map pages: pages whose text contains
     "Label the map/plan/diagram". C20 and C21 have a text layer (read
     with pymupdf); C19 is a pure scan, so the OCR sidecars produced by
     tools/ocr_c19.py (/tmp/c19/pg/p-*.txt) are used when present.
  3. For each candidate page, parse the nearby headers (Test N, PART /
     Section N, Questions A-B) and resolve the page to its largest
     embedded raster image via `pdfimages -list` (tiny logo fragments
     under 400 px on the long side are ignored).
  4. Match the figure to a seed group by (testNo, listening section
     number, exact question-number range). Exactly one confident match
     is required; anything ambiguous is skipped and the group keeps no
     image field (never guess).
  5. Copy the matched PNG to
     app/src/main/assets/images/maps/<book>_t<test>s<sec>g<idx>.png,
     downscaling the longest side to 1600 px with PIL when available.
  6. Set "imageAsset": "images/maps/<file>" on the matched groups in
     app/src/main/assets/seed/c20.json, c21_full.json and c19.json.

Only files listed above are written. Nothing under ui/, net/, sec/,
data/, res/ or build files is touched.

Usage: python3 tools/extract_maps.py [--workdir /tmp/maps] [--no-extract]
"""

import glob
import json
import os
import re
import shutil
import subprocess
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "app", "src", "main", "assets")
MAPS_DIR = os.path.join(ASSETS, "images", "maps")

BOOKS = [
    {
        "book": "c20",
        "pdf": "/root/ielts/Cambrige 20.pdf",
        "seed": os.path.join(ASSETS, "seed", "c20.json"),
        "ocr_dir": None,  # has a real text layer
    },
    {
        "book": "c21",
        "pdf": "/root/ielts/Cambridge 21.pdf",
        "seed": os.path.join(ASSETS, "seed", "c21_full.json"),
        "ocr_dir": None,  # paper-capture OCR text layer
    },
    {
        "book": "c19",
        "pdf": "/root/ielts/Cambridge-IELTS-19-Academic 2.pdf",
        "seed": os.path.join(ASSETS, "seed", "c19.json"),
        "ocr_dir": "/tmp/c19/pg",  # pure scan; reuse ocr_c19.py sidecars
    },
]

LABEL_RE = re.compile(r"label\s+the\s+(map|plan|diagram)", re.I)
TEST_RE = re.compile(r"test\s*(\d)", re.I)
SEC_RE = re.compile(r"(?:part|section)\s*(\d)", re.I)
QRANGE_RE = re.compile(r"questions?\s*(\d+)\s*[-\u2013\u2014]\s*(\d+)", re.I)
MIN_SIDE = 400  # ignore logo fragments below this on the long side
MAX_SIDE = 1600  # downscale target for the longest side


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"{' '.join(cmd)} failed: {r.stderr.strip()}")
    return r.stdout


def page_texts_textlayer(pdf):
    """Return {pdf_page_no: text} using pymupdf (1-based pages)."""
    import fitz  # pymupdf

    doc = fitz.open(pdf)
    out = {}
    for i, page in enumerate(doc):
        out[i + 1] = page.get_text()
    doc.close()
    return out


def page_texts_ocr_sidecars(ocr_dir, n_pages):
    """Return {pdf_page_no: text} from ocr_c19.py sidecars (p-<NNN>.txt).

    pdftoppm names rendered pages p-<NNN>.png with the same numbering,
    so p-<NNN>.txt holds the OCR text of PDF page NNN.
    """
    out = {}
    files = sorted(glob.glob(os.path.join(ocr_dir, "p-*.txt")))
    if len(files) < 100:
        return out
    for f in files:
        m = re.search(r"p-(\d+)\.txt$", f)
        if not m:
            continue
        pno = int(m.group(1))
        if 1 <= pno <= n_pages:
            with open(f, encoding="utf-8", errors="replace") as fh:
                out[pno] = fh.read()
    return out


def find_map_pages(texts):
    """Pages whose text captions a map/plan/diagram labelling task."""
    return sorted(p for p, t in texts.items() if LABEL_RE.search(t or ""))


def parse_headers(texts, page):
    """(test, section, qfirst, qlast) for the map caption on `page`.

    The question range comes from the caption page itself (preferring
    the caption line); Test/section headers may sit on the preceding
    pages, so the closest header at or before `page` wins. Anything
    unclear returns None fields and the caller skips the page.
    """
    own = texts.get(page, "") or ""
    cap_lines = [l for l in own.splitlines() if LABEL_RE.search(l)]
    qrange = None
    for l in cap_lines:
        mq = QRANGE_RE.search(l)
        if mq:
            qrange = (int(mq.group(1)), int(mq.group(2)))
            break
    if qrange is None:
        mq = QRANGE_RE.search(own)
        if mq:
            qrange = (int(mq.group(1)), int(mq.group(2)))
    test = sec = None
    for p in range(page - 2, page + 1):
        t = texts.get(p, "") or ""
        for m in TEST_RE.finditer(t):
            test = int(m.group(1))
        for m in SEC_RE.finditer(t):
            sec = int(m.group(1))
    return test, sec, qrange


def list_images(pdf):
    """Parse `pdfimages -list` -> {(page, num): (width, height)}."""
    out = {}
    lines = run(["pdfimages", "-list", pdf]).splitlines()
    for line in lines[2:]:
        parts = line.split()
        if len(parts) < 4:
            continue
        try:
            page, num, w, h = int(parts[0]), int(parts[1]), int(parts[3]), int(parts[4])
        except ValueError:
            continue
        out[(page, num)] = (w, h)
    return out


def resolve_image(workdir, prefix, page, images):
    """Largest embedded image on `page` (skip tiny fragments)."""
    cands = [(num, wh) for (pg, num), wh in images.items()
             if pg == page and max(wh) >= MIN_SIDE]
    if not cands:
        return None
    cands.sort(key=lambda kv: kv[1][0] * kv[1][1], reverse=True)
    num = cands[0][0]
    for ext in ("png", "ppm", "pbm", "jpg"):
        p = os.path.join(workdir, f"{prefix}-{num:03d}.{ext}")
        if os.path.exists(p):
            return p
    # Fall back to any file with that numeric suffix.
    hits = glob.glob(os.path.join(workdir, f"{prefix}-{num:03d}.*"))
    return hits[0] if hits else None


def seed_section_no(section):
    m = re.search(r"(\d+)", section or "")
    return int(m.group(1)) if m else None


def match_group(seed_data, test, sec, qrange):
    """Single confident seed group for (test, section, q-range) or None."""
    hits = []
    for t in seed_data.get("tests", []):
        if t.get("testNo") != test:
            continue
        for gi, g in enumerate(t.get("groups", [])):
            if "Listening" not in (g.get("section") or ""):
                continue
            if seed_section_no(g.get("section")) != sec:
                continue
            qnos = [q.get("qNo") for q in g.get("questions", [])]
            if not qnos:
                continue
            if (min(qnos), max(qnos)) != (qrange[0], qrange[1]):
                continue
            blob = (g.get("instructions") or "") + " " + " ".join(
                q.get("prompt", "") for q in g.get("questions", []))
            if not re.search(r"\b(label|map|plan|diagram)\b", blob, re.I):
                continue
            hits.append((t, gi, g))
    return hits[0] if len(hits) == 1 else None


def place_image(src, dest_name):
    dest = os.path.join(MAPS_DIR, dest_name)
    try:
        from PIL import Image
        im = Image.open(src)
        im.load()
        w, h = im.size
        long_side = max(w, h)
        if long_side > MAX_SIDE:
            scale = MAX_SIDE / long_side
            im = im.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
        if im.mode in ("RGBA", "LA", "PA"):
            bg = Image.new("RGB", im.size, (255, 255, 255))
            bg.paste(im, mask=im.split()[-1])
            im = bg
        elif im.mode != "RGB":
            im = im.convert("RGB")
        im.save(dest, "PNG")
    except ImportError:
        shutil.copyfile(src, dest)
    # Readability check: the file must decode as an image.
    try:
        from PIL import Image
        with Image.open(dest) as chk:
            chk.verify()
    except ImportError:
        assert os.path.getsize(dest) > 0
    return dest


def main():
    workdir = sys.argv[sys.argv.index("--workdir") + 1] \
        if "--workdir" in sys.argv else "/tmp/maps"
    do_extract = "--no-extract" not in sys.argv
    os.makedirs(workdir, exist_ok=True)
    os.makedirs(MAPS_DIR, exist_ok=True)

    try:
        import fitz  # noqa: F401
        have_fitz = True
    except ImportError:
        have_fitz = False

    report = []
    for cfg in BOOKS:
        book, pdf, seed_path = cfg["book"], cfg["pdf"], cfg["seed"]
        if do_extract:
            run(["pdfimages", "-png", pdf,
                 os.path.join(workdir, book)])
        if cfg["ocr_dir"]:
            import fitz as _f
            n_pages = len(_f.open(pdf))
            texts = page_texts_ocr_sidecars(cfg["ocr_dir"], n_pages)
            if not texts:
                print(f"{book}: no OCR sidecars in {cfg['ocr_dir']}, skip")
                continue
        else:
            if not have_fitz:
                print(f"{book}: pymupdf missing, skip")
                continue
            texts = page_texts_textlayer(pdf)

        images = list_images(pdf)
        matched = 0
        matched_qs = 0
        with open(seed_path, encoding="utf-8") as fh:
            seed = json.load(fh)
        for page in find_map_pages(texts):
            test, sec, qrange = parse_headers(texts, page)
            if not (test and sec and qrange):
                print(f"{book} p{page}: headers unclear "
                      f"(test={test} sec={sec} range={qrange}), skip")
                continue
            hit = match_group(seed, test, sec, qrange)
            if hit is None:
                print(f"{book} p{page}: no single confident seed group "
                      f"for T{test} S{sec} Q{qrange[0]}-{qrange[1]}, skip")
                continue
            t, gi, g = hit
            src = resolve_image(workdir, book, page, images)
            if src is None:
                print(f"{book} p{page}: no embedded image, skip")
                continue
            dest_name = f"{book}_t{test}s{sec}g{gi}.png"
            dest = place_image(src, dest_name)
            g["imageAsset"] = f"images/maps/{dest_name}"
            nq = len(g.get("questions", []))
            matched += 1
            matched_qs += nq
            print(f"{book} p{page} -> {dest} "
                  f"(T{test} S{sec} g{gi} Q{qrange[0]}-{qrange[1]})")
        with open(seed_path, "w", encoding="utf-8") as fh:
            # Match the existing on-disk style (indent=1, raw UTF-8,
            # no trailing newline) so the diff stays minimal.
            json.dump(seed, fh, ensure_ascii=False, indent=1)
        report.append((book, matched, matched_qs))

    print("\nMatched groups per book:")
    total_g = total_q = 0
    for book, ng, nq in report:
        print(f"  {book}: {ng} group(s), {nq} question(s)")
        total_g += ng
        total_q += nq
    print(f"  total: {total_g} group(s), {total_q} question(s)")


if __name__ == "__main__":
    main()
