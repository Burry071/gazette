package com.gazette.pipeline

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Replays the archived desktop run (results_v3: stored votes, results_v4:
// post-reconciliation decisions from scripts/reconcile.js) through the Kotlin
// port. Every row must land on the identical decision — this is the parity bar
// for the port, verified with zero model calls.
class ReconcileParityTest {

    private fun resourceLines(name: String): List<JSONObject> {
        val stream = javaClass.getResourceAsStream("/$name")
            ?: error("$name missing from test resources")
        return stream.readBytes().toString(Charsets.UTF_8)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { JSONObject(it) }
    }

    private fun stringVotes(row: JSONObject, key: String): List<String?> {
        val arr = row.getJSONArray(key)
        return (0 until arr.length()).map { if (arr.isNull(it)) null else arr.getString(it) }
    }

    private fun doubleVotes(row: JSONObject, key: String): List<Double?> {
        val arr = row.getJSONArray(key)
        return (0 until arr.length()).map { if (arr.isNull(it)) null else arr.getDouble(it) }
    }

    private fun nullableAmount(obj: JSONObject, key: String): Double? =
        if (obj.isNull(key)) null else obj.getDouble(key)

    @Test
    fun kotlinReconciler_reproducesArchivedV4RowForRow() {
        val v3 = resourceLines("results_v3_gemma3_1b.jsonl")
        val v4 = resourceLines("results_v4_gemma3_1b.jsonl")
        assertEquals(v3.size, v4.size)
        assertEquals(30, v4.size)

        val failures = mutableListOf<String>()
        for (i in v4.indices) {
            val votes = v3[i]
            val expected = v4[i]
            val got = Reconciler.reconcile(
                text = votes.getString("text"),
                gated = votes.getBoolean("gated"),
                clsVotes = stringVotes(votes, "clsVotes").filterNotNull(),
                amtVotes = doubleVotes(votes, "amtVotes"),
                dirVotes = stringVotes(votes, "dirVotes"),
            )
            val want = expected.getJSONObject("want")
            val expectedGot = expected.getJSONObject("got")

            val transactionOk = got.transaction == expectedGot.getBoolean("transaction")
            val amountOk = got.amount == nullableAmount(expectedGot, "amount")
            val directionOk = got.direction == if (expectedGot.isNull("direction")) null else expectedGot.getString("direction")
            val pass = got.transaction == want.getBoolean("transaction") &&
                (!want.getBoolean("transaction") ||
                    (got.amount == nullableAmount(want, "amount") && got.direction == if (want.isNull("direction")) null else want.getString("direction")))
            val passOk = pass == expected.getBoolean("pass")

            if (!(transactionOk && amountOk && directionOk && passOk)) {
                failures += "row $i \"${votes.getString("text").take(50)}\": " +
                    "tx=$transactionOk amt=$amountOk dir=$directionOk pass=$passOk got=$got expected=$expectedGot"
            }
        }
        assertTrue("parity failures:\n" + failures.joinToString("\n"), failures.isEmpty())

        val real = v4.filter { !it.getBoolean("synth") }
        val synth = v4.filter { it.getBoolean("synth") }
        assertEquals(28, real.size)
        assertEquals(28, real.count { it.getBoolean("pass") })
        assertEquals(2, synth.size)
        assertEquals(2, synth.count { it.getBoolean("pass") })
        assertEquals(18, real.count { !it.getJSONObject("want").getBoolean("transaction") })
    }
}
