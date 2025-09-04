// file: SurveyGraphTest64.kt
package com.negi.nav3

import org.junit.Assert.*
import org.junit.Test

class SurveyGraphTestUI {

    // ---------- Helpers ----------
    fun sampleBuild(): SurveyGraph {

        val nEnd = Node(id = END, defaultNext = END, text = "終了")

        val n3  = Node(id = "Q3", text = "Q3 (まとめ)", defaultNext = END)
        val nF1 = Node(id = "Q2_A1", text = "Q2 - A のフォローアップ1", defaultNext = "Q3")
        val nF2 = Node(id = "Q2_A2", text = "Q2 - A のフォローアップ2", defaultNext = "Q3")
        val nB  = Node(id = "Q2_B1", text = "Q2 - B のフォローアップ1", defaultNext = "Q3")
        val nR  = Node(id = "Q2_C1", text = "Q2 - C のフォローアップ1", defaultNext = "Q3")

        val n2 = Node(
            id = "Q2",
            text = "複数選択可の質問（最大2つまで）",
            options = mapOf(
                "A" to listOf("Q2_A1", "Q2_A2"),
                "B" to listOf("Q2_B1"),
                "C" to listOf("Q2_C1")
            ),
            minSelect = 1,
            maxSelect = 2,
            allowMulti = true,
            optionOrder = listOf("B", "A", "C"),
            defaultNext = "Q3"
        )

        val n1single = Node(
            id = "Q1",
            text = "単一選択の質問 (Yes -> 続行, No -> 終了)",
            options = mapOf(
                "Yes" to listOf("Q2"),
                "No"  to listOf(END)
            ),
            minSelect = 1,
            allowMulti = false
        )

        val nStart = Node(id = START, text = "最初の画面", defaultNext = "Q1")

        val nodes = listOf(nStart, n1single, n2, n3, nF1, nF2, nB, nR, nEnd).associateBy { it.id }
        return SurveyGraph(startId = START, nodes = nodes)
    }

        /**
         * 再現シナリオ:
         * 1. START -> Q1
         * 2. Q1 = Yes -> Q2
         * 3. Q2 = [B, C] -> enqueue Q2_B1, Q2_C1
         * 4. advance -> Q2_B1, advance -> Q2_C1
         * 5. onBack() x2 -> 戻って Q1
         * 6. advance -> Q2
         * 7. Q2 = [A, C] に変更 -> enqueue Q2_A1,Q2_A2,Q2_C1 （**Q2_B1 は残らない**）
         */
    @Test
    fun testBackReplacesOldOriginChildren() {
        val g = sampleBuild()

        // start -> Q1
        val first = g.advanceToNext()
        assertEquals("Q1", first)

        // Q1 -> Yes -> Q2
        g.updateSingleAnswer("Q1", "Yes")
        val toQ2 = g.advanceToNext()
        assertEquals("Q2", toQ2)

        // Q2 select B,C
        g.updateMultiAnswer("Q2", listOf("B", "C"))

        // advance through enqueued follow-ups: expect B1 then C1
        val f1 = g.advanceToNext()
        assertEquals("Q2_B1", f1)
        val f2 = g.advanceToNext()
        assertEquals("Q2_C1", f2)

        // back twice to return to Q1 (history snapshots created at each advance)
        assertTrue(g.onBack())
        assertTrue(g.onBack())

        // now at Q1
        assertEquals("Q1", g.currentNodeId)

        // forward to Q2 again
        val stepToQ2 = g.advanceToNext()
        assertEquals("Q2", stepToQ2)

        // change answer to A,C
        g.updateMultiAnswer("Q2", listOf("A", "C"))

        // After selecting A,C the pending should NOT contain Q2_B1
        val pending = g.pendingQueueSnapshot().map { it.nodeId }
        assertFalse("古い B1 が残っていないこと", pending.contains("Q2_B1"))

        // The immediate next candidate should be A系のフォローアップ (Q2_A1)
        val peek = g.peekNext()
        assertEquals("Q2_A1", peek)
    }

    /**
     * peekNext() と advanceToNext() の整合性（簡易）
     * - 現在の peekNext を取得し、advanceToNext が同じノードに進むかを検査
     *   （ただし advance は pending を消費するため、テストは currentNodeId が END でない場合にのみ実行）
     */
    @Test
    fun testPeekAndAdvanceConsistency_simple() {
        val g = sampleBuild()

        // START -> Q1
        assertEquals("Q1", g.advanceToNext())

        // Q1 -> Yes -> Q2
        g.updateSingleAnswer("Q1", "Yes")
        assertEquals("Q2", g.advanceToNext())

        // Q2 select A (enqueue Q2_A1, Q2_A2)
        g.updateMultiAnswer("Q2", listOf("A"))

        // peekNext should match advanceToNext result
        val peek = g.peekNext()
        val adv = g.advanceToNext()
        assertEquals("peekNext と advanceToNext が一致すること", peek, adv)
    }

    @Test
    fun testPeekThenAdvanceConsistency_strict() {
        val g = sampleBuild()
        // start -> Q1
        assertEquals("Q1", g.advanceToNext())
        // Q1 -> Yes -> Q2
        g.updateSingleAnswer("Q1", "Yes")
        assertEquals("Q2", g.advanceToNext())

        g.updateMultiAnswer("Q2", listOf("C","B"))
        // peek and immediately advance
        val peek = g.peekNext()
        val adv = g.advanceToNext()
        assertEquals("peek と advance が一致すること", peek, adv)

        val peek2 = g.peekNext()
        val adv2 = g.advanceToNext()
        assertEquals("peek と advance が一致すること", peek, adv)
    }

}
