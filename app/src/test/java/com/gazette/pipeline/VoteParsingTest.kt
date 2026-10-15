package com.gazette.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteParsingTest {

    @Test
    fun parseClass_firstOfPaymentOrOtherWins() {
        assertEquals("P", VoteParsing.parseClass("PAYMENT"))
        assertEquals("P", VoteParsing.parseClass("payment."))
        assertEquals("O", VoteParsing.parseClass("OTHER"))
        assertEquals("O", VoteParsing.parseClass("Other\nPayment"))
        assertEquals("P", VoteParsing.parseClass("Payment, but other details follow"))
        assertEquals("O", VoteParsing.parseClass("banana"))
    }

    @Test
    fun parseAmount_handlesCommasAndJunk() {
        assertEquals(2500.0, VoteParsing.parseAmount("AMOUNT: 2,500.00\nDIRECTION: received"))
        assertEquals(700.0, VoteParsing.parseAmount("AMOUNT: 700\nDIRECTION: sent"))
        assertEquals(70.0, VoteParsing.parseAmount("amount: 70.00"))
        assertEquals(1.2, VoteParsing.parseAmount("AMOUNT: 1.2.3"))
        assertNull(VoteParsing.parseAmount("DIRECTION: sent"))
    }

    @Test
    fun parseDir_synonymSets() {
        assertEquals("sent", VoteParsing.parseDir("DIRECTION: sent"))
        assertEquals("received", VoteParsing.parseDir("direction: Received"))
        assertEquals("sent", VoteParsing.parseDir("DIRECTION: debited"))
        assertNull(VoteParsing.parseDir("DIRECTION: maybe"))
        assertNull(VoteParsing.parseDir("AMOUNT: 5"))
    }

    @Test
    fun majority_tieReturnsFallback() {
        assertEquals("P", VoteParsing.majority(listOf("P", "P", "O"), "O"))
        assertEquals("X", VoteParsing.majority(listOf("P", "O"), "X"))
        assertEquals("X", VoteParsing.majority(listOf("P", "P", "O", "O"), "X"))
        assertNull(VoteParsing.majority(emptyList(), null))
    }

    @Test
    fun gate_requiresCurrencyAmount() {
        assertTrue(Gate.hasAmount("Rs 500.00 received from JazzCash"))
        assertTrue(Gate.hasAmount("an amount of Rs. 700 has been sent"))
        assertTrue(Gate.hasAmount("Use 3.5GB for PKR 2"))
        assertFalse(Gate.hasAmount("Your OTP code for Comedy Corner is 0000"))
        assertFalse(Gate.hasAmount("157 messages from 2 chats"))
    }

    @Test
    fun reconcile_otpVetoWinsEvenWithAmount() {
        val r = Reconciler.reconcile(
            "Rs 500 paid, your OTP is 1234",
            gated = false,
            clsVotes = listOf("P", "P", "P"),
            amtVotes = listOf(500.0, 500.0, 500.0),
            dirVotes = listOf("sent", "sent", "sent"),
        )
        assertFalse(r.transaction)
        assertTrue(r.otpVeto)
        assertNull(r.amount)
    }

    @Test
    fun reconcile_eventVerbRescuesAndOverridesDirection() {
        val r = Reconciler.reconcile(
            "Rs 500.00 received from ACME",
            gated = false,
            clsVotes = listOf("O", "O", "O"),
            amtVotes = emptyList(),
            dirVotes = emptyList(),
        )
        assertTrue(r.transaction)
        assertTrue(r.eventRescue)
        assertEquals("received", r.direction)
        assertEquals(500.0, r.amount)
    }

    @Test
    fun reconcile_balanceStateVetoedWithoutEventVerb() {
        val r = Reconciler.reconcile(
            "Rs 6,023.37 is your available balance on JazzCash",
            gated = false,
            clsVotes = listOf("P", "P", "O"),
            amtVotes = listOf(6023.37, 6023.37, null),
            dirVotes = listOf("received", "received", null),
        )
        assertFalse(r.transaction)
        assertTrue(r.balanceVeto)
        assertNull(r.amount)
    }

    @Test
    fun pipeline_gateSkipsEngine() {
        var calls = 0
        val engine = object : LlmEngine {
            override suspend fun generate(prompt: String, maxNewTokens: Int, temperature: Double): String {
                calls++
                return "PAYMENT"
            }
        }
        val pipeline = Pipeline(engine)
        val r = kotlinx.coroutines.runBlocking { pipeline.process("157 messages from 2 chats") }
        assertEquals(0, calls)
        assertFalse(r.transaction)
        assertTrue(r.gated)
    }

    @Test
    fun pipeline_votesFlowThroughToReconciledOutput() {
        val engine = object : LlmEngine {
            var n = 0
            override suspend fun generate(prompt: String, maxNewTokens: Int, temperature: Double): String {
                n++
                return if (n <= 3) "PAYMENT" else "AMOUNT: 2,500.00\nDIRECTION: received"
            }
        }
        val pipeline = Pipeline(engine)
        val r = kotlinx.coroutines.runBlocking {
            pipeline.process("Rs 2,500.00 received from ACME TECH SOLUTIONS on 29/08/2026.")
        }
        assertTrue(r.transaction)
        assertEquals(2500.0, r.amount)
        assertEquals("received", r.direction)
    }
}
