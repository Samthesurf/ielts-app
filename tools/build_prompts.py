#!/usr/bin/env python3
"""Assemble the 5 AI system prompts from vendored skill sources.

Reads skill text from /tmp/ielts-skills, strips DOCX/Desktop/script-path
lines only, prepends an app header, appends authored framework/schema
fragments from tools/prompt_parts/, and fits each file to 18-25 KB
(18432-25600 bytes). Band descriptors are always kept verbatim and full;
only the SKILL workflow body is trimmed to fit.
"""
import os
import re
import sys

SK = "/tmp/ielts-skills"
T1 = f"{SK}/ielts-writing-review-skills/skills/ielts-task1-review"
T2 = f"{SK}/ielts-writing-review-skills/skills/ielts-task2-review"
AW = f"{SK}/Awesome-ielts-claude-skills"
EC = f"{SK}/english-exam-coach/plugins/english-exam-coach/skills/english-exam-coach"
PARTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "prompt_parts")
OUT = "/root/ielts-app/app/src/main/assets/ai"

MIN_BYTES = 18432
MAX_BYTES = 25000  # headroom under the 25600 cap
TARGET = 23500

STRIP_RES = [
    re.compile(r"docx", re.I),
    re.compile(r"desktop", re.I),
    re.compile(r"scripts?/[\w.\-]*\.py", re.I),
    re.compile(r"CLAUDE_SKILL_DIR", re.I),
    re.compile(r"\.ps1\b", re.I),
    re.compile(r"extract_[\w]+\.py|create_[\w]+\.py|find_[\w]+\.py|validate_[\w]+\.py", re.I),
]


def load(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def strip_paths(text):
    kept, dropped = [], 0
    for line in text.splitlines():
        if any(r.search(line) for r in STRIP_RES):
            dropped += 1
            continue
        kept.append(line)
    return "\n".join(kept), dropped


def core_sections(text, keep_heads):
    """Keep only markdown sections whose heading matches keep_heads."""
    out, buf, keep = [], [], False
    for line in text.splitlines():
        m = re.match(r"#{1,3}\s+(.*)", line)
        if m:
            if buf and keep:
                out.extend(buf)
            buf, keep = [line], any(
                h.lower() in m.group(1).lower() for h in keep_heads
            )
        else:
            buf.append(line)
    if buf and keep:
        out.extend(buf)
    return "\n".join(out)


def fit(parts, fixed_bytes):
    """Trim the marked trimmable part (index 0 of parts flagged) to hit TARGET."""
    return parts


def assemble(name, head, fixed_parts, trimmable, tail_parts):
    body = [head] + fixed_parts
    base = sum(len(p.encode("utf-8")) for p in body + tail_parts)
    budget = TARGET - base
    assert budget > 0, f"{name}: fixed parts exceed target ({base} bytes)"
    tb = trimmable.encode("utf-8")
    if len(tb) > budget:
        paras = trimmable.split("\n\n")
        kept, size = [], 0
        for p in paras:
            pb = len(p.encode("utf-8")) + 2
            if size + pb > budget:
                break
            kept.append(p)
            size += pb
        trimmable = "\n\n".join(kept)
    text = "\n\n".join(body + [trimmable] + tail_parts)
    text += "\n"
    n = len(text.encode("utf-8"))
    assert MIN_BYTES <= n <= 25600, f"{name}: {n} bytes out of range"
    return text, n


def main():
    os.makedirs(OUT, exist_ok=True)
    frag = lambda n: load(os.path.join(PARTS, n))

    # ---- shared loads ----
    t1_skill, d1 = strip_paths(load(f"{T1}/SKILL.md"))
    t2_skill, d2 = strip_paths(load(f"{T2}/SKILL.md"))
    t1_desc = load(f"{T1}/references/ielts_task1_band_descriptors.md")
    t2_desc = load(f"{T2}/references/ielts_task2_band_descriptors.md")
    t1_proto, _ = strip_paths(load(f"{T1}/references/visual_analysis_protocol.md"))
    t1_style, _ = strip_paths(load(f"{T1}/references/teacher_style.md"))
    t2_style, _ = strip_paths(load(f"{T2}/references/teacher_style.md"))
    t1_fmt, _ = strip_paths(load(f"{T1}/references/review_format.md"))
    t2_fmt, _ = strip_paths(load(f"{T2}/references/review_format.md"))
    sp_skill, _ = strip_paths(load(f"{AW}/ielts-speaking/SKILL.md"))
    rd_skill, _ = strip_paths(load(f"{AW}/ielts-reading/SKILL.md"))
    li_skill, _ = strip_paths(load(f"{AW}/ielts-listening/SKILL.md"))
    ec_speaking, _ = strip_paths(load(f"{EC}/references/speaking.md"))
    cefr_sp = load(f"{EC}/data/cefr/speaking-descriptors.md")
    router, _ = strip_paths(load(f"{AW}/ielts/SKILL.md"))

    t1_core = core_sections(t1_skill, ["core rule", "scor", "quality", "review plan"])
    t2_core = core_sections(t2_skill, ["core rule", "scor", "quality", "review plan"])

    results = {}

    # ---- task1 ----
    head1 = (
        "# IELTS Academic Writing Task 1 examiner prompt v1 (app asset)\n\n"
        "You are an IELTS Writing examiner marking Academic Task 1. The chart or "
        "diagram image arrives with the user message. The student answer is plain "
        "text. Score strictly against the official Band 5-9 descriptors quoted "
        "verbatim below. Teacher workflow and visual analysis protocol follow. "
        "Where the source mentions Word documents or desktop scripts, ignore it: "
        "this app sends images inline and expects strict JSON back. Word count "
        "rule: flag answers under 150 words with underLength=true but still mark "
        "them; penalise Task Achievement, never refuse."
    )
    text, n = assemble(
        "task1_system.txt", head1,
        [t1_desc, t1_proto, t1_style],
        t1_core + "\n\n" + t1_fmt,
        [frag("writing_schema.txt")],
    )
    results["task1_system.txt"] = (text, n, d1)

    # ---- task2 ----
    head2 = (
        "# IELTS Academic Writing Task 2 examiner prompt v1 (app asset)\n\n"
        "You are an IELTS Writing examiner marking Academic Task 2. Input is the "
        "essay prompt plus the student essay as text. Score strictly against the "
        "official Band 5-9 descriptors quoted verbatim below. Teacher workflow "
        "follows. Where the source mentions Word documents or desktop scripts, "
        "ignore it: this app expects strict JSON back. Word count rule: flag "
        "essays under 250 words with underLength=true but still mark them; "
        "penalise Task Response, never refuse."
    )
    text, n = assemble(
        "task2_system.txt", head2,
        [t2_desc, t2_style],
        t2_core + "\n\n" + t2_fmt,
        [frag("writing_schema.txt")],
    )
    results["task2_system.txt"] = (text, n, d2)

    # ---- speaking ----
    head_sp = (
        "# IELTS Speaking coach prompt v1 (app asset)\n\n"
        "You are an IELTS Speaking examiner and coach. Input is either a recording "
        "transcript plus audio features, or text only. You run Part 1, Part 2 "
        "(cue card, 1 minute prep, 1-2 minute talk, 200-250 words), and Part 3 "
        "follow-ups. Where the source mentions command line scripts or local "
        "config files, ignore it: the app keeps history in its own database. "
        "Coaching method and story bank rules follow."
    )
    text, n = assemble(
        "speaking_system.txt", head_sp,
        [sp_skill, ec_speaking, cefr_sp],
        frag("speaking_extra.txt"),
        [],
    )
    results["speaking_system.txt"] = (text, n, 0)

    # ---- reading ----
    head_rd = (
        "# IELTS Reading coach prompt v1 (app asset)\n\n"
        "You are an IELTS Reading coach. Input is a passage (or its location), "
        "the questions, the student answers, and the correct answers. You explain "
        "every error with a per-question derivation chain and a synonym table, "
        "and you tag the weakest question type. Where the source mentions local "
        "config files, ignore it: the app keeps history in its own database. "
        "Method follows."
    )
    text, n = assemble(
        "reading_system.txt", head_rd,
        [rd_skill, router],
        frag("reading_framework.txt"),
        [],
    )
    results["reading_system.txt"] = (text, n, 0)

    # ---- listening ----
    head_li = (
        "# IELTS Listening coach prompt v1 (app asset)\n\n"
        "You are an IELTS Listening coach. Input is the test section, the "
        "questions, the student answers, the correct answers, and the "
        "audioscript where available. You attribute every error to a concrete "
        "cause in the taxonomy below and you set dictation tasks. Where the "
        "source mentions local config files, ignore it: the app keeps history "
        "in its own database. Method follows."
    )
    text, n = assemble(
        "listening_system.txt", head_li,
        [li_skill, router],
        frag("listening_framework.txt"),
        [],
    )
    results["listening_system.txt"] = (text, n, 0)

    for name, (text, n, dropped) in results.items():
        with open(os.path.join(OUT, name), "w", encoding="utf-8") as f:
            f.write(text)
        print(f"{name}: {n} bytes (stripped {dropped} path lines)")


if __name__ == "__main__":
    sys.exit(main())
