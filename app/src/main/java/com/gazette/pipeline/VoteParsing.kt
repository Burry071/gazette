package com.gazette.pipeline

// Vote parsing for model output. Mirrors parseClass/parseAmount/parseDir/majority
// in scripts/extract_test_v3.js.
object VoteParsing {

    fun parseClass(raw: String): String {
        val t = raw.uppercase().replace(Regex("[^A-Z ]"), " ")
        val p = t.indexOf("PAYMENT")
        val o = t.indexOf("OTHER")
        return if (p >= 0 && (o < 0 || p < o)) "P" else "O"
    }

    private val AMOUNT_LINE = Regex("""AMOUNT:\s*([0-9][0-9.,\s]*)""", RegexOption.IGNORE_CASE)
    private val LEADING_NUMBER = Regex("""^[0-9]+(?:\.[0-9]*)?""")

    fun parseAmount(raw: String): Double? {
        val m = AMOUNT_LINE.find(raw) ?: return null
        return parseAmountNumber(m.groupValues[1].replace(Regex("[,\\s]"), ""))
    }

    // JS parseFloat semantics over an already comma/space-stripped digit run.
    fun parseAmountNumber(cleaned: String): Double? {
        val m = LEADING_NUMBER.find(cleaned) ?: return null
        val d = m.value.toDoubleOrNull() ?: return null
        return Math.round(d * 100) / 100.0
    }

    private val DIR_LINE = Regex("""DIRECTION:\s*([A-Za-z]+)""", RegexOption.IGNORE_CASE)
    private val SENT_WORDS = setOf("paid", "spent", "sent", "debit", "debited", "withdrawn", "outgoing", "out")
    private val RECV_WORDS = setOf("received", "credited", "credit", "got", "incoming", "deposit", "deposited", "in")

    fun parseDir(raw: String): String? {
        val m = DIR_LINE.find(raw) ?: return null
        val d = m.groupValues[1].lowercase()
        if (d in SENT_WORDS) return "sent"
        if (d in RECV_WORDS) return "received"
        return null
    }

    // Counts every value including null, first-seen insertion order; a tie
    // returns the fallback. Matches the JS Map-based majority exactly.
    fun <T> majority(votes: List<T?>, fallback: T?): T? {
        val counts = LinkedHashMap<T?, Int>()
        for (v in votes) counts[v] = (counts[v] ?: 0) + 1
        var best: T? = null
        var bestN = 0
        var tie = false
        for ((v, n) in counts) {
            if (n > bestN) {
                best = v
                bestN = n
                tie = false
            } else if (n == bestN) {
                tie = true
            }
        }
        return if (tie) fallback else best
    }
}
