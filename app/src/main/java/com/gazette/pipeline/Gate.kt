package com.gazette.pipeline

// Layer 1: structural gate. A real payment notification states an amount;
// anything without one is dropped before the model runs. Mirrors AMOUNT_RE
// in scripts/extract_test_v3.js.
object Gate {
    private val AMOUNT_RE = Regex("""\b(?:rs\.?|pkr)\s*[\d,]""", RegexOption.IGNORE_CASE)

    fun hasAmount(text: String): Boolean = AMOUNT_RE.containsMatchIn(text)
}
