#!/usr/bin/env python3
"""Ingest Cambridge 21 Writing/Speaking prompts plus sample answers.

Usage: python3 tools/ingest_c21.py [pdf-path] [out-path]

Sources everything from the text layer via ``pdftotext -layout`` plus
page renders via ``pdftoppm``. Headings are found by regex, never by
hardcoded PDF page numbers (printed Contents numbers are offset from
PDF indices). Task 1 chart regions are cropped from the Task 1 page
renders with PIL.

Output: app/src/main/assets/seed/c21_writing.json with, per test:
  writingTask1 (prompt, chart caption, chart image asset path),
  writingTask2 (prompt), speaking (parts 1-3), samples (banded
  candidate answers with examiner comments for marker calibration).

Cambridge 21 Listening/Reading ingest is deferred to post-Dec 1.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

PDF = "/root/ielts/Cambrige 21.pdf" if len(sys.argv) < 2 else sys.argv[1]
# Accept the actual on-disk name; fall back to the tidy spelling.
if not os.path.exists(PDF):
    alt = "/root/ielts/Cambridge 21.pdf"
    if os.path.exists(alt):
        PDF = alt
OUT = ("/root/ielts-app/app/src/main/assets/seed/c21_writing.json"
       if len(sys.argv) < 3 else sys.argv[2])
IMG_DIR = os.path.join(os.path.dirname(OUT), "..", "images", "c21")
IMG_DIR = os.path.normpath(IMG_DIR)

TASK1_HEAD = re.compile(r"WRITING\s+TASK\s+1\b")
TASK2_HEAD = re.compile(r"WRITING\s+TASK\s+2\b")
SPEAK_HEAD = re.compile(r"^\s*SPEAKING\s*$")
PART_HEAD = re.compile(r"^PART\s+([123])\s*$")
TEST_HEAD = re.compile(r"^Test\s+([1-4])\s*$")
SAMPLE_HEAD = re.compile(r"TEST\s+([1-4]),\s*WRITING\s+TASK\s+([12])")
BAND_LINE = re.compile(
    r"achieved a Band\s+([0-9](?:\s*\.\s*[05])?)\s+score")
EXAMINER_HEAD = re.compile(r"Here is the examiner's comment:")
OVERVIEW_HEAD = re.compile(r"Example overview")
PART2_BOILER = {
    "You will have to talk",
    "about the topic for 1 to",
    "2 minutes. You have",
    "1 minute to think about",
    "what you are going to",
    "say. You can make some",
    "notes to help you if",
    "you wish.",
}


def extract_text(pdf):
    tmp = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext", "-layout", pdf, tmp], check=True)
    with open(tmp, encoding="utf-8", errors="replace") as f:
        return f.read()


def pages(text):
    """Split layout text into 1-indexed PDF pages on form feeds."""
    raw = text.split("\x0c")
    return {i + 1: raw[i].splitlines() for i in range(len(raw))}


def find_pages(pgs, pattern):
    return [n for n, lines in pgs.items()
            if any(pattern.search(l) for l in lines)]


def clean(lines):
    out = []
    for l in lines:
        s = l.strip()
        if s:
            out.append(re.sub(r"\s+", " ", s))
    return out


def parse_task1(lines):
    """Return (prompt, chart_caption_lines) from a Task 1 page."""
    body = clean(lines)
    joined = "\n".join(body)
    m = TASK1_HEAD.search(joined)
    assert m, "WRITING TASK 1 heading not found"
    after = joined[m.end():]
    m2 = re.search(r"Write at least 150 words\.", after)
    assert m2, "Task 1 word-count line not found"
    prompt = " ".join(after[:m2.start()].split())
    rest = after[m2.end():].split("\n")
    caption = []
    for l in rest:
        s = l.strip()
        if not s:
            continue
        if re.match(r"^[➔~pI.\s\d]*$", s):
            continue  # footer cross-reference, not chart text
        if re.fullmatch(r"[\d\s.,\-–—·•%'\"()©®|/\\_]+", s):
            continue  # axis ticks / chart junk
        caption.append(s)
        if len(caption) >= 3 and len(" ".join(caption)) > 40:
            break
    return prompt, caption[:6]


def parse_task2(lines):
    body = clean(lines)
    joined = "\n".join(body)
    m = TASK2_HEAD.search(joined)
    assert m, "WRITING TASK 2 heading not found"
    after = joined[m.end():]
    m2 = re.search(r"Write at least 250 words\s*\.?", after)
    assert m2, "Task 2 word-count line not found"
    chunk = after[:m2.start()]
    chunk = re.sub(r"Write about the following\s+topic:",
                   "", chunk, flags=re.I)
    return " ".join(chunk.split())


def strip_part2_boiler(lines):
    """Drop the right-hand timing column merged in by -layout.

    The cue card (left) and the timing notes (right) land on shared
    lines separated by wide gaps, so split on 2+ spaces and discard
    any segment that is timing boilerplate.
    """
    kept = []
    for l in lines:
        segs = [s for s in re.split(r"\s{2,}", l.strip()) if s.strip()]
        segs = [s for s in segs if s.strip() not in PART2_BOILER]
        line = " ".join(segs)
        if line.strip():
            kept.append(line)
    return kept


def parse_speaking(lines):
    """Return dict with part1/part2/part3 raw text from a SPEAKING page."""
    idx = [i for i, l in enumerate(lines) if PART_HEAD.match(l.strip())]
    assert len(idx) >= 3, "fewer than 3 PART headings on speaking page"
    blocks = {}
    for k, pos in enumerate(idx):
        pm = PART_HEAD.match(lines[pos].strip())
        assert pm is not None, "PART heading lost its number"
        part = pm.group(1)
        end = idx[k + 1] if k + 1 < len(idx) else len(lines)
        blocks[part] = lines[pos + 1:end]
    p1 = clean(blocks["1"])
    p1 = [l for l in p1 if "examiner asks you about yourself" not in l
          and l != "topics." and l != "EXAMPLE"]
    p2 = clean(strip_part2_boiler(blocks["2"]))
    p3 = clean(blocks["3"])
    assert p1 and p2 and p3, "empty speaking part parsed"
    return {
        "part1": "\n".join(p1),
        "part2": "\n".join(p2),
        "part3": "\n".join(p3),
    }


def parse_samples(text):
    """Return {(test, task): {band, text, examinerComment, exampleOverview}}."""
    heads = list(SAMPLE_HEAD.finditer(text))
    assert len(heads) == 8, \
        "expected 8 sample answers (2 per test), found %d" % len(heads)
    samples = {}
    for k, h in enumerate(heads):
        seg = text[h.end():heads[k + 1].start() if k + 1 < len(heads)
                   else len(text)]
        bm = BAND_LINE.search(seg)
        assert bm, "band line missing in sample %s" % h.group(0)
        band = float(re.sub(r"\s+", "", bm.group(1)))
        body = seg[bm.end():]
        em = EXAMINER_HEAD.search(body)
        assert em, "examiner comment missing in sample %s" % h.group(0)
        answer = " ".join(body[:em.start()].split())
        comment = body[em.end():]
        om = OVERVIEW_HEAD.search(comment)
        overview = ""
        if om:
            overview = " ".join(comment[om.end():].split())
            comment = comment[:om.start()]
        comment = re.sub(r"\s*\x0c\s*", " ", comment)
        comment = " ".join(comment.split())
        answer = answer.strip()
        assert len(answer) > 200, \
            "sample answer too short in %s" % h.group(0)
        assert len(comment) > 100, \
            "examiner comment too short in %s" % h.group(0)
        key = (int(h.group(1)), int(h.group(2)))
        assert key not in samples, "duplicate sample %s" % (key,)
        samples[key] = {"band": band, "text": answer,
                        "examinerComment": comment,
                        "exampleOverview": overview.strip()}
    return samples


def render_chart(page_no, dest):
    """Render one PDF page at 150dpi and crop the chart region."""
    from PIL import Image
    tmp = tempfile.mktemp(prefix="c21chart")
    subprocess.run(["pdftoppm", "-png", "-r", "150", "-f", str(page_no),
                    "-l", str(page_no), PDF, tmp], check=True)
    src = tmp + "-%03d.png" % page_no
    if not os.path.exists(src):
        cands = [f for f in os.listdir(tempfile.gettempdir())
                 if f.startswith(os.path.basename(tmp))]
        raise AssertionError("pdftoppm produced no output: %s" % cands)
    img = Image.open(src)
    w, h = img.size
    # Chart sits below the prompt text: keep the lower band of the page.
    crop = img.crop((int(0.02 * w), int(0.36 * h),
                     int(0.98 * w), int(0.95 * h)))
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    crop.save(dest)
    os.remove(src)
    return dest


def main():
    text = extract_text(PDF)
    pgs = pages(text)
    task1_pages, task2_pages, speak_pages = [], [], []
    cur_test = None
    ordered = sorted(pgs)
    for n in ordered:
        for l in pgs[n]:
            m = TEST_HEAD.match(l.strip())
            if m:
                cur_test = int(m.group(1))
        if any(SAMPLE_HEAD.search(l) for l in pgs[n]):
            continue  # sample-answer pages reuse task headings
        if any(TASK1_HEAD.search(l) for l in pgs[n]):
            task1_pages.append((cur_test, n))
        if any(TASK2_HEAD.search(l) for l in pgs[n]):
            task2_pages.append((cur_test, n))
        if any(SPEAK_HEAD.match(l) for l in pgs[n]):
            speak_pages.append((cur_test, n))
    assert len(task1_pages) == 4, "expected 4 Task 1 pages, got %s" \
        % (task1_pages,)
    assert len(task2_pages) == 4, "expected 4 Task 2 pages, got %s" \
        % (task2_pages,)
    assert len(speak_pages) == 4, "expected 4 Speaking pages, got %s" \
        % (speak_pages,)
    samples = parse_samples(text)

    tests = []
    os.makedirs(IMG_DIR, exist_ok=True)
    for t in (1, 2, 3, 4):
        p1 = next(n for tt, n in task1_pages if tt == t)
        p2 = next(n for tt, n in task2_pages if tt == t)
        ps = next(n for tt, n in speak_pages if tt == t)
        prompt1, caption = parse_task1(pgs[p1])
        prompt2 = parse_task2(pgs[p2])
        speaking = parse_speaking(pgs[ps])
        img_name = "t%d_task1.png" % t
        render_chart(p1, os.path.join(IMG_DIR, img_name))
        entry = {
            "testNo": t,
            "writingTask1": {
                "timeMinutes": 20,
                "minWords": 150,
                "prompt": prompt1,
                "chartCaption": caption,
                "chartImage": "images/c21/" + img_name,
            },
            "writingTask2": {
                "timeMinutes": 40,
                "minWords": 250,
                "prompt": prompt2,
            },
            "speaking": speaking,
            "samples": [],
        }
        for task in (1, 2):
            s = samples.get((t, task))
            assert s is not None, \
                "missing sample answer T%d task %d" % (t, task)
            entry["samples"].append({"task": task, **s})
        tests.append(entry)

    out = {"book": "C21", "tests": tests}
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    n_words = sum(len(s["text"].split()) for t in tests
                  for s in t["samples"])
    print("wrote %s: %d tests, %d samples, %d sample words"
          % (OUT, len(tests),
             sum(len(t["samples"]) for t in tests), n_words))


if __name__ == "__main__":
    main()
