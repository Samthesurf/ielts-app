#!/usr/bin/env python3
"""Audit table/form-completion prompts in seed JSONs for garbled text.

Flags prompts that look like line-stitching mash from multi-column tables:
numbered blanks from 2+ columns in one prompt, text glued to a blank,
leftover dot-runs, OCR repeat garbage, prompts starting mid-fragment with
no verb anywhere, and cross-column vocabulary mashed without a sentence
boundary. Weaker smells (subject-less fragment, next-item spill) are
reported as NOTICE, not SUSPECT.

Usage: python3 tools/audit_tables.py [seed.json ...]
Default: c20.json c21_full.json c19.json next to the seeds dir.
Exit 0 always; the report is the result.
"""

import json
import os
import re
import sys

SEED_DIR = "/root/ielts-app/app/src/main/assets/seed"
DEFAULTS = [os.path.join(SEED_DIR, f)
            for f in ("c20.json", "c21_full.json", "c19.json")]

TABLE_GROUP = re.compile(
    r"\btable\b|\bform\b|notes?\s+below|summary|\bflow\b|\bchart\b|"
    r"\bdiagram\b|\bmap\b|\blabel\b",
    re.I)

GAPN = re.compile(r"(?<!\d)(\d{1,2})\s*___")
GLUED = re.compile(r"[A-Za-z0-9]___|___[A-Za-z]")
RAW_DOTS = re.compile(r"[.…․]{3,}|\.{4,}")
REPEAT = re.compile(r"([^_\s])\1{4,}")
GAP_FIRST = re.compile(r"^\s*(?:[•\-–*]\s*)?\d{1,2}\s*___")

VERBS = {
    "is", "are", "was", "were", "be", "been", "being", "am",
    "has", "have", "had", "do", "does", "did", "will", "would",
    "can", "could", "shall", "should", "may", "might", "must",
    "need", "needs", "offer", "offers", "cost", "costs", "pay",
    "pays", "paid", "use", "uses", "using", "used", "include",
    "includes", "make", "makes", "made", "take", "takes", "give",
    "gives", "come", "comes", "see", "sees", "range", "ranges",
    "deliver", "delivers", "see", "take", "bring", "know", "like",
    "work", "works", "help", "call", "mean", "show", "give",
}
NONVERB_ING = {
    "morning", "evening", "thing", "things", "something", "nothing",
    "everything", "king", "ring", "sing", "during", "spring", "string",
}
STOP = {
    "with", "from", "that", "this", "these", "those", "than", "then",
    "when", "where", "which", "while", "very", "only", "also", "each",
    "other", "more", "most", "such", "into", "over", "under", "about",
    "good", "only", "quite", "just", "even", "still", "both", "either",
    "and", "for", "the", "you", "your", "our", "their", "its",
}

# Column vocabularies of the C20 grid tables (restaurant T1S1, furniture
# T3S1). A prompt holding words from 2+ of these without a sentence break
# stitches columns together. Harmless elsewhere (no hits, no flag).
LOC = {"station", "street", "greyson", "bow", "cinema", "hotel",
       "near", "next to", "top of a"}
REC = {"keen", "chef", "uses"}
COM = {"expensive", "drink", "deposit", "friendly", "lunch", "portions",
       "menu", "food", "average", "size"}
SETS = (LOC, REC, COM)


def is_verb(w):
    w = w.lower().strip(".,:;!?()\"'“”‘’")
    if w in VERBS:
        return True
    if len(w) > 4 and w not in NONVERB_ING and (
            w.endswith("ed") or w.endswith("ing")):
        return True
    return False


def is_nounish(w):
    s = w.strip(".,:;!?()\"'“”‘’…-–").lower()
    if len(s) < 4 or s in STOP or s in ("___", "…"):
        return False
    if s.isdigit():
        return False
    if is_verb(s):
        return False
    return True


def words(t):
    return re.findall(r"[A-Za-z0-9£$…_]+(?:'[a-z]+)?|___", t)


def gap_sentence(prompt):
    """Text of the sentence holding the first numbered blank."""
    m = GAPN.search(prompt)
    if not m:
        return prompt
    parts = re.split(r"[.!?;•]", prompt)
    pos = 0
    for p in parts:
        if pos <= m.start() < pos + len(p) + 1:
            return p
        pos += len(p) + 1
    return prompt


def cross_col(prompt):
    body = prompt.split(":", 1)[-1] if ":" in prompt else prompt
    hits = []
    for i, vocab in enumerate(SETS):
        for v in vocab:
            m = re.search(r"\b" + re.escape(v) + r"\b", body, re.I)
            if m:
                hits.append((i, m.start()))
                break
    if len({h[0] for h in hits}) < 2:
        return False
    span = body[min(h[1] for h in hits):max(h[1] for h in hits)]
    return ". " not in span and ".\n" not in span


def audit_prompt(prompt):
    """Return (suspects, notices), lists of reason strings."""
    suspects, notices = [], []
    if "___" not in prompt and not RAW_DOTS.search(prompt):
        return suspects, notices
    numbered = bool(GAPN.search(prompt))
    gaps = {int(g) for g in GAPN.findall(prompt)}
    if len(gaps) >= 3:
        suspects.append(f"multi-gap {sorted(gaps)} from 2+ columns")
    elif len(gaps) == 2:
        notices.append(f"two blanks share one prompt {sorted(gaps)}")
    if GLUED.search(prompt):
        suspects.append("text glued to blank (missing space)")
    core = re.sub(r"[.…․]+$", "", prompt)
    if RAW_DOTS.search(core):
        suspects.append("leftover dot-run, blank not formed")
    m = REPEAT.search(core)
    if m:
        suspects.append(f"repeat garbage {m.group(0)[:12]!r}")
    if not numbered:
        return suspects, notices
    bulleted = bool(re.match(r"^[\s•\-–*]+", prompt))
    stripped = re.sub(r"^[\s•\-–*\"“‘'(\[]+", "", prompt)
    if bulleted:
        if GAP_FIRST.match(prompt) and len(prompt) > 40:
            notices.append("starts with the blank, head may be lost")
    elif GAP_FIRST.match(prompt) and len(prompt) > 40:
        notices.append("starts with the blank, head may be lost")
    low = stripped[:1].islower()
    if (not bulleted and low and len(gaps) == 1 and len(prompt) > 30
            and len(words(prompt)) > 8
            and not re.search(r"[.!?]", prompt) and not any(
                is_verb(w) for w in words(prompt))):
        suspects.append("lowercase fragment with no verb (stitched tail)")
    if cross_col(prompt):
        suspects.append("cross-column vocabulary mashed, no break")
    sent = gap_sentence(prompt)
    before = sent.split("___")[0] if "___" in sent else sent
    bw = [w for w in words(before) if w not in ("___", "…")]
    alpha = [w for w in bw if re.search(r"[A-Za-z]", w)]
    if (len(words(sent)) > 3 and len(alpha) >= 3
            and not any(is_nounish(w) for w in bw)):
        first = next((w for w in words(sent)
                      if re.search(r"[A-Za-z0-9]", w)), "")
        if not is_verb(first):
            notices.append("fragment, no subject before blank")
    gm = GAPN.search(prompt)
    tail = prompt[gm.end():] if gm else ""
    if "•" in tail or re.search(r"\s-\s+[A-Z]", tail):
        notices.append("possible next-item spill after blank")
    return suspects, notices


def audit_file(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    book = data.get("book", os.path.basename(path))
    groups_checked = prompts_checked = 0
    suspects, notices = [], []
    for t in data.get("tests", []):
        for g in t.get("groups", []):
            instr = g.get("instructions", "")
            qs = g.get("questions", [])
            if not (TABLE_GROUP.search(instr)
                    or (g.get("section", "").startswith("Listening")
                        and any("___" in q.get("prompt", "") for q in qs))):
                continue
            groups_checked += 1
            for q in qs:
                p = q.get("prompt", "")
                if "___" not in p and not RAW_DOTS.search(p):
                    continue
                prompts_checked += 1
                s, n = audit_prompt(p)
                for r in s:
                    suspects.append((t.get("testNo"), g.get("section"),
                                     q.get("qNo"), r, p[:110]))
                for r in n:
                    notices.append((t.get("testNo"), g.get("section"),
                                    q.get("qNo"), r, p[:110]))
    return book, groups_checked, prompts_checked, suspects, notices


def main(paths):
    total_sus = 0
    for path in paths:
        if not os.path.exists(path):
            print(f"== {path}: MISSING")
            continue
        book, ng, nq, sus, nots = audit_file(path)
        total_sus += len(sus)
        print(f"== {path} (book {book}): "
              f"{ng} table/form groups, {nq} prompts checked")
        for t, sec, q, reason, snippet in sus:
            print(f"SUSPECT {book} T{t} {sec} Q{q}: {reason} :: {snippet!r}")
        for t, sec, q, reason, snippet in nots:
            print(f"notice  {book} T{t} {sec} Q{q}: {reason} :: {snippet!r}")
        print(f"-- {book}: {len(sus)} suspect, {len(nots)} notice")
    print(f"TOTAL suspect: {total_sus}")


if __name__ == "__main__":
    main(sys.argv[1:] if len(sys.argv) > 1 else DEFAULTS)
