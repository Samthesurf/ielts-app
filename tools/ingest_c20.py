#!/usr/bin/env python3
"""Ingest Cambridge 20 (text-selectable PDF) into seed JSON.

Usage: python3 tools/ingest_c20.py [pdf-path] [out-path]

Parses all 4 tests, Listening Sections 1-4 plus Reading Passages 1-3
(Q1-40 each), with answer keys from the 2-column key pages at the end
of the book. Cambridge 20 has no Writing or Speaking prompts, so none
are emitted. Answers come only from the printed keys; a question with
no key entry fails the run instead of being invented.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

PDF = "/root/ielts/Cambrige 20.pdf"
OUT = "/root/ielts-app/app/src/main/assets/seed/c20.json"

TEST_HEAD = re.compile(r"^TEST ([1-4])$")
KEY_HEAD = re.compile(r"TEST ([1-4]) (LISTENING|READING) ANSWERS")
KEY_PART = re.compile(r"^\s*(LISTENING|READING) ANSWERS\s*$")
SEC_HEAD = re.compile(r"^Section (\d)\s+Questions?\s+(.+)$")
Q_HEAD = re.compile(r"^Questions?\s+(\d+)\s*[-\u2013\u2014]|and\s+(\d+)", re.I)
Q_HEAD_FULL = re.compile(
    r"^Questions?\s+(\d+)\s*(?:[-\u2013\u2014]|and)\s*(\d+)\s*(.*)$", re.I)
RANGE_INLINE = re.compile(r"^(\d+)\s*[-\u2013\u2014]\s*(\d+)\s*(.+)$")
NUM_ITEM = re.compile(r"^(\d{1,2})(?:[.)]\s*|\s+)(.+)$")
OPTION = re.compile(r"^([A-G])\.\s*(\S.*)$")
GAP = re.compile(r"(?<!\d)(\d{1,2})(?:\s*[.]?\s*£)?\s*[.…_]{2,}")
READ_PASS = re.compile(r"^READING PASSAGE (\d)")
SPEND = re.compile(r"You should spend about 20 minutes")
KEY_TOKEN = re.compile(r"(\d{1,2})(?:\s*[-\u2013\u2014]\s*(\d{1,2}))?\s*(\S+)")
WORD_LIMIT = re.compile(
    r"ONE WORD AND/OR A NUMBER|ONE WORD ONLY|NO MORE THAN (ONE|\d+) WORDS?",
    re.I)
INSTR_RE = re.compile(
    r"^(Choose|Write|Complete the|Match|Look at|NB\b|In boxes|For each|"
    r"You may|Which section|Which paragraph|Reading passage \d has)")


# --- Cell-aware table parsing (wrapped multi-column tables) ---
# pdftotext -layout keeps the table columns aligned, but clean() collapses
# the whitespace, so stitching cleaned lines mashes fragments from different
# columns together (e.g. 'the station keen on 1 ___ good place for a drink').
# These helpers slice each raw layout line by the column x-ranges derived
# from the header row, join each cell's wrapped fragments vertically, and
# only then form one prompt per gap number.

TABLE_HEADERS = [
    ("Name of", "Location", "Reason"),  # T1 restaurant table
    ("Name of company", "Information about costs",
     "Additional notes"),  # T3 furniture table
]

TABLE_STOP = re.compile(
    r"^(TEST|Section|Questions?|Choose|Complete|Write|Match|Look|Label|"
    r"Which|NB\b|You may|For each|In boxes|Reading passage|LISTENING|READING)",
    re.I)


def norm_gap_spacing(s):
    """Ensure a space after ___ when the source glued text to the dots."""
    return re.sub(r"___([A-Za-z\u00a3$])", r"___ \1", s)


def is_table_header(cleaned):
    for markers in TABLE_HEADERS:
        if all(m in cleaned for m in markers):
            return True
    return False


def split_header_segments(header_raw):
    return [p for p in re.split(r"\s{3,}", header_raw.strip()) if p]


def column_bounds(header_raw, segments):
    """Column x-ranges keyed on each header title's start offset.

    Wrapped body fragments begin at their column's left edge, so the
    header starts themselves are the cut points (midpoints slice words
    like 'especially' or 'renting' in two).
    """
    starts, pos = [], 0
    for seg in segments:
        j = header_raw.find(seg, pos)
        if j < 0:
            return None
        starts.append(j)
        pos = j + len(seg)
    return [(0 if i == 0 else starts[i],
             starts[i + 1] if i + 1 < len(starts) else None)
            for i in range(len(segments))]


def body_bounds(body, ncols):
    """Derive column x-ranges from the body's own text runs.

    Header titles are often offset from the body columns, so cut at the
    median gap between runs on lines holding all ncols columns.
    Returns None when too few full lines exist.
    """
    gaps: list = [[] for _ in range(ncols - 1)]
    for r in body:
        runs = [(m.start(), m.end())
                for m in re.finditer(r"\S+(?: \S+)*", r)]
        if len(runs) < ncols:
            continue
        runs = runs[:ncols]
        for j in range(ncols - 1):
            gaps[j].append((runs[j][1] + runs[j + 1][0]) // 2)
    if any(len(g) < 2 for g in gaps):
        return None

    def med(xs):
        xs = sorted(xs)
        return xs[len(xs) // 2]

    cuts = [med(g) for g in gaps]
    los, his = [0] + cuts, cuts + [None]
    return list(zip(los, his))


def slice_row(raw, bounds):
    """Slice a raw layout line into one fragment per column.

    A boundary can cut a word in two ('rentin|g', 'goo|d', dots+'are');
    when the end of one fragment is contiguous with the start of the next
    (no whitespace between in the raw line) the leading token belongs to
    the previous column, so glue it back.
    """
    frags, spans = [], []
    for lo, hi in bounds:
        seg = raw[lo:hi] if hi is not None else raw[lo:]
        if seg.strip():
            start = lo + (len(seg) - len(seg.lstrip()))
            end = lo + len(seg.rstrip())
            frags.append(seg.strip())
            spans.append((start, end))
        else:
            frags.append("")
            spans.append(None)
    for i in range(1, len(frags)):
        if (frags[i] and frags[i - 1] and spans[i] and spans[i - 1]
                and spans[i - 1][1] == spans[i][0]):
            mm = re.match(r"\S+", frags[i])
            tok = mm.group(0) if mm else ""
            frags[i - 1] += tok
            frags[i] = frags[i][len(tok):].strip()
    return frags


HUNGRY_TAIL = re.compile(
    r"(the|a|an|of|to|for|from|with|and|or|most|per|at|in|on|is|are|was|-)$",
    re.I)


def normalize_table_text(text):
    text = GAP.sub(lambda m: f"{m.group(1)} ___", text)
    text = norm_gap_spacing(text)
    return re.sub(r"\s+", " ", text).strip()


def redact_gaps(text, keep):
    def rep(m):
        return m.group(0) if int(m.group(1)) == keep else "\u2026"
    return re.sub(r"(?<!\d)(\d{1,2}) ___", rep, text)


def redact_all_gaps(text):
    return re.sub(r"(?<!\d)(\d{1,2}) ___", "\u2026", text)


def parse_one_table(lines, hi, test_no, section):
    """Parse one wrapped table starting at header index hi.

    Returns dict with title/headers/prompts plus '_next' (first index
    past the table body), or None if the header has no usable columns.
    """
    n = len(lines)
    segments = split_header_segments(lines[hi])
    if len(segments) < 3:
        return None
    bounds = column_bounds(lines[hi], segments)
    if bounds is None:
        return None
    headers = list(segments)
    j = hi + 1
    cont_lines = []
    while j < n:  # header continuations, e.g. 'restaurant ... recommendation'
        c = clean(lines[j])
        if not c:
            break
        if (GAP.search(c) or TABLE_STOP.match(c)
                or Q_HEAD_FULL.match(c) or SEC_HEAD.match(c)
                or TEST_HEAD.match(c) or KEY_HEAD.search(c)):
            break
        cont_lines.append(lines[j])
        j += 1
        if j - hi > 4:
            break
    body = []
    k = j
    while k < n:
        c = clean(lines[k])
        if (TEST_HEAD.match(c) or SEC_HEAD.match(c)
                or Q_HEAD_FULL.match(c) or KEY_HEAD.search(c)
                or c.startswith("Section ") or TABLE_STOP.match(c)):
            break
        body.append(lines[k])
        k += 1
        if k - j > 90:
            break
    full = [r for r in body if clean(r)]
    bounds = body_bounds(full, len(segments)) or bounds
    for cr in cont_lines:
        frags = slice_row(cr, bounds)
        headers = [h + (" " + f if f else "")
                   for h, f in zip(headers, frags)]
    blocks, cur = [], []
    for r in body:
        if not clean(r):
            if cur:
                blocks.append(cur)
                cur = []
        else:
            cur.append(r)
    if cur:
        blocks.append(cur)
    # Segment blocks into rows. T1 rows are blank-separated, but T3 rows
    # run together, so also start a new row when a line refills column 0
    # with a named entry while the current row already holds gaps.
    # A lone fragment in column 0 whose row has a hungry tail elsewhere
    # ('... for the most' + 'up-to-date prices') is a wrap, not a row.
    rows = []
    for b in blocks:
        cur_cols, cur_gap = None, False
        for r in b:
            frags = slice_row(r, bounds)
            has_gap = bool(GAP.search(clean(r)))
            nonempty = [i for i, f in enumerate(frags) if f]
            if (nonempty == [0] and cur_cols is not None
                    and any(cur_cols[0])):
                tails = [" ".join(c) for c in cur_cols]
                hungry = [j for j in range(1, len(tails))
                          if tails[j] and HUNGRY_TAIL.search(tails[j])]
                if hungry:
                    cur_cols[hungry[-1]].append(frags[0])
                    cur_gap = cur_gap or has_gap
                    continue
            if cur_cols is None:
                cur_cols = [[f] if f else [] for f in frags]
                cur_gap = has_gap
            elif (frags[0] and any(frags[1:])
                    and re.search(r"[A-Za-z]", frags[0]) and cur_gap):
                rows.append(cur_cols)
                cur_cols = [[f] if f else [] for f in frags]
                cur_gap = has_gap
            else:
                for ci, f in enumerate(frags):
                    if f:
                        cur_cols[ci].append(f)
                cur_gap = cur_gap or has_gap
        if cur_cols is not None:
            rows.append(cur_cols)
    rows = [c for c in rows
            if any(GAP.search(" ".join(f)) for f in c)]
    if not rows:
        return None
    prompts = {}
    for cols in rows:
        rawcells = [" ".join(f) for f in cols]
        qnos = []
        for cell in rawcells:
            for gm in GAP.finditer(cell):
                q = int(gm.group(1))
                if q not in qnos:
                    qnos.append(q)
        cells = [normalize_table_text(c) for c in rawcells]
        name = redact_all_gaps(cells[0]) if cells[0] else ""
        if not name:
            for c in cells[1:]:
                if c:
                    name = redact_all_gaps(c)
                    break
        for q in qnos:
            ci = next(i for i, c in enumerate(cells)
                      if re.search(rf"(?<!\d){q} ___", c))
            cell_q = redact_gaps(cells[ci], q)
            if ci == 0:
                prompt = f"{headers[0]}: {cell_q}"
            else:
                prompt = f"{name} - {headers[ci]}: {cell_q}"
            prompts[q] = prompt
    title = ""
    t = hi - 1
    while t >= 0 and not clean(lines[t]):
        t -= 1
    if t >= 0:
        c = clean(lines[t])
        if (not GAP.search(c) and not TABLE_STOP.match(c)
                and not Q_HEAD_FULL.match(c) and not SEC_HEAD.match(c)
                and not TEST_HEAD.match(c) and not KEY_HEAD.search(c)):
            title = c
    return {"test": test_no, "section": section, "title": title,
            "headers": headers, "prompts": prompts, "_next": k}


def parse_tables(lines):
    """Scan raw layout lines for wrapped tables; return list of dicts."""
    tables = []
    cur_t, cur_sec = None, None
    i, n = 0, len(lines)
    while i < n:
        cl = clean(lines[i])
        m = TEST_HEAD.match(cl)
        if m:
            cur_t = int(m.group(1))
            cur_sec = None
            i += 1
            continue
        m = SEC_HEAD.match(cl)
        if m:
            cur_sec = f"Listening Section {m.group(1)}"
            i += 1
            continue
        if (cur_t is not None and cur_sec is not None
                and is_table_header(cl)):
            t = parse_one_table(lines, i, cur_t, cur_sec)
            if t is not None:
                tables.append(t)
                i = t.pop("_next")
                continue
        i += 1
    return tables


PARA_STUB = re.compile(r"^(Paragraph [A-G])[\s….․…]*(.*)$", re.S)


def clean_paragraph_stubs(bodies):
    """Matching-heading prompts should be just the label.

    The generic stitcher leaves leader dots ('Paragraph A………') and can
    glue the next passage title onto the last stub ('Paragraph F…………
    Can the ...?'). Neither is a numbered blank; keep the label only.
    """
    for test in bodies.values():
        for g in test["groups"]:
            for q in g.questions:
                if re.search(r"\d\s*___", q["prompt"]):
                    continue
                m = PARA_STUB.match(q["prompt"])
                if m:
                    q["prompt"] = m.group(1)


def apply_table_fixes(bodies, tables):
    """Override garbled stitched prompts with cell-aware ones (in place)."""
    for t in tables:
        test = bodies.get(t["test"])
        if not test:
            continue
        for g in test["groups"]:
            if g.section != t["section"]:
                continue
            if not any(q["qNo"] in t["prompts"] for q in g.questions):
                continue
            for q in g.questions:
                if q["qNo"] in t["prompts"]:
                    q["prompt"] = t["prompts"][q["qNo"]]
            base = " ".join(g.instructions).strip()
            m = re.search(r"for each answer\.", base)
            if m:
                base = base[:m.end()]
            if t["title"] and t["title"] not in base:
                base = (base + " " + t["title"]).strip()
            g.instructions = [base] if base else []


def extract_text(pdf):
    tmp = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext", "-layout", pdf, tmp], check=True)
    with open(tmp, encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


def split_columns(line):
    """Split a 2-column key line into halves on wide gaps."""
    parts = re.split(r"\s{4,}", line.strip())
    return [p for p in parts if p]


def parse_keys(lines):
    """Return {testNo: {'listening': {q: ans}, 'reading': {q: ans}}}."""
    keys = {}
    cur_test = None
    cur_part = None
    for line in lines:
        m = KEY_HEAD.search(line)
        if m:
            t = int(m.group(1))
            cur_test = keys.setdefault(t, {"listening": {}, "reading": {}})
            cur_part = m.group(2).lower()
            continue
        m = KEY_PART.match(line)
        if m and cur_test is not None:
            cur_part = m.group(1).lower()
            continue
        if cur_test is None or cur_part is None:
            continue
        keydict = cur_test[cur_part]
        for half in split_columns(line):
            if re.search(r"passage|section", half, re.I):
                continue
            for tm in KEY_TOKEN.finditer(half):
                q1 = int(tm.group(1))
                q2 = int(tm.group(2)) if tm.group(2) else q1
                ans = tm.group(3).replace(".", ",")
                for q in range(q1, q2 + 1):
                    keydict[q] = ans
    return keys


class Group:
    def __init__(self, section, passage_title=None):
        self.section = section
        self.passage_title = passage_title
        self.instructions = []
        self.questions = []  # dicts qNo/prompt/choices
        self.pending_range: tuple | None = None
        self.pre_lines = []
        self.options = []
        self.fresh_range = False

    def flush_shared(self):
        if self.pending_range and self.fresh_range:
            pre = list(self.pre_lines)
            if pre and INSTR_RE.match(pre[0]):
                self.instructions.append(pre.pop(0))
            stem = " ".join(pre).strip()
            if stem:
                for q in range(self.pending_range[0],
                               self.pending_range[1] + 1):
                    self.questions.append(
                        {"qNo": q, "prompt": stem,
                         "choices": list(self.options)})
            else:
                self.instructions.extend(pre)
        else:
            self.instructions.extend(self.pre_lines)
        self.pending_range = None
        self.pre_lines = []
        self.options = []
        self.fresh_range = False


def clean(s):
    return re.sub(r"\s+", " ", s.replace("\x0c", "")).strip()


def parse_body(lines):
    """Return {testNo: {'groups': [...], 'passages': [...]}}."""
    tests = {}
    cur_t = None
    cur_group = None
    state = None  # 'listen' | 'readpass' | 'questions'
    cur_pass_sec = None
    cur_pass_title = None
    passage_paras = []
    passage_title = None
    groups = []
    passages = []

    def new_test(t):
        nonlocal groups, passages
        if cur_t is not None:
            if cur_group is not None:
                cur_group.flush_shared()
                groups.append(cur_group)
            tests[cur_t] = {"groups": groups, "passages": passages}
        groups, passages = [], []

    def new_group(section, title=None):
        nonlocal cur_group
        if cur_group is not None:
            cur_group.flush_shared()
            groups.append(cur_group)
        cur_group = Group(section, title)

    for raw in lines:
        line = clean(raw)
        if not line:
            continue
        if KEY_HEAD.search(line):
            break  # keys handled separately
        m = TEST_HEAD.match(line)
        if m:
            new_test(int(m.group(1)))
            cur_t = int(m.group(1))
            cur_group = None
            state = None
            cur_pass_sec = None
            cur_pass_title = None
            continue
        if cur_t is None:
            continue
        m = READ_PASS.match(line)
        if m:
            if cur_group is not None:
                cur_group.flush_shared()
                groups.append(cur_group)
                cur_group = None
            state = "readpass"
            cur_pass_sec = f"Reading Passage {m.group(1)}"
            cur_pass_title = None
            passage_paras = []
            passage_title = None
            passages.append({"section": f"Reading Passage {m.group(1)}",
                             "title": "", "paragraphs": []})
            continue
        m = SEC_HEAD.match(line)
        if m:
            new_group(f"Listening Section {m.group(1)}")
            cur_pass_sec = None
            state = "questions"
            rng = Q_HEAD_FULL.match(f"Questions {m.group(2)}")
            assert cur_group is not None
            if rng and int(rng.group(1)) != int(rng.group(2)):
                cur_group.pending_range = (int(rng.group(1)),
                                           int(rng.group(2)))
                cur_group.fresh_range = True
            continue
        m = Q_HEAD_FULL.match(line)
        if m:
            if cur_group is None:
                new_group(cur_pass_sec or "Listening ?",
                          cur_pass_title)
            else:
                cur_group.flush_shared()
                if (cur_group.questions or cur_group.options
                        or cur_group.pre_lines or cur_group.instructions):
                    groups.append(cur_group)
                    cur_group = Group(cur_group.section,
                                      cur_group.passage_title)
            assert cur_group is not None
            q1, q2, rest = int(m.group(1)), int(m.group(2)), clean(m.group(3))
            if q1 != q2:
                cur_group.pending_range = (q1, q2)
                cur_group.fresh_range = True
                if rest:
                    cur_group.pre_lines.append(rest)
            else:
                cur_group.fresh_range = False
                cur_group.questions.append(
                    {"qNo": q1, "prompt": rest, "choices": []})
            state = "questions"
            continue
        if state == "readpass":
            if SPEND.search(line):
                continue
            if passage_title is None:
                passage_title = line
                cur_pass_title = line
                passages[-1]["title"] = line
            else:
                passage_paras.append(line)
                passages[-1]["paragraphs"] = list(passage_paras)
            continue
        if state != "questions" or cur_group is None:
            continue
        m = OPTION.match(line)
        if m:
            choice = f"{m.group(1)}. {clean(m.group(2))}"
            targets = [q for q in cur_group.questions if not q["choices"]]
            if targets:
                for q in targets:
                    q["choices"].append(choice)
            else:
                cur_group.options.append(choice)
            continue
        m = RANGE_INLINE.match(line)
        if m:
            assert cur_group is not None
            cur_group.flush_shared()
            q1, q2 = int(m.group(1)), int(m.group(2))
            if q1 < q2 and q2 - q1 <= 6:
                cur_group.pending_range = (q1, q2)
                cur_group.fresh_range = True
                cur_group.pre_lines.append(clean(m.group(3)))
                continue
        gm = GAP.search(line)
        if gm:
            assert cur_group is not None
            cur_group.fresh_range = False
            prompt = clean(GAP.sub(lambda x: f"{x.group(1)} ___", line))
            prompt = norm_gap_spacing(prompt)
            for qm in GAP.finditer(line):
                q = int(qm.group(1))
                if not any(x["qNo"] == q for x in cur_group.questions):
                    cur_group.questions.append(
                        {"qNo": q, "prompt": prompt,
                         "choices": list(cur_group.options)})
            continue
        m = NUM_ITEM.match(line)
        if m:
            assert cur_group is not None
            cur_group.fresh_range = False
            q = int(m.group(1))
            prompt = clean(GAP.sub(lambda x: f"{x.group(1)} ___",
                                   clean(m.group(2))))
            prompt = norm_gap_spacing(prompt)
            cur_group.questions.append(
                {"qNo": q, "prompt": prompt,
                 "choices": list(cur_group.options)})
            continue
        # instruction or shared stem or continuation
        if cur_group.pending_range and cur_group.fresh_range:
            cur_group.pre_lines.append(line)
        elif cur_group.questions and not cur_group.options:
            last = cur_group.questions[-1]
            if not last["choices"]:
                last["prompt"] += " " + line
            else:
                cur_group.instructions.append(line)
        else:
            cur_group.instructions.append(line)

    if cur_t is not None:
        if cur_group is not None:
            cur_group.flush_shared()
            groups.append(cur_group)
        tests[cur_t] = {"groups": groups, "passages": passages}
    return tests


def word_limit_of(instructions):
    m = WORD_LIMIT.search(instructions)
    return m.group(0).upper() if m else ""


def build(pdf, out):
    lines = extract_text(pdf)
    keys = parse_keys(lines)
    bodies = parse_body(lines)
    tables = parse_tables(lines)
    apply_table_fixes(bodies, tables)
    clean_paragraph_stubs(bodies)
    print(f"cell-aware tables fixed: {len(tables)} " +
          ", ".join(f"T{t['test']} {t['section']} "
                    f"Q{min(t['prompts'])}-{max(t['prompts'])}"
                    for t in tables))
    data = {"book": "C20", "tests": []}
    problems = []
    for t in sorted(bodies):
        if t not in keys:
            problems.append(f"test {t}: no keys found")
            continue
        tgroups = []
        for g in bodies[t]["groups"]:
            kind = ("listening" if g.section.startswith("Listening")
                    else "reading")
            questions = []
            for q in sorted(g.questions, key=lambda x: x["qNo"]):
                ans = keys[t][kind].get(q["qNo"])
                if ans is None:
                    problems.append(
                        f"T{t} {g.section} Q{q['qNo']}: no key "
                        f"({q['prompt'][:60]})")
                    continue
                questions.append({
                    "qNo": q["qNo"],
                    "prompt": q["prompt"],
                    "choices": q["choices"],
                    "answer": ans,
                })
            if questions:
                tgroups.append({
                    "section": g.section,
                    "passage_title": g.passage_title,
                    "instructions": " ".join(g.instructions).strip(),
                    "word_limit": word_limit_of(" ".join(g.instructions)),
                    "questions": questions,
                })
        data["tests"].append({
            "testNo": t,
            "passages": bodies[t]["passages"],
            "groups": tgroups,
        })

    counts = {}
    for t in data["tests"]:
        n = sum(len(g["questions"]) for g in t["groups"])
        nl = sum(len(g["questions"]) for g in t["groups"]
                 if g["section"].startswith("Listening"))
        nr = n - nl
        counts[t["testNo"]] = (nl, nr, n)
    print("per-test (listening, reading, total):", counts)

    total = sum(c[2] for c in counts.values())
    if problems:
        print("PROBLEMS:")
        for p in problems:
            print("  " + p)
    assert not problems, f"{len(problems)} questions lack keys"
    for t, (nl, nr, n) in counts.items():
        assert nl == 40, f"test {t}: {nl} listening, want 40"
        assert nr == 40, f"test {t}: {nr} reading, want 40"
    assert total == 320, f"total {total}, want 320"

    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=1)
    print(f"wrote {out}: {os.path.getsize(out)} bytes, {total} questions")


if __name__ == "__main__":
    pdf = sys.argv[1] if len(sys.argv) > 1 else PDF
    out = sys.argv[2] if len(sys.argv) > 2 else OUT
    build(pdf, out)
