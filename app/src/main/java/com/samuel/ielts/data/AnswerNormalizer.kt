package com.samuel.ielts.data

/**
 * Normalises IELTS answers before comparison with the answer key.
 * Pure Kotlin, no Android dependency, so it is unit-testable on the JVM.
 *
 * Handles: case and trim, "(a)" / "(s)" optional parts, "/" alternates
 * (e.g. "30/thirty", "photos/photographs/pictures"), T/TRUE/F/FALSE/
 * Y/YES/N/NO/NG/NOT GIVEN letter-or-word forms, UK/US spelling pairs
 * (colour/color, organise/organize, centre/center), and number words
 * versus digits (thirty vs 30).
 */
object AnswerNormalizer {

    private val letterMap = mapOf(
        "t" to "true",
        "f" to "false",
        "y" to "yes",
        "n" to "no",
        "ng" to "not given"
    )

    private val ones = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13,
        "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
        "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )

    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    /** Expand "(...)" optional parts into all variants, e.g. "pump(s)". */
    fun expandOptionals(raw: String): List<String> {
        val out = mutableListOf(raw)
        val re = Regex("\\(([^()]*)\\)")
        var i = 0
        while (i < out.size) {
            val m = re.find(out[i])
            if (m == null) {
                i++
                continue
            }
            val cur = out.removeAt(i)
            val inner = m.groupValues[1]
            out.add(i, cur.replaceRange(m.range, ""))
            out.add(i, cur.replaceRange(m.range, inner))
        }
        return out.distinct()
    }

    /** Fold UK/US spelling differences so both sides compare equal. */
    fun foldSpelling(s: String): String {
        var r = s
        r = Regex("our\\b").replace(r, "or")
        r = Regex("ise\\b").replace(r, "ize")
        r = Regex("yse\\b").replace(r, "yze")
        r = Regex("re\\b").replace(r, "er")
        r = Regex("ogue\\b").replace(r, "og")
        return r
    }

    /** Convert a number-word phrase to digits where fully numeric, else unchanged. */
    fun foldNumber(s: String): String {
        val parts = s.split(Regex("\\s+|\\-"))
        var total = 0
        var used = false
        for (p in parts) {
            val o = ones[p]
            val t = tens[p]
            when {
                o != null -> {
                    total += o
                    used = true
                }
                t != null -> {
                    total += t
                    used = true
                }
                p == "hundred" && used -> total *= 100
                else -> return s
            }
        }
        return if (used) total.toString() else s
    }

    fun canonical(s: String): String {
        var r = s.trim().lowercase().replace(Regex("\\s+"), " ")
        r = r.trimEnd('.')
        r = letterMap[r] ?: r
        r = foldNumber(r)
        r = foldSpelling(r)
        return r
    }

    /** True when [given] matches any alternate of the raw key [expected]. */
    fun matches(expected: String, given: String): Boolean {
        val want = canonical(given)
        for (alt in expected.split("/")) {
            for (variant in expandOptionals(alt)) {
                if (canonical(variant) == want) return true
            }
        }
        return false
    }
}
