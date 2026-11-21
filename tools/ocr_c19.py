#!/usr/bin/env python3
"""Ingest Cambridge 19 Listening + Reading from a pure-scan PDF via OCR.

Usage: python3 tools/ocr_c19.py [--pdf PATH] [--out PATH]

The C19 PDFs are scanned images with no text layer, so pages are rendered
with pdftoppm and OCR'd with tesseract (bundled here: runs once, reads
from /tmp/c19/pg afterwards). Layout/heading discovery is still by regex,
never by hardcoded PDF page numbers: the key region is delimited by the
last Audioscripts head and the first Sample head.

Output: app/src/main/assets/seed/c19.json with the same schema as
c20.json/c21_full.json (book/tests/passages/groups, question "answer"),
plus per-question "needs_review" (bool) and "review_note" (str).
A question with no key entry keeps answer null and is marked for review
instead of inventing an answer; such cases (or short counts) fail the
run loudly via PROBLEMS + nonzero exit, but the file is still written.
"""

import glob
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ingest_c21_full as base

PDF = "/root/ielts/Cambridge-IELTS-19-Academic 2.pdf"
OCR_DIR = "/tmp/c19/pg"
OUT = "/root/ielts-app/app/src/main/assets/seed/c19.json"

ENTRY_NUM = re.compile(
    r"^(\d{1,2})[.)\xb0'‘’\"|:;*!©¢$€«»\-_,]*$")
EITHER_NUM = re.compile(r"^(\d{1,2})(&|8)(\d{1,2})$")
RANGE_TOK = re.compile(r"^\d+\s*[-\u2013\u2014]")
CLOSE_TOK = {
    "part", "passage", "passages", "questions", "question", "reading",
    "listening", "test", "lael", "bank", "resource", "explanations",
    "extra", "answer", "key", "keys", "in", "either", "order", "if",
    "you", "chart", "figure", "table", "box", "letter", "letters",
    "correct", "write", "choose", "next", "to", "from", "the",
    "and", "or", "of", "for",
}
GLUE_TOK = {
    "of", "to", "in", "and", "or", "the", "a", "an", "for", "on",
    "by", "with", "from", "as", "at", "is", "it", "be", "we", "are",
    "was", "were", "has", "have", "had", "will", "would", "can", "may",
    "more", "most", "much", "such", "than", "then", "that", "this",
    "these", "those", "it", "its", "your", "their", "his", "her",
}
ANSWER_LIKE = re.compile(
    r"^(?:[A-Ia-i]|TRUE|FALSE|NOT|GIVEN|NOTGIVEN|YES|NO|[a-z]{2,12})$")
SKIP_LINE = re.compile(
    r"score|unlikely|acceptable|examination|conditions|recommend|spend|"
    r"improving|english|institutions|remember|resource bank|"
    r"extra explanations|answer key|不同|chart|sample",
    re.I)
GARBAGE_RUN = re.compile(r"([^\s_])\1{2,}")
MOJIBAKE = re.compile(r"[©¢¤§±ÃÂ®°½€«»„“”†‡•¡¿¬¦]")
LEGIT_NONASCII = set("’‘—–…£éèêàáóíçñü·×÷")


def canonical_answer(text):
    if re.sub(r"\s+", "", text).upper() == "NOTGIVEN":
        return "NOT GIVEN"
    return text


def cleanup_token(tok):
    """Return (text-or-None-to-drop, dirty). Never invents content."""
    if re.fullmatch(r"[~*_#]+", tok):
        return None, True
    m = re.fullmatch(r"(\d)([A-I])", tok)
    if m:
        return m.group(2), True  # "8B" -> "B"
    m = re.fullmatch(r"([A-Ia-i])[a-z]", tok)
    if m:
        return m.group(1).upper(), True  # "Bb" -> "B"
    stripped = tok.strip(".,:;|*!©¢$€«»°'‘’\"\\-_=~")
    if stripped != tok:
        return (stripped or None), True
    return tok, False


def ensure_ocr(pdf):
    have = sorted(glob.glob(os.path.join(OCR_DIR, "p-*.txt")))
    if len(have) >= 130:
        return
    os.makedirs(OCR_DIR, exist_ok=True)
    pre = os.path.join(OCR_DIR, "p")
    subprocess.run(["pdftoppm", "-r", "300", "-gray", "-png",
                    pdf, pre], check=True)
    pngs = sorted(glob.glob(pre + "-*.png"))
    for i in range(0, len(pngs), 2):
        batch = pngs[i:i + 2]
        procs = []
        for p in batch:
            procs.append(subprocess.Popen(
                ["tesseract", p, p[:-4], "-l", "eng", "--psm", "6"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        for pr in procs:
            pr.wait()


def load_pages():
    files = sorted(glob.glob(os.path.join(OCR_DIR, "p-*.txt")))
    assert len(files) >= 130, f"only {len(files)} OCR pages, want 130+"
    pages = []
    for f in files:
        with open(f, encoding="utf-8", errors="replace") as fh:
            pages.append(fh.read())
    return pages


def split_pages(pages):
    def is_audio(p):
        return any(re.match(r"^\s*Audioscripts\s*$", l)
                   for l in p.splitlines())

    def is_sample(p):
        return any(re.match(r"^\s*\|?\s*(S&S \w+ )?Sample\b", l)
                   for l in p.splitlines())

    def is_key(p):
        return (re.search(r"(TEST|Lael)\s*[1-4]", p)
                and ("LISTENING" in p or "READING" in p))

    audio = [i for i, p in enumerate(pages) if is_audio(p)]
    assert audio, "no Audioscripts pages found"
    key_pages = []
    for i in range(audio[-1] + 1, len(pages)):
        if is_sample(pages[i]):
            break
        if is_key(pages[i]):
            key_pages.append(pages[i])
    assert len(key_pages) >= 6, \
        f"only {len(key_pages)} key pages, want 8"
    return pages[:audio[0]], key_pages


OCR_LEADER = re.compile(
    r"(?<!\d)(\d{1,2})\s+([soceunxmziabd._~\-·;: ]{3,}?)"
    r"(?=\s+[A-Z]|\s*\+?\s*$)")
OCR_DIGIT_GLUE = re.compile(r"(?<!\d)(\d)([GOSBIZ])\b")
OCR_DIGIT_MAP = {"G": "6", "O": "0", "S": "5", "B": "8",
                 "I": "1", "Z": "2"}
BOX_ITEM = re.compile(
    r"(?:^|\s)([A-J])\s*[_~.]{1,3}\s+([A-Za-z][A-Za-z\-]*)")
BOX_LINE = re.compile(
    r"^\s*([A-J])\s*([_~.])?\s*(\S.{0,40}?)\s*$")


def ocr_gap_rewrite(lines):
    """Replace OCR-mangled gap leaders with dots, listening only.

    Scanned tables render leaders as letter runs ("4 scacnaan RECTORS");
    rewriting them to dots lets the strict shared parser handle them.
    Scoped to listening sections: reading gaps are line-start numbered
    items, while reading prose must never gain phantom questions.
    """
    out = []
    listening = False
    for raw in lines:
        line = base.clean(raw)
        if base.TEST_HEAD.match(line):
            listening = False
        elif base.SEC_HEAD.match(line):
            listening = True
        elif base.READ_PASS.match(line):
            listening = False
        if listening:
            def _rep(m):
                q = int(m.group(1))
                lead = re.sub(r"\s+", "", m.group(2))
                if 1 <= q <= 40 and len(lead) >= 3:
                    return f"{m.group(1)} ........"
                return m.group(0)
            raw = OCR_DIGIT_GLUE.sub(
                lambda m: m.group(1) + OCR_DIGIT_MAP[m.group(2)], raw)
            raw = OCR_LEADER.sub(_rep, raw)
            raw = re.sub(r"\s*\+\s*$", "", raw)
        out.append(raw)
    return out


def expand_boxes(lines):
    """Split OCR option-box debris into one 'X. text' line per item.

    Tesseract merges "choose from the box" option lists into one line
    ("A _ size B escape C age ..."); the shared parser only recognises
    options line by line, so expand matches (2+ items) up front.
    """
    out = []
    for raw in lines:
        items = BOX_ITEM.findall(raw.replace("\x0c", ""))
        if len(items) >= 2:
            out.extend(f"{letter}. {word}" for letter, word in items)
            continue
        m = BOX_LINE.match(raw.replace("\x0c", ""))
        if m and (m.group(2) or " " not in m.group(3).strip()):
            # single box item per line ("B escape", "D~ water"): a bare
            # capital + short rest as a full line in a listening/read
            # questions run is an option, never prose
            out.append(f"{m.group(1)}. {m.group(3).strip()}")
            continue
        out.append(raw)
    return out


def preclean_body_line(raw):
    l = raw.replace("\x0c", "")
    s = base.clean(l)
    if not s:
        return None
    if re.search(r"E LTS", s):
        return None  # garbled section header/footer
    if len(s) < 28 and re.search(r"S&S|IELTS|MirsIELTS", s):
        return None  # watermark
    if re.search(r"\|\s*p\.\s*\d|\bp\.\s*\d{2,}", s):
        return None  # footer page reference
    if re.match(r"^\d{3}\s+[a-zA-Z]$", s):
        return None  # page number + stray char
    if re.fullmatch(r"\s*Audioscripts\s*", s):
        return "Audioscripts"
    if re.match(r"(?i)^\s*speaking\s*$", s):
        return "SPEAKING"
    if re.fullmatch(r"[^\w\s]+", s) and not re.fullmatch(
            r"[.·․…_~\-\s]{2,}", s):
        return None  # pure punctuation (not a dot-run)
    return l


COL_GAP = 120
LOW_CONF = 85.0


def load_key_words():
    """Read key-page TSVs into pages of word-box lines.

    Returns [(page_width, lines)] where each line is a list of
    (left, right, conf, text) word tuples in x order.
    """
    import csv
    files = sorted(glob.glob(os.path.join(OCR_DIR, "keys-*.tsv")))
    if len(files) < 8:
        for p in (120, 121, 122, 123, 124, 125, 126, 127):
            src = os.path.join(OCR_DIR, f"p-{p}.png")
            subprocess.run(
                ["tesseract", src, os.path.join(OCR_DIR, f"keys-{p}"),
                 "-l", "eng", "--psm", "6", "tsv"],
                check=True, stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL)
        files = sorted(glob.glob(os.path.join(OCR_DIR, "keys-*.tsv")))
    assert len(files) >= 8, f"only {len(files)} key TSVs"
    pages = []
    for f in files:
        width = 2480
        lines = {}
        with open(f, encoding="utf-8") as fh:
            for row in csv.DictReader(fh, delimiter="\t"):
                if row["level"] == "1":
                    try:
                        width = int(row["width"])
                    except ValueError:
                        pass
                if row["level"] != "5":
                    continue
                try:
                    conf = float(row["conf"])
                except ValueError:
                    conf = -1.0
                key = (int(row["block_num"]), int(row["par_num"]),
                       int(row["line_num"]))
                l = int(row["left"])
                w = int(row["width"])
                lines.setdefault(key, []).append(
                    (l, l + w, conf, row["text"] or ""))
        pages.append((width, [sorted(v) for _, v in sorted(lines.items())]))
    return pages


def new_col():
    return {"pending": [], "pending_from": -1, "current": None,
            "toks": [], "dirty": False, "seen": False, "seq": False,
            "last": 0}


def parse_c19_keys():
    """Two-column TSV parse of the OCR key flow.

    Words carry x boxes, so each line is split at its widest gap into
    left/right column token streams. Each column has its own entry and
    either-order state; sequence recovery targets the smallest missing
    number above that column's last attached number (columns ascend).
    Returns (keys, problems, review_flags).
    """
    keys = {}
    cur_test = None
    cur_part = None
    cols = [new_col(), new_col()]
    gid = [0]
    problems = []
    flags = {}
    line_no = [0]

    def cur():
        return keys[cur_test][cur_part]

    def reset_cols():
        for c in cols:
            c["pending"] = []
            c["pending_from"] = -1
            c["current"] = None
            c["toks"] = []
            c["dirty"] = False
            c["last"] = 0

    def flush_col(col, note=""):
        c = cols[col]
        if c["current"] is None or not c["toks"]:
            return  # empty entry (e.g. from "Questions" headers)
        ans = canonical_answer(base.normalize_answer(" ".join(c["toks"])))
        d = cur()
        if c["current"] in d and d[c["current"]] != ans:
            problems.append(
                f"T{cur_test} {cur_part} Q{c['current']}: key conflict "
                f"{d[c['current']]!r} vs {ans!r}")
        else:
            d[c["current"]] = ans
            c["last"] = max(c["last"], c["current"])
        if c["dirty"] or garbage_answer(ans):
            flags[(cur_test, cur_part, c["current"])] = (
                note or "ocr cleanup applied to key; verify")
        c["toks"] = []

    def recover(col, text, note):
        c = cols[col]
        have = set(cur().keys()) - {"__pending__"}
        above = [q for q in range(1, 41)
                 if q not in have and q > c["last"]]
        q = above[0] if above else next(
            (x for x in range(1, 41) if x not in have), None)
        if q is None:
            return
        ans = canonical_answer(base.normalize_answer(text))
        d = cur()
        if q not in d:
            d[q] = ans
            c["last"] = max(c["last"], q)
        flags[(cur_test, cur_part, q)] = note

    def feed(col, items, has_either_word):
        c = cols[col]
        c["seen"] = False
        c["seq"] = False
        for text, conf in items:
            tc = base.clean(text)
            if not tc:
                continue
            low = 0.0 <= conf < LOW_CONF
            nospace = re.sub(r"\s+", "", tc)
            if "EITHER" in nospace.upper():
                flush_col(col)
                mnum = re.match(r"^(\d{1,2})\s*&\s*(\d{1,2})", tc)
                if mnum:
                    qs = (int(mnum.group(1)), int(mnum.group(2)))
                    if 1 <= qs[0] <= 40 and 1 <= qs[1] <= 40:
                        c["pending"] = [qs[0], qs[1]]
                        c["pending_from"] = line_no[0]
                        gid[0] += 1
                c["current"] = None
                continue
            em = EITHER_NUM.match(tc)
            if em and ("&" in tc or has_either_word):
                flush_col(col)
                cand = [int(em.group(1)), int(em.group(3))]
                if all(1 <= q <= 40 for q in cand):
                    c["pending"] = cand
                    c["pending_from"] = line_no[0]
                    gid[0] += 1
                    c["seen"] = True
                c["current"] = None
                continue
            nm = ENTRY_NUM.match(tc)
            if nm and 1 <= int(nm.group(1)) <= 40:
                flush_col(col)
                c["seen"] = True
                qn = int(nm.group(1))
                if qn in cur():
                    # number already filled (OCR misread it): route the
                    # rest of this column by sequence
                    c["current"] = None
                    c["seq"] = True
                else:
                    c["current"] = qn
                    c["seq"] = False
                c["toks"] = []
                c["dirty"] = False
                continue
            if RANGE_TOK.match(tc) or tc.lower() in CLOSE_TOK:
                flush_col(col)
                c["current"] = None
                continue
            if re.fullmatch(r"[.,:;|/]+", tc):
                continue
            text2, dirty = cleanup_token(tc)
            if text2 is None:
                c["dirty"] = True
                continue
            dirty = dirty or low
            if len(text2) > 40:
                flush_col(col)
                c["current"] = None
                continue
            if (c["pending"] and c["pending_from"] < line_no[0]
                    and re.fullmatch(r"[A-Ia-i]", text2)
                    and c["current"] is not None and c["toks"]):
                # bare letter on a later line while an entry already has
                # content: flush the entry, hand the letter to pending
                flush_col(col)
                c["current"] = None
            if c["current"] is not None:
                c["toks"].append(text2)
                c["dirty"] = c["dirty"] or dirty
                continue
            if c["pending"]:
                d = cur()
                d["__pending__"] = d.get("__pending__", {})
                d["__pending__"].setdefault(gid[0], []).append(text2)
                got = d["__pending__"][gid[0]]
                if len(got) == len(c["pending"]):
                    combo = ",".join(
                        canonical_answer(base.normalize_answer(a))
                        for a in got)
                    for q in c["pending"]:
                        if q in d and d[q] != combo:
                            problems.append(
                                f"T{cur_test} {cur_part} Q{q}: key "
                                f"conflict {d[q]!r} vs {combo!r}")
                        else:
                            d[q] = combo
                        if dirty or any(
                                garbage_answer(g) for g in got):
                            flags[(cur_test, cur_part, q)] = (
                                "either-order key from noisy OCR")
                    c["pending"] = []
                continue
            # stray answer-like token: sequence recovery
            if ((c["seq"] or not c["seen"])
                    and ANSWER_LIKE.match(text2)
                    and text2.lower() not in GLUE_TOK
                    and text2 != "a"):
                recover(col, text2,
                        "number missing in OCR; assigned by sequence")
                continue
            # anything else: ignore
        flush_col(col)
        c["current"] = None

    for width, lines in load_key_words():
        mids = []
        for ws in lines:
            if len(ws) >= 2:
                gaps = [(ws[i + 1][0] - ws[i][1],
                         (ws[i][1] + ws[i + 1][0]) / 2)
                        for i in range(len(ws) - 1)]
                g, mid = max(gaps)
                if g >= COL_GAP:
                    mids.append(mid)
        split_x = sorted(mids)[len(mids) // 2] if mids else width / 2
        for ws in lines:
            line_no[0] += 1
            text_all = base.clean(" ".join(w[3] for w in ws))
            if not text_all:
                continue
            if (re.search(r"S&S|MirsIELTS|E LTS", text_all)
                    or re.match(r"^\d{3}\s+[a-z]\.?$", text_all)):
                continue  # watermark / garbled header / page folio
            if SKIP_LINE.search(text_all):
                for col in (0, 1):
                    flush_col(col)
                    cols[col]["pending"] = []
                    cols[col]["current"] = None
                continue
            m = re.search(r"(TEST|Lael|est)\s*([1-4])\b", text_all, re.I)
            if m and re.search(r"TEST|Lael", text_all):
                for col in (0, 1):
                    flush_col(col)
                cur_test = int(m.group(2))
                keys.setdefault(cur_test,
                                {"listening": {}, "reading": {}})
                cur_part = None
                reset_cols()
                continue
            if cur_test is not None and re.search(
                    r"\bLISTENING\b", text_all) and len(text_all) < 40:
                for col in (0, 1):
                    flush_col(col)
                cur_part = "listening"
                reset_cols()
                continue
            if cur_test is not None and re.search(
                    r"\bREADING\b", text_all) and len(text_all) < 40:
                for col in (0, 1):
                    flush_col(col)
                cur_part = "reading"
                reset_cols()
                continue
            if cur_test is None or cur_part is None:
                continue
            if re.fullmatch(r"\d+", text_all):
                continue
            gaps = [(ws[i + 1][0] - ws[i][1], i)
                    for i in range(len(ws) - 1)] if len(ws) > 1 else []
            if gaps and max(g for g, _ in gaps) >= COL_GAP:
                cut = max(gaps)[1]
                groups = [(0, [(w[3], w[2]) for w in ws[:cut + 1]]),
                          (1, [(w[3], w[2]) for w in ws[cut + 1:]])]
            else:
                cx = sum((w[0] + w[1]) / 2 for w in ws) / len(ws)
                groups = [(0 if cx < split_x else 1,
                           [(w[3], w[2]) for w in ws])]
            if len(groups) == 2:
                for col, items in groups:
                    has_either = "EITHER" in re.sub(
                        r"\s+", "", " ".join(t for t, _ in items)).upper()
                    feed(col, items, has_either)
            else:
                col, items = groups[0]
                has_either = "EITHER" in re.sub(
                    r"\s+", "", " ".join(t for t, _ in items)).upper()
                feed(col, items, has_either)
    for t in keys.values():
        for part in t.values():
            part.pop("__pending__", None)
    for col in (0, 1):
        if cols[col]["pending"]:
            problems.append(
                f"T{cur_test} {cur_part}: either-order group "
                f"{cols[col]['pending']} never completed")
    return keys, problems, flags


def garbage_answer(ans):
    core = "".join(ch for ch in ans if ch not in LEGIT_NONASCII)
    if MOJIBAKE.search(core):
        return True
    if GARBAGE_RUN.search(ans):
        return True
    for tok in re.split(r"[\s/]+", ans):
        t = tok.strip("().-")
        if (len(t) <= 2 and t.isalpha() and not t.isupper()
                and t.lower() not in ("a", "an", "of", "or", "to", "in",
                                      "on", "by", "at", "up", "ox")):
            return True
    return False


def garbage_prompt(prompt):
    if len(prompt) < 12:
        return "prompt fragment (very short)"
    core = "".join(ch for ch in prompt if ch not in LEGIT_NONASCII)
    if MOJIBAKE.search(core):
        return "mojibake characters in prompt"
    m = GARBAGE_RUN.search(prompt)
    if m:
        return f"repeated-char OCR run {m.group(0)[:12]!r}"
    return ""


def section_span(section):
    """Expected (lo, hi) question numbers for a section label."""
    m = re.match(r"Listening Section (\d)$", section)
    if m:
        n = int(m.group(1))
        return (10 * (n - 1) + 1, 10 * n)
    m = re.match(r"Reading Passage (\d)$", section)
    if m:
        return {1: (1, 13), 2: (14, 26), 3: (27, 40)}[int(m.group(1))]
    return (1, 40)


def build(pdf, out):
    ensure_ocr(pdf)
    pages = load_pages()
    body_pages, key_pages = split_pages(pages)
    print(f"body pages: {len(body_pages)}, key pages: {len(key_pages)}")
    body_raw = "\x0c".join(body_pages).splitlines()
    print(f"body lines: {len(body_raw)}")

    keys, key_problems, key_flags = parse_c19_keys()
    for t in sorted(keys):
        for part in ("listening", "reading"):
            ks = sorted(keys[t].get(part, {}))
            miss = [q for q in range(1, 41) if q not in keys[t].get(part, {})]
            print(f"keys T{t} {part}: n={len(ks)} missing={miss}")

    body_lines = []
    for raw in body_raw:
        c = preclean_body_line(raw)
        if c is not None:
            body_lines.append(c)
    body_lines = expand_boxes(body_lines)
    body_lines = ocr_gap_rewrite(body_lines)
    bodies = base.parse_body(body_lines)

    data = {"book": "C19", "tests": []}
    problems = list(key_problems)
    for t in sorted(bodies):
        if t not in keys:
            problems.append(f"test {t}: no keys found")
            continue
        winners = {}
        for g in bodies[t]["groups"]:
            for q in g.questions:
                k = (g.section, q["qNo"])
                prev = winners.get(k)
                if prev is None:
                    winners[k] = (g, q)
                elif (q.get("source") == "explicit"
                        and prev[1].get("source") != "explicit"):
                    winners[k] = (g, q)
                elif (q.get("source") != "explicit"
                        and prev[1].get("source") == "explicit"):
                    pass
                elif (q["prompt"] != prev[1]["prompt"]
                        or q["choices"] != prev[1]["choices"]):
                    problems.append(
                        f"T{t} {g.section} Q{q['qNo']}: duplicate prompts "
                        f"({prev[1]['prompt'][:50]!r} vs "
                        f"{q['prompt'][:50]!r})")
        tgroups = []
        by_group = {}
        for (sec, _), (g, q) in winners.items():
            by_group.setdefault(id(g), (g, []))[1].append(q)
        for g in bodies[t]["groups"]:
            if id(g) not in by_group:
                continue
            _, qs = by_group[id(g)]
            kind = ("listening" if g.section.startswith("Listening")
                    else "reading")
            lo, hi = section_span(g.section)
            questions = []
            for q in sorted(qs, key=lambda x: x["qNo"]):
                if not lo <= q["qNo"] <= hi:
                    problems.append(
                        f"T{t} {g.section} Q{q['qNo']}: number outside "
                        f"section span {lo}-{hi} "
                        f"({q['prompt'][:60]})")
                    continue
                ans = keys[t][kind].get(q["qNo"])
                notes = []
                if ans is None:
                    notes.append("no key in OCR; answer left null")
                    problems.append(
                        f"T{t} {g.section} Q{q['qNo']}: no key "
                        f"({q['prompt'][:60]})")
                flag_note = key_flags.get((t, kind, q["qNo"]))
                if flag_note:
                    notes.append("key: " + flag_note)
                gp = garbage_prompt(q["prompt"])
                if gp:
                    notes.append("prompt: " + gp)
                questions.append({
                    "qNo": q["qNo"],
                    "prompt": q["prompt"],
                    "choices": q["choices"],
                    "answer": ans,
                    "needs_review": bool(notes),
                    "review_note": "; ".join(notes),
                })
            if questions:
                tgroups.append({
                    "section": g.section,
                    "passage_title": g.passage_title,
                    "instructions": " ".join(g.instructions).strip(),
                    "word_limit": base.word_limit_of(
                        " ".join(g.instructions)),
                    "questions": questions,
                })
        data["tests"].append({
            "testNo": t,
            "passages": bodies[t]["passages"],
            "groups": tgroups,
        })

    counts = {}
    flagged = 0
    for t in data["tests"]:
        n = sum(len(g["questions"]) for g in t["groups"])
        nl = sum(len(g["questions"]) for g in t["groups"]
                 if g["section"].startswith("Listening"))
        nr = n - nl
        counts[t["testNo"]] = (nl, nr, n)
        flagged += sum(1 for g in t["groups"] for q in g["questions"]
                       if q["needs_review"])
    print("per-test (listening, reading, total):", counts)
    print(f"needs_review questions: {flagged}")

    total = sum(c[2] for c in counts.values())
    if problems:
        print("PROBLEMS:")
        for p in problems:
            print("  " + p)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print(f"wrote {out}: {os.path.getsize(out)} bytes, {total} questions")

    keyless = [p for p in problems if "no key" in p]
    if keyless or any(nl != 40 or nr != 40 for nl, nr, n in counts.values()):
        raise SystemExit(
            f"{len(problems)} problems ({len(keyless)} keyless); "
            f"counts {counts}")
    if problems:
        raise SystemExit(f"{len(problems)} problems")
    print("OK: 4x40 keyed")


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    pdf = args[0] if len(args) > 0 else PDF
    out = args[1] if len(args) > 1 else OUT
    build(pdf, out)
