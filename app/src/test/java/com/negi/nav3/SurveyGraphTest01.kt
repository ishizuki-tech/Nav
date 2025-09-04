// file: SurveyGraphTest128Extra.kt
package com.negi.nav3

import android.util.Log
import org.junit.*
import org.junit.Assert.*

class SurveyGraphTest128Extra {

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

    private fun newG() = sampleBuild()

    private fun reachQ2(g: SurveyGraph) {
        g.advanceToNext()                 // Start -> Q1
        g.updateSingleAnswer("Q1","Yes")  // enqueue Q2
        g.advanceToNext()                 // Q1 -> Q2
        assertEquals("Q2", g.currentNodeId)
    }

    private fun pqIds(g: SurveyGraph): List<String> = g.pendingQueueSnapshot().map { it.nodeId }

    private fun setMulti(g: SurveyGraph, vararg keys: String, replaceQueued: Boolean = true) {
        g.updateMultiAnswer("Q2", keys.toList(), replaceQueued = replaceQueued)
    }

    // ====================== 追加テスト t65〜t128 ======================

    @Test fun t65_peekNext_does_not_consume() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        val p1 = g.peekNext()
        val p2 = g.peekNext()
        assertEquals("Q2_A1", p1)
        assertEquals(p1, p2)
        assertEquals(listOf("Q2_A1","Q2_A2"), pqIds(g)) // 未消費
    }

    @Test fun t66_advanceToNext_matches_initial_peekNext() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B")
        assertEquals(g.peekNext(), g.advanceToNext())
    }

    @Test fun t67_enqueue_unknown_returns_false() {
        val g = newG()
        assertFalse(g.enqueue("NOPE"))
    }

    @Test fun t68_updateFreeText_overwrites_value() {
        val g = newG()
        g.updateFreeText("Q3","first")
        g.updateFreeText("Q3","second")
        assertEquals("second", g.getTextAnswer("Q3"))
    }

    @Test fun t69_replaceQueued_false_unions_children() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        setMulti(g,"B", replaceQueued = false)
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_B1"), pqIds(g))
    }

    @Test fun t70_remove_branch_clears_visited() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.advanceToNext() // -> Q2_A1
        assertTrue(g.visitedSnapshot().contains("Q2_A1"))
        setMulti(g,"B") // remove A subtree
        assertFalse(g.visitedSnapshot().contains("Q2_A1"))
    }

    @Test fun t71_back_multiple_levels_restores_state() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.advanceToNext() // -> Q2_A1
        g.advanceToNext() // -> Q2_A2
        assertTrue(g.onBack()) // back to after first advance
        assertEquals(listOf("Q2_A1","Q2_A2"), listOf("Q2_A1","Q2_A2")) // sanity
        assertTrue(g.onBack()) // back to Q2 snapshot
        assertEquals("Q2", g.currentNodeId)
        assertEquals(listOf("Q2_A1","Q2_A2"), pqIds(g))
    }

    @Test fun t72_snapshot_restore_after_complex_answers() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","C")
        g.updateFreeText("Q2_A2","memo")
        val js = g.snapshotJson()
        val g2 = newG(); g2.restoreFromJson(js)
        assertEquals(g.pendingQueueSnapshot(), g2.pendingQueueSnapshot())
        assertEquals("memo", g2.getTextAnswer("Q2_A2"))
        assertEquals(listOf("A","C"), g2.getChoiceAnswer("Q2"))
    }

    @Test fun t73_peekNextFrom_Q2_ignores_queue_uses_default_Q3() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        assertEquals("Q3", g.peekNextFrom("Q2"))
    }

    @Test fun t74_canGoNext_true_with_pending_children() {
        val g = newG(); reachQ2(g)
        setMulti(g,"C")
        assertTrue(g.canGoNext())
    }

    @Test fun t75_move_to_END_after_Q1_no() {
        val g = newG()
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","No")
        assertEquals("End", g.advanceToNext()) // move to END
        assertEquals("End", g.currentNodeId)
    }

    @Test fun t76_END_not_in_visited() {
        val g = newG()
        g.advanceToNext(); g.updateSingleAnswer("Q1","No"); g.advanceToNext()
        assertFalse(g.visitedSnapshot().contains("End"))
    }

    @Test fun t77_clear_single_answer_removes_origin_and_pending() {
        val g = newG()
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","Yes") // enqueue Q2
        g.updateSingleAnswer("Q1", null) // clear (replaceQueued=true default)
        assertFalse(g.originMapSnapshot().containsKey("Q1"))
        assertTrue(g.pendingQueueSnapshot().isEmpty())
    }

    @Test fun t78_multi_unknown_key_ignored_children_none() {
        val g = newG(); reachQ2(g)
        setMulti(g,"Z") // minSelect=1 は満たすが子は無い
        assertTrue(pqIds(g).isEmpty())
        assertEquals(listOf("Z"), g.getChoiceAnswer("Q2")) // 回答は保存
    }

    @Test(expected = IllegalArgumentException::class)
    fun t79_multi_exceeds_maxSelect_throws() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","B","C") // maxSelect=2
    }

    @Test(expected = IllegalArgumentException::class)
    fun t80_multi_below_minSelect_throws() {
        val g = newG(); reachQ2(g)
        g.updateMultiAnswer("Q2", emptyList()) // minSelect=1
    }

    @Test fun t81_reordering_keys_still_ABC() {
        val g = newG(); reachQ2(g)
        // 2つ選択に変更（Q2.maxSelect == 2 の前提を満たす）
        setMulti(g,"C","A")
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_C1"), pqIds(g))
    }

    @Test fun t82_start_peek_and_advance_use_defaultNext_Q1() {
        val g = newG()
        assertEquals("Q1", g.peekNext())
        assertEquals("Q1", g.advanceToNext())
    }

    @Test fun t83_history_trim_exact_when_small_limit() {
        // maxHistory=2 のグラフを用意
        val base = sampleBuild()
        val nodes = listOf(
            base.getNode(START), base.getNode("Q1"), base.getNode("Q2"),
            base.getNode("Q3"), base.getNode("Q2_A1"), base.getNode("Q2_A2"),
            base.getNode("Q2_B1"), base.getNode("Q2_C1"), base.getNode(END)
        ).associateBy { it.id }
        val g = SurveyGraph(startId = START, nodes = nodes, maxHistory = 2)

        g.advanceToNext()                 // Start -> Q1
        g.updateSingleAnswer("Q1","Yes")
        g.advanceToNext()                 // Q1 -> Q2
        setMulti(g, "A")
        g.advanceToNext()                 // Q2 -> Q2_A1

        // maxHistory=2 なので「2回だけ戻れる」
        assertTrue(g.onBack())            // Q2_A1 -> （スナップショット2）=> Q2
        assertEquals("Q2", g.currentNodeId)

        assertTrue(g.onBack())            // Q2 -> （スナップショット1）=> Q1
        assertEquals("Q1", g.currentNodeId)

        // 3回目は戻れない（Start のスナップショットはトリム済み）
        assertFalse(g.onBack())
    }

    @Test fun t84_onBack_restores_pending_order_after_two_advances() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.advanceToNext() // -> Q2_A1
        g.advanceToNext() // -> Q2_A2

        // 一段戻ると pending は [Q2_A2]（実装どおり）
        assertTrue(g.onBack())
        assertEquals(listOf("Q2_A2"), pqIds(g))

        // さらにもう一段戻ると元の [Q2_A1, Q2_A2] に戻る
        assertTrue(g.onBack())
        assertEquals(listOf("Q2_A1","Q2_A2"), pqIds(g))
        assertEquals("Q2", g.currentNodeId)
    }

    @Test fun t85_enqueue_duplicate_unknown_stays_false() {
        val g = newG()
        assertFalse(g.enqueue("???"))
        assertFalse(g.enqueue("???"))
    }

    @Test fun t86_external_cleared_after_consumption_allows_reenqueue() {
        val g = newG(); reachQ2(g)
        assertTrue(g.enqueue("Q3"))
        assertFalse(g.enqueue("Q3"))
        // 消費
        setMulti(g,"A"); g.advanceToNext(); g.advanceToNext(); g.advanceToNext() // -> Q3
        // 再度 enqueue できるはず
        assertTrue(g.enqueue("Q3"))
    }

    @Test fun t87_getNextFrom_START_is_Q1() {
        val g = newG()
        assertEquals("Q1", g.getNextFrom(START))
    }

    @Test fun t88_repeat_setMulti_after_visiting_keeps_remaining() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.advanceToNext() // consume A1
        setMulti(g,"A")   // delta なし
        assertEquals(listOf("Q2_A2"), pqIds(g))
    }

    @Test fun t89_originMapSnapshot_order_matches_children_order() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","C")
        val m = g.originMapSnapshot()
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_C1"), m["Q2"])
    }

    @Test fun t90_pending_entries_have_origin_Q2_when_from_Q2() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B")
        val entries = g.pendingQueueSnapshot()
        assertTrue(entries.all { it.origin == "Q2" })
    }

    @Test fun t91_external_entries_have_null_origin() {
        val g = newG()
        assertTrue(g.enqueue("Q3"))
        assertTrue(g.pendingQueueSnapshot().all { it.origin == null })
    }

    @Test fun t92_getNextFrom_Q3_is_END() {
        val g = newG()
        assertEquals("End", g.getNextFrom("Q3"))
    }

    @Test fun t93_peekNextFrom_unknown_id_is_END() {
        val g = newG()
        assertEquals("End", g.peekNextFrom("NOPE"))
    }

    @Test fun t94_single_yes_sets_originMap_Q1_to_Q2() {
        val g = newG()
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","Yes")
        val m = g.originMapSnapshot()
        assertEquals(listOf("Q2"), m["Q1"])
    }

    @Test fun t95_updateFreeText_long_string_preserved() {
        val g = newG()
        val long = "x".repeat(300)
        g.updateFreeText("Q3", long)
        assertEquals(300, g.getTextAnswer("Q3")!!.length)
    }

    @Test fun t96_clearAll_clears_visited() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B"); g.advanceToNext()
        assertTrue(g.visitedSnapshot().isNotEmpty())
        g.clearAll()
        assertTrue(g.visitedSnapshot().isEmpty())
    }

    @Test fun t97_isFinished_false_on_Q2_without_selection() {
        val g = newG(); reachQ2(g)
        assertFalse(g.isFinished())
    }

    @Test fun t98_isFinished_true_on_Q3() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B")
        g.advanceToNext() // -> Q2_B1
        g.advanceToNext() // -> Q3
        assertTrue(g.isFinished())
    }

    @Test fun t99_history_chain_records_previous_nodes() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.advanceToNext() // A1
        val hist = g.getHistoryNodeIds()
        assertTrue(hist.isNotEmpty())
        assertEquals("Q2", hist.last())
    }

    @Test fun t100_history_is_copy() {
        val g = newG(); reachQ2(g)
        val h1 = g.getHistoryNodeIds().toMutableList()
        h1.add("X")
        assertFalse(g.getHistoryNodeIds().contains("X"))
    }

    @Test fun t101_updateSingle_unknown_key_results_in_no_children() {
        val g = newG(); g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","Maybe") // not in options
        assertTrue(pqIds(g).isEmpty())
        assertTrue(g.originMapSnapshot()["Q1"]?.isEmpty() ?: true)
    }

    @Test fun t102_clear_single_answer_removes_pending_when_replaceQueued_true() {
        val g = newG()
        g.advanceToNext(); g.updateSingleAnswer("Q1","Yes")
        g.updateSingleAnswer("Q1", null, replaceQueued = true)
        assertTrue(g.pendingQueueSnapshot().isEmpty())
    }

    @Test fun t103_clear_single_answer_keeps_pending_when_replaceQueued_false() {
        val g = newG()
        g.advanceToNext(); g.updateSingleAnswer("Q1","Yes")
        g.updateSingleAnswer("Q1", null, replaceQueued = false)
        assertEquals(listOf("Q2"), pqIds(g))
    }

    @Test fun t104_clear_one_origin_does_not_affect_others() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A"); g.enqueue("Q3")
        g.updateSingleAnswer("Q1", null, replaceQueued = false) // unrelated
        assertTrue(pqIds(g).containsAll(listOf("Q2_A1","Q2_A2","Q3")))
    }

    @Test fun t105_advance_from_END_stays_END() {
        val g = newG()
        g.advanceToNext(); g.updateSingleAnswer("Q1","No"); g.advanceToNext()
        assertEquals("End", g.advanceToNext()) // still End
        assertEquals("End", g.currentNodeId)
    }

    @Test fun t106_getNextFrom_Q1_after_yes_is_Q2() {
        val g = newG()
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","Yes")
        assertEquals("Q2", g.getNextFrom("Q1"))
    }

    @Test fun t107_getNextFrom_Q2_after_A_is_Q2_A1() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        assertEquals("Q2_A1", g.getNextFrom("Q2"))
    }

    @Test fun t108_peekNext_on_Q1_after_yes_is_Q2() {
        val g = newG()
        g.advanceToNext(); g.updateSingleAnswer("Q1","Yes")
        assertEquals("Q2", g.peekNext())
    }

    @Test fun t109_multi_duplicate_keys_treated_once() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","A")
        assertEquals(listOf("Q2_A1","Q2_A2"), pqIds(g))
    }

    @Test fun t110_multi_lowercase_not_matched() {
        val g = newG(); reachQ2(g)
        setMulti(g,"a")
        assertTrue(pqIds(g).isEmpty())
    }

    @Test fun t111_originMap_contains_only_present_origins() {
        val g = newG(); reachQ2(g)
        setMulti(g,"C")
        val m = g.originMapSnapshot()
        assertEquals(setOf("Q2"), m.keys)
    }

    @Test fun t112_advance_consumes_pending_entry() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B")
        assertEquals("Q2_B1", g.peekNext())
        g.advanceToNext()
        assertFalse(pqIds(g).contains("Q2_B1"))
    }

    @Test fun t113_enqueue_END_is_false() {
        val g = newG()
        assertFalse(g.enqueue(END))
    }

    @Test fun t114_enqueue_unknown_is_false() {
        val g = newG()
        assertFalse(g.enqueue("X_X"))
    }

    @Test fun t115_originMap_size_matches_children_count() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","B")
        val m = g.originMapSnapshot()
        assertEquals(3, m["Q2"]!!.size) // A1,A2,B1
    }

    @Test fun t116_drain_all_then_isFinished_on_Q3() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.advanceToNext() // A1
        g.advanceToNext() // A2
        assertEquals("Q3", g.advanceToNext())
        assertTrue(g.isFinished())
    }

    @Test fun t117_replaceQueued_false_then_true_prunes_previous() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        setMulti(g,"B", replaceQueued = false)
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_B1"), pqIds(g))
        setMulti(g,"B", replaceQueued = true)
        assertEquals(listOf("Q2_B1"), pqIds(g))
    }

    @Test fun t118_snapshot_preserves_history() {
        val g = newG(); reachQ2(g)
        val h1 = g.getHistoryNodeIds()
        val s = g.snapshot()
        val g2 = newG(); g2.restore(s)
        assertEquals(h1, g2.getHistoryNodeIds())
    }

    @Test fun t119_peekNext_END_when_current_END() {
        val g = newG()
        g.advanceToNext(); g.updateSingleAnswer("Q1","No"); g.advanceToNext()
        assertEquals("End", g.peekNext())
    }

    @Test fun t120_onBack_from_Q1_returns_to_Start() {
        val g = newG()
        g.advanceToNext() // -> Q1 (history has Start)
        assertTrue(g.onBack())
        assertEquals(START, g.currentNodeId)
    }

    @Test fun t121_order_when_add_B_after_A_with_replaceFalse_is_A_then_B() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        setMulti(g,"B", replaceQueued = false)
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_B1"), pqIds(g))
    }

    @Test fun t122_getNextFrom_START_ignores_pending_if_none() {
        val g = newG()
        assertEquals("Q1", g.getNextFrom(START))
    }

    @Test fun t123_peekNext_changes_after_consumption() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        assertEquals("Q2_A1", g.peekNext())
        g.advanceToNext()
        assertEquals("Q2_A2", g.peekNext())
    }

    @Test fun t124_pending_contains_expected_origins_mixed_with_external() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A"); g.enqueue("Q3")
        val entries = g.pendingQueueSnapshot()
        val byOrigin = entries.groupBy { it.origin }
        assertTrue(byOrigin.containsKey("Q2"))
        assertTrue(byOrigin.containsKey(null))
    }

    @Test fun t125_snapshot_restore_keeps_currentNode() {
        val g = newG(); reachQ2(g)
        val js = g.snapshotJson()
        val g2 = newG(); g2.restoreFromJson(js)
        assertEquals(g.currentNodeId, g2.currentNodeId)
    }

    @Test fun t126_change_from_AC_to_B_replaces_children_when_true() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","C")
        setMulti(g,"B", replaceQueued = true)
        assertEquals(listOf("Q2_B1"), pqIds(g))
    }

    @Test fun t127_change_from_A_to_AB_with_false_appends_B() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        setMulti(g,"A","B", replaceQueued = false)
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_B1"), pqIds(g))
    }

    @Test fun t128_after_A1_A2_Q3_path_isFinished_true() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.advanceToNext() // A1
        g.advanceToNext() // A2
        g.advanceToNext() // Q3
        assertTrue(g.isFinished())
    }
}
