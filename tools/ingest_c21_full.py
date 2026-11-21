#!/usr/bin/env python3
"""Ingest Cambridge 21 Listening + Reading (text-selectable PDF) into seed JSON.

Usage: python3 tools/ingest_c21_full.py [pdf-path] [out-path]

Sources everything from the text layer via ``pdftotext -layout``. Test
boundaries and headings are found by regex, never by hardcoded PDF page
numbers (printed Contents numbers are offset from PDF indices).

Output: app/src/main/assets/seed/c21_full.json with the same schema as
c20.json: {"book": "C21", "tests": [{testNo, passages, groups}]}, where
each group holds questions with prompts, choices and the "answer" taken
only from the printed answer keys at the end of the book.

Key format notes (2-column layout, split on wide gaps):
- "21 &22 IN EITHER ORDER" followed by bare "B" / "D" lines means Q21
  and Q22 take B,D in either order; both question records store the
  combined "B,D" string (same convention as ingest_c20.py).
- "cafe I cafe" pipe artifacts normalize to "cafe/cafe".
- A question with no key entry fails the run instead of being invented.
"""

import json
import os
import re
import subprocess
import sys
import tempfile

PDF = "/root/ielts/Cambridge 21.pdf"
OUT = "/root/ielts-app/app/src/main/assets/seed/c21_full.json"

TEST_HEAD = re.compile(r"^Test ([1-4])$")
KEY_TEST = re.compile(r"^\s*TEST ([1-4])\s*$")
KEY_PART = re.compile(r"^\s*(LISTENING|READING)\s*$")
SAMPLE_HEAD = re.compile(r"TEST\s+([1-4]),\s*WRITING\s+TASK\s+([12])")
SEC_HEAD = re.compile(r"^(?:Section|PART)\s+(\d)\s+Questions?\s+(.+)$")
Q_HEAD_FULL = re.compile(
    r"^Questions?\s+(\d+)\s*(?:[-–—]|and)\s*(\d+)\s*(.*)$", re.I)
RANGE_INLINE = re.compile(r"^(\d+)\s*[–—-]\s*(\d+)\s*(.+)$")
NUM_ITEM = re.compile(r"^(\d{1,2})(?:[.)]\s*|\s+)(.+)$")
OPTION = re.compile(r"^([A-I])[.)]\s+(\S.*)$")
OPTION_RAW = re.compile(r"^\s*([A-I])[ \t]{2,}(\S.*)$")
GAP = re.compile(r"(?<!\d)(\d{1,2})(?:\s*[.]?\s*£)?\s*[.·․…_]{2,}")
READ_PASS = re.compile(r"^READING\s+PASSAGE\s+(\d)")
SPEND = re.compile(r"You should spend about 20 minutes")
PASSAGE_BELOW = re.compile(r"^Passage \d+ below\.$")
TASK_HEAD = re.compile(r"WRITING\s+TASK\s+[12]\b")
SPEAK_HEAD = re.compile(r"^\s*SPEAKING\s*$")
DOTS_ONLY = re.compile(r"^[.·․…_~\- ]+$")
DOTS_PREFIX = re.compile(r"^[.·․…_~\- ;:]{2,}\s*(\S.*)?$")
TRAIL_NUM = re.compile(r"^(.*\D)?(\d{1,2})\s*$")
WORD_LIMIT = re.compile(
    r"ONE WORD AND/OR A NUMBER|ONE WORD ONLY|NO MORE THAN (ONE|\d+) WORDS?",
    re.I)
INSTR_RE = re.compile(
    r"^(Choose|Write|Complete the|Match|Look at|NB\b|In boxes|For each|"
    r"You may|Which section|Which paragraph|Reading passage \d has)")
KEY_NUM = re.compile(r"^(\d{1,2})\s*(?:&\s*(\d{1,2}))?\s*(.*)$")
JUNK_HALF = re.compile(
    r"Part |Passage|Questions|Reading|LISTENING|TEST |score|unlikely|"
    r"acceptable|examination|conditions|recommend|spend|improving|"
    r"English|institutions|require|Different|remember|Resource Bank|"
    r"extra explanations|Answer key|If you|you (are|may)|a lot of|"
    r"before you take|study$|scores$|your (score|practice|tests)|"
    r"bank score|Component|tales$",
    re.I)


def extract_text(pdf):
    tmp = tempfile.mktemp(suffix=".txt")
    subprocess.run(["pdftotext", "-layout", pdf, tmp], check=True)
    with open(tmp, encoding="utf-8", errors="replace") as f:
        return f.read().splitlines()


def clean(s):
    return re.sub(r"\s+", " ", s.replace("\x0c", "")).strip()


def split_columns(line):
    """Split a 2-column key line into halves on wide gaps."""
    return [p for p in re.split(r"\s{4,}", line.strip()) if p]


def normalize_answer(raw):
    a = re.sub(r"\s+[I|]\s+", "/", raw.strip())
    a = re.sub(r"\s*/\s*", "/", a)
    return re.sub(r"\s+", " ", a).strip()


def parse_keys(lines):
    """Return ({testNo: {'listening': {q: ans}, 'reading': {q: ans}}},
    problems).

    Operates on RAW lines: pdftotext -layout separates the two columns
    (and sometimes the Q number from its answer) with wide gaps, so
    tokenize on 2+ spaces. A bare-number token opens an entry whose
    answer is the next non-number token; a bare "21 &22" token opens an
    either-order group whose answers are the following bare tokens.
    """
    keys = {}
    cur_test = None
    cur_part = None
    pending = []  # either-order q numbers awaiting bare answers
    group_id = [0]
    current = None  # q number awaiting its answer token on this line

    def cur():
        assert cur_test is not None and cur_part is not None
        return keys[cur_test][cur_part]

    def assign(q, ans, problems):
        d = cur()
        if q in d and d[q] != ans:
            problems.append(
                f"T{cur_test} {cur_part} Q{q}: key conflict "
                f"{d[q]!r} vs {ans!r}")
        d[q] = ans

    problems = []
    def drain_pending(note):
        if pending:
            problems.append(
                f"T{cur_test} {cur_part}: either-order group {pending} "
                f"never completed ({note})")

    for raw in lines:
        if not raw.strip():
            current = None
            continue
        line = clean(raw)
        if SAMPLE_HEAD.search(line) or line == "Audioscripts":
            drain_pending("at samples/audioscripts")
            cur_test, cur_part = None, None
            pending, current = [], None
            continue
        m = KEY_TEST.match(line)
        if m:
            drain_pending("at TEST head")
            cur_test = int(m.group(1))
            keys.setdefault(cur_test, {"listening": {}, "reading": {}})
            cur_part = None
            pending, current = [], None
            continue
        m = KEY_PART.match(line)
        if m and cur_test is not None:
            drain_pending("at part head")
            cur_part = m.group(1).lower()
            pending, current = [], None
            continue
        if cur_test is None or cur_part is None:
            continue
        if re.fullmatch(r"\d+", line):
            continue  # page number
        if line.startswith("•") or line == "-":
            current = None
            continue
        current = None
        for tok in re.split(r"\s{2,}", raw.strip()):
            tc = clean(tok)
            if not tc or tc in ("•", "-"):
                continue
            nospace = re.sub(r"\s+", "", tc)
            if "EITHER" in nospace.upper():
                mnum = re.match(r"^(\d{1,2})\s*&\s*(\d{1,2})", tc)
                if mnum:
                    q1, q2 = int(mnum.group(1)), int(mnum.group(2))
                    if 1 <= q1 <= 40 and 1 <= q2 <= 40:
                        pending = [q1, q2]
                        group_id[0] += 1
                current = None
                continue
            mnum = re.match(r"^(\d{1,2})\s*(?:&\s*(\d{1,2}))?\s*$", tc)
            if mnum:
                q1 = int(mnum.group(1))
                q2 = int(mnum.group(2)) if mnum.group(2) else None
                if q2 and 1 <= q1 <= 40 and 1 <= q2 <= 40:
                    pending = [q1, q2]  # number pair split from EITHER word
                    group_id[0] += 1
                    current = None
                elif 1 <= q1 <= 40 and not mnum.group(2):
                    current = q1  # answer should follow as next token
                else:
                    current = None
                continue
            ment = re.match(r"^(\d{1,2})\s+(\S.*)$", tc)
            if ment and current is None:
                # number and answer glued in one token ("14 C"): the
                # narrow gap inside an entry can be a single space.
                # Skipped when a number is already awaiting its answer
                # ("48 I forty-eight" is Q2's answer, not Q48).
                # number and answer glued in one token ("14 C"): the
                # narrow gap inside an entry can be a single space
                q1 = int(ment.group(1))
                rest = ment.group(2).strip()
                if (1 <= q1 <= 40 and "EITHER" not in
                        re.sub(r"\s+", "", rest).upper()):
                    assign(q1, normalize_answer(rest), problems)
                current = None
                continue
            if JUNK_HALF.search(tc) or re.match(r"^\d+\s*[–—-]", tc):
                current = None
                continue
            if current is not None:
                # the answer token always follows its number on the same
                # line, so it wins over a pending either-order group from
                # the other column sharing this line
                assign(current, normalize_answer(tc), problems)
                current = None
                continue
            if pending:
                if len(tc) > 40:
                    continue
                cur()["__pending__"] = cur().get("__pending__", {})
                cur()["__pending__"].setdefault(group_id[0], []).append(tc)
                got = cur()["__pending__"][group_id[0]]
                if len(got) == len(pending):
                    combo = ",".join(normalize_answer(a) for a in got)
                    for q in pending:
                        assign(q, combo, problems)
                    pending = []
                continue
            # stray token (score-table fragment etc.): ignore
    # drop helper entries
    for t in keys.values():
        for part in t.values():
            part.pop("__pending__", None)
    return keys, problems


class Group:
    def __init__(self, section, passage_title=None):
        self.section = section
        self.passage_title = passage_title
        self.instructions = []
        self.questions = []  # dicts qNo/prompt/choices/source
        self.pending_range = None
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
                         "choices": list(self.options),
                         "source": "stem"})
            else:
                self.instructions.extend(pre)
        else:
            self.instructions.extend(self.pre_lines)
        self.pending_range = None
        self.pre_lines = []
        self.options = []
        self.fresh_range = False


def parse_body(lines):
    """Return {testNo: {'groups': [...], 'passages': [...]}}."""
    tests = {}
    cur_t = None
    cur_group = None
    pending_gap = None  # (qNo, prev-line) for a gap number wrapped
    # onto the next line (dots alone on the following line)
    pending_test: list = [None]  # test number seen but not yet committed: page
    # running heads ("Test N" atop every page) lag/lead the content by
    # up to a page, so a different-number head only takes effect at the
    # next section-level head (PART / passage / LISTENING / ...)
    state = None  # 'listen' | 'readpass' | 'questions' | 'skip'
    cur_pass_sec = None
    cur_pass_title = None
    cur_section = None  # last Listening Section label (survives page heads)
    passage_paras = []
    passage_title = None
    groups = []
    passages = []

    def flush_group():
        nonlocal cur_group
        if cur_group is not None:
            cur_group.flush_shared()
            groups.append(cur_group)
            cur_group = None

    def new_test(t):
        nonlocal groups, passages, cur_t, cur_section, cur_pass_sec
        nonlocal cur_pass_title, state
        flush_group()
        if cur_t is not None and t != cur_t:
            tests[cur_t] = {"groups": groups, "passages": passages}
            groups, passages = [], []
            cur_section = None
            cur_pass_sec = None
            cur_pass_title = None
            state = None
        # same-number repeat (Listening head then Reading head, plus one
        # "Test N" running head per page): keep section state, only the
        # group was flushed above.
        cur_t = t
        pending_test[0] = None

    def commit_test():
        if pending_test[0] is not None and pending_test[0] != cur_t:
            new_test(pending_test[0])
        pending_test[0] = None

    def new_group(section, title=None):
        nonlocal cur_group, cur_section
        flush_group()
        cur_group = Group(section, title)
        if section.startswith("Listening"):
            cur_section = section

    for raw in lines:
        line = clean(raw)
        if not line:
            continue
        if (line == "Audioscripts"
                or line == "Listening and Reading answer keys"
                or SAMPLE_HEAD.search(line)):
            break  # keys/samples handled separately
        if "➔" in line or "©" in line:
            continue  # footer cross-reference junk
        if re.fullmatch(r"\d{1,3}", line):
            continue  # bare page number
        m = TEST_HEAD.match(line)
        if m:
            if cur_t is None or int(m.group(1)) == cur_t:
                new_test(int(m.group(1)))
                cur_group = None
            else:
                pending_test[0] = int(m.group(1))
            pending_gap = None
            continue
        if cur_t is None:
            continue
        if TASK_HEAD.search(line) or SPEAK_HEAD.match(line):
            commit_test()
            flush_group()
            state = "skip"
            continue
        if line in ("LISTENING", "READING"):
            commit_test()
            continue
        if state == "skip":
            m = SEC_HEAD.match(line)
            if m:
                commit_test()
                pending_gap = None
                new_group(f"Listening Section {m.group(1)}")
                state = "questions"
                rng = Q_HEAD_FULL.match(f"Questions {m.group(2)}")
                if rng and int(rng.group(1)) != int(rng.group(2)):
                    assert cur_group is not None
                    cur_group.pending_range = (int(rng.group(1)),
                                               int(rng.group(2)))
                    cur_group.fresh_range = True
                continue
            m = READ_PASS.match(line)
            if m:
                commit_test()
                pending_gap = None
                flush_group()
                state = "readpass"
                cur_pass_sec = f"Reading Passage {m.group(1)}"
                cur_pass_title = None
                passage_paras = []
                passage_title = None
                passages.append({"section": f"Reading Passage {m.group(1)}",
                                 "title": "", "paragraphs": []})
                continue
            continue
        m = READ_PASS.match(line)
        if m:
            commit_test()
            flush_group()
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
            commit_test()
            pending_gap = None
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
            commit_test()
            pending_gap = None
            if cur_group is None:
                sec = cur_pass_sec or cur_section
                if sec is None:
                    sec = ("Listening Section %d" % ((int(m.group(1)) - 1) // 10 + 1)
                           if 1 <= int(m.group(1)) <= 40 and 1 <= int(m.group(2)) <= 40
                           and (int(m.group(1)) - 1) // 10 == (int(m.group(2)) - 1) // 10
                           else "Listening ?")
                    if sec.startswith("Listening Section"):
                        cur_section = sec
                new_group(sec or "Listening ?",
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
            if rest == ".":
                rest = ""
            if q1 != q2:
                cur_group.pending_range = (q1, q2)
                cur_group.fresh_range = True
                if rest:
                    cur_group.pre_lines.append(rest)
            else:
                cur_group.fresh_range = False
                cur_group.questions.append(
                    {"qNo": q1, "prompt": rest, "choices": [],
                     "source": "explicit"})
            state = "questions"
            continue
        if state == "readpass":
            if SPEND.search(line) or PASSAGE_BELOW.match(line):
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
            pending_gap = None
            continue
        if DOTS_ONLY.match(line) and len(line.strip()) >= 2:
            # dots alone on a line: either the wrapped half of a gap
            # whose number ended the previous line, or layout noise
            if pending_gap is not None:
                q, prev = pending_gap
                prompt = clean(re.sub(r"(\d{1,2})\s*$", r"\1 ___", prev))
                cur_group.fresh_range = False
                if not any(x["qNo"] == q for x in cur_group.questions):
                    cur_group.questions.append(
                        {"qNo": q, "prompt": prompt,
                         "choices": list(cur_group.options),
                         "source": "explicit"})
            pending_gap = None
            continue
        mp = DOTS_PREFIX.match(line)
        if mp:
            # line starts with dot/semicolon debris ("; ; keeping time"):
            # wrapped gap dots. With a carried number it completes the
            # question, otherwise it is orphan debris, never prompt text.
            if pending_gap is not None:
                q, prev = pending_gap
                rest = (mp.group(1) or "").strip()
                prompt = clean(
                    re.sub(r"(\d{1,2})\s*$", r"\1 ___", prev)
                    + (" " + rest if rest else ""))
                cur_group.fresh_range = False
                if not any(x["qNo"] == q for x in cur_group.questions):
                    cur_group.questions.append(
                        {"qNo": q, "prompt": prompt,
                         "choices": list(cur_group.options),
                         "source": "explicit"})
                pending_gap = None
                continue
            continue
        pending_gap = None  # any other line voids a carried number
        m = OPTION.match(line)
        if not m:
            # layout gaps are collapsed by clean(): match gap-form options
            # ("A    the ...") on the raw line instead
            m = OPTION_RAW.match(raw.replace("\x0c", ""))
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
            cur_group.flush_shared()
            q1, q2 = int(m.group(1)), int(m.group(2))
            if 1 <= q1 < q2 <= 40 and q2 - q1 <= 6:
                cur_group.pending_range = (q1, q2)
                cur_group.fresh_range = True
                cur_group.pre_lines.append(clean(m.group(3)))
                continue
        gm = GAP.search(line)
        if gm:
            cur_group.fresh_range = False
            prompt = clean(GAP.sub(lambda x: f"{x.group(1)} ___", line))
            for qm in GAP.finditer(line):
                q = int(qm.group(1))
                if not 1 <= q <= 40:
                    continue
                if not any(x["qNo"] == q for x in cur_group.questions):
                    cur_group.questions.append(
                        {"qNo": q, "prompt": prompt,
                         "choices": list(cur_group.options),
                         "source": "explicit"})
            continue
        m = NUM_ITEM.match(line)
        if m:
            cur_group.fresh_range = False
            q = int(m.group(1))
            if not 1 <= q <= 40:
                cur_group.instructions.append(line)
                continue
            rest = clean(m.group(2))
            if len(rest.replace(" ", "")) < 2:
                continue  # page-foot fragment like "24 i", not a question
            tm = re.search(r"(?<!\d)(\d{1,2})\s*$", rest)
            if (tm and 1 <= int(tm.group(1)) <= 40 and tm.group(1) != m.group(1)
                    and GAP.search(line) is None):
                # table row headed by a duration or label ("5 minutes
                # tuning guitars 7"): the leading number is not a
                # question. Carry the trailing gap number for the
                # wrapped dots instead of inventing a question here.
                pending_gap = (int(tm.group(1)), line)
                continue
            prompt = clean(GAP.sub(lambda x: f"{x.group(1)} ___", rest))
            cur_group.questions.append(
                {"qNo": q, "prompt": prompt,
                 "choices": list(cur_group.options),
                 "source": "explicit"})
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
        # carry a trailing bare number: the gap dots may sit alone on
        # the next line (wrapped gap)
        tm = TRAIL_NUM.match(line)
        if tm and 1 <= int(tm.group(2)) <= 40 and len(line) > 8:
            pending_gap = (int(tm.group(2)), line)

    if cur_t is not None:
        flush_group()
        tests[cur_t] = {"groups": groups, "passages": passages}
    return tests


def word_limit_of(instructions):
    m = WORD_LIMIT.search(instructions)
    return m.group(0).upper() if m else ""


def build(pdf, out):
    lines = extract_text(pdf)
    keys, key_problems = parse_keys(lines)
    bodies = parse_body(lines)
    data = {"book": "C21", "tests": []}
    problems = list(key_problems)
    for t in sorted(bodies):
        if t not in keys:
            problems.append(f"test {t}: no keys found")
            continue
        # Dedupe across groups of the same section: a shared stem range
        # materializes questions that a narrower sub-range head later
        # re-parses as explicit per-question lines. Explicit wins; two
        # different explicit prompts for one number fail loudly.
        winners = {}  # (section, qNo) -> question dict
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
                        f"({prev[1]['prompt'][:50]!r} vs {q['prompt'][:50]!r})")
                # identical repeat: keep first
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
            questions = []
            for q in sorted(qs, key=lambda x: x["qNo"]):
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
    assert not problems, f"{len(problems)} problems, keys or conflicts"
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
