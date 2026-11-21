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
