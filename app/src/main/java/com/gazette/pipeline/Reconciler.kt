package com.gazette.pipeline

// Layer 5: neuro-symbolic reconciliation over stored votes (no model calls).
// Verbatim port of scripts/reconcile.js — the model proposes, this layer
// settles the linguistically-certain cases and never stores OTP texts.
object Reconciler {

    private val RECV_RE = Regex("""\b(received|credited|deposited)\b""", RegexOption.IGNORE_CASE)
    private val SENT_RE = Regex("""\b(paid|sent|spent|debited|withdrawn|transferred|withdrawal)\b""", RegexOption.IGNORE_CASE)
    private val BALANCE_RE = Regex("""\bbalance\b""", RegexOption.IGNORE_CASE)
    private val OTP_RE = Regex("""\botp\b|one[- ]time\s+(pin|password|code|passcode)""", RegexOption.IGNORE_CASE)
    private val AMOUNT_CAP = Regex("""(?:\brs\.?|\bpkr)\s*([\d][\d,]*\.?\d*)""", RegexOption.IGNORE_CASE)

    data class Reconciled(
        val transaction: Boolean,
        val amount: Double?,
        val direction: String?,
        val gated: Boolean,
        val eventRescue: Boolean,
        val balanceVeto: Boolean,
        val otpVeto: Boolean,
        val directionOverride: Boolean,
        val engineError: Boolean = false,
    )

    data class Action(val hasEvent: Boolean, val dir: String?)

    fun actionFrom(text: String): Action {
        val recv = RECV_RE.containsMatchIn(text)
        val sent = SENT_RE.containsMatchIn(text)
        if (recv == sent) return Action(recv && sent, null)
        return Action(true, if (recv) "received" else "sent")
    }

    fun amountFrom(text: String): Double? {
        val m = AMOUNT_CAP.find(text) ?: return null
        return VoteParsing.parseAmountNumber(m.groupValues[1].replace(",", ""))
    }

    fun reconcile(
        text: String,
        gated: Boolean,
        clsVotes: List<String>,
        amtVotes: List<Double?>,
        dirVotes: List<String?>,
    ): Reconciled {
        val (hasEvent, verbDir) = actionFrom(text)
        val modelTx = !gated && clsVotes.count { it == "P" } >= 2

        var tx = modelTx
        var rescue = false
        var balanceVeto = false
        var otpVeto = false
        if (!tx && hasEvent) {
            tx = true
            rescue = true
        }
        if (tx && BALANCE_RE.containsMatchIn(text) && !hasEvent) {
            tx = false
            balanceVeto = true
        }
        if (tx && OTP_RE.containsMatchIn(text)) {
            tx = false
            otpVeto = true
        }

        var amount: Double? = null
        var direction: String? = null
        var dirOverride = false
        if (tx) {
            val nonNullAmounts = amtVotes.filterNotNull()
            amount = if (nonNullAmounts.isNotEmpty()) {
                VoteParsing.majority(nonNullAmounts, null)
            } else {
                amountFrom(text)
            }
            if (verbDir != null) {
                direction = verbDir
                val voteDir = VoteParsing.majority(dirVotes.filterNotNull(), null)
                if (voteDir != null && voteDir != verbDir) dirOverride = true
            } else {
                direction = VoteParsing.majority(dirVotes.filterNotNull(), null)
            }
        }
        return Reconciled(tx, amount, direction, gated, rescue, balanceVeto, otpVeto, dirOverride)
    }
}
