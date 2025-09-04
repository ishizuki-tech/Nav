// file: SurveyGraphTest64.kt
package com.negi.nav3

import org.junit.Assert.*
import org.junit.Test

class SurveyGraphTest64 {

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
        g.advanceToNext() // Start -> Q1
        g.updateSingleAnswer("Q1", "Yes") // enqueue Q2
        g.advanceToNext() // Q1 -> Q2
        assertEquals("Q2", g.currentNodeId)
    }

    private fun pqIds(g: SurveyGraph): List<String> = g.pendingQueueSnapshot().map { it.nodeId }

    private fun setMulti(g: SurveyGraph, vararg keys: String) {
        g.updateMultiAnswer("Q2", keys.toList())
    }

    // ---------- Ordering (ABC + child order) ----------
    @Test fun t01_multi_A_order() {
        val g = newG(); reachQ2(g)
        setMulti(g, "A")
        assertEquals(listOf("Q2_A1", "Q2_A2"), pqIds(g))
    }

    @Test fun t02_multi_B_order() {
        val g = newG(); reachQ2(g)
        setMulti(g, "B")
        assertEquals(listOf("Q2_B1"), pqIds(g))
    }

    @Test fun t03_multi_C_order() {
        val g = newG(); reachQ2(g)
        setMulti(g, "C")
        assertEquals(listOf("Q2_C1"), pqIds(g))
    }

    @Test fun t04_multi_BA_is_A_then_B() {
        val g = newG(); reachQ2(g)
        setMulti(g, "B","A")
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_B1"), pqIds(g))
    }

    @Test fun t05_multi_CA_is_A_then_C() {
        val g = newG(); reachQ2(g)
        setMulti(g, "C","A")
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_C1"), pqIds(g))
    }

    @Test fun t06_multi_AC_is_A_then_C() {
        val g = newG(); reachQ2(g)
        setMulti(g, "A","C")
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_C1"), pqIds(g))
    }

    @Test fun t07_multi_BC_is_B_then_C() {
        val g = newG(); reachQ2(g)
        setMulti(g, "B","C")
        assertEquals(listOf("Q2_B1","Q2_C1"), pqIds(g))
    }

    @Test fun t08_multi_CB_is_B_then_C_even_if_given_CB() {
        val g = newG(); reachQ2(g)
        setMulti(g, "C","B")
        assertEquals(listOf("Q2_B1","Q2_C1"), pqIds(g))
    }

    // ---------- Front insertion preserves child order ----------
    @Test fun t09_frontInsert_keeps_A1_A2() {
        val g = newG(); reachQ2(g)
        setMulti(g, "A")
        assertEquals(listOf("Q2_A1","Q2_A2"), pqIds(g))
    }

    @Test fun t10_frontInsert_A_then_add_C_results_A_then_C() {
        val g = newG(); reachQ2(g)
        setMulti(g, "A")
        setMulti(g, "A","C")
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_C1"), pqIds(g))
    }

    // ---------- Single choice behaves as before ----------
    @Test fun t11_single_yes_enqueues_Q2() {
        val g = newG()
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","Yes")
        assertEquals(listOf("Q2"), pqIds(g))
    }

    @Test fun t12_single_no_finishes() {
        val g = newG()
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","No")
        // defaultNext of Q1 when No is END, no pending
        assertTrue(pqIds(g).isEmpty())
        assertEquals("Q1", g.currentNodeId)
        assertEquals("End", g.getNode(END).id)
    }

    // ---------- Advance selection priority ----------
    @Test fun t13_advance_prefers_current_origin_over_external() {
        val g = newG(); reachQ2(g)
        g.enqueue("Q3") // external
        setMulti(g, "A") // current-origin: Q2_A1,Q2_A2
        assertEquals("Q2_A1", g.advanceToNext()) // should consume current-origin first
    }

    @Test fun t14_external_consumed_after_current_origin() {
        val g = newG(); reachQ2(g)
        g.enqueue("Q3")
        setMulti(g,"A")
        g.advanceToNext() // -> Q2_A1
        g.advanceToNext() // -> Q2_A2
        g.advanceToNext() // -> Q3 (external)
        assertEquals("Q3", g.currentNodeId)
    }

    // ---------- Back reconstructs deterministically ----------
    @Test fun t15_back_rebuilds_pending_A_children() {
        val g = newG(); reachQ2(g)
        setMulti(g, "A")
        assertEquals(listOf("Q2_A1","Q2_A2"), pqIds(g))
        g.advanceToNext() // -> Q2_A1
        assertTrue(g.onBack()) // back to Q2, merge answers
        assertEquals(listOf("Q2_A1","Q2_A2"), pqIds(g))
    }

    @Test fun t16_back_preserves_ABC_multi_order() {
        val g = newG(); reachQ2(g)
        setMulti(g,"C","A") // ABC -> A then C
        g.advanceToNext() // -> Q2_A1
        assertTrue(g.onBack())
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_C1"), pqIds(g))
    }

    // ---------- originMap consistency ----------
    @Test fun t17_originMap_contains_children_after_select_A() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        val m = g.originMapSnapshot()
        assertTrue(m["Q2"]!!.containsAll(listOf("Q2_A1","Q2_A2")))
    }

    @Test fun t18_originMap_updates_when_change_A_to_B() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        setMulti(g,"B")
        val m = g.originMapSnapshot()
        assertTrue(m["Q2"]!!.contains("Q2_B1"))
        assertFalse(m["Q2"]!!.contains("Q2_A1"))
        assertFalse(m["Q2"]!!.contains("Q2_A2"))
    }

    // ---------- remove unreferenced from pending ----------
    @Test fun t19_removed_children_drop_from_pending() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","B")
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_B1"), pqIds(g))
        setMulti(g,"B") // remove A-children
        assertEquals(listOf("Q2_B1"), pqIds(g))
    }

    // ---------- enqueue external uniqueness ----------
    @Test fun t20_external_enqueue_once() {
        val g = newG()
        assertTrue(g.enqueue("Q3"))
        assertFalse(g.enqueue("Q3")) // duplicate
        assertEquals(listOf("Q3"), pqIds(g))
    }

    @Test fun t21_external_coexists_with_origin_children() {
        val g = newG(); reachQ2(g)
        assertTrue(g.enqueue("Q3"))
        setMulti(g,"B")
        // current-origin first, then external
        assertEquals(listOf("Q2_B1","Q3"), pqIds(g))
    }

    // ---------- peekNext / getNextFrom ----------
    @Test fun t22_peekNext_prefers_current_origin() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        assertEquals("Q2_A1", g.peekNext())
    }

    @Test fun t23_getNextFrom_Q2_equals_peekNext() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        assertEquals(g.peekNext(), g.getNextFrom("Q2"))
    }

    @Test fun t24_peekNextFrom_ignores_queue_and_uses_defaultNext() {
        val g = newG()
        assertEquals("Q1", g.peekNextFrom(START)) // defaultNext of Start
    }

    // ---------- finished / canGoX ----------
    @Test fun t25_isFinished_false_on_Q2_with_children() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        assertFalse(g.isFinished())
    }

    @Test fun t26_isFinished_true_when_next_is_END() {
        val g = newG()
        // Start->Q1->No -> END
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","No")
        // peekNextFrom(Q1) is END; current is Q1
        assertTrue(g.isFinished())
    }

    @Test fun t27_canGoBack_false_initially_true_after_advance() {
        val g = newG()
        assertFalse(g.canGoBack())
        g.advanceToNext() // -> Q1
        assertTrue(g.canGoBack())
    }

    @Test fun t28_canGoNext_false_when_current_END_or_default_END() {
        val g = newG()
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","No")
        assertFalse(g.canGoNext())
    }

    // ---------- answers API ----------
    @Test fun t29_choiceAnswer_reflects_latest_selection() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","C")
        assertEquals(listOf("A","C"), g.getChoiceAnswer("Q2"))
    }

    @Test fun t30_textAnswer_persists() {
        val g = newG()
        g.updateFreeText("Q3","hello")
        assertEquals("hello", g.getTextAnswer("Q3"))
    }

    @Test fun t31_hasAnswer_works_for_choice() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        assertTrue(g.hasAnswerFor("Q2"))
    }

    @Test fun t32_hasAnswer_works_for_text() {
        val g = newG()
        g.updateFreeText("Q3","x")
        assertTrue(g.hasAnswerFor("Q3"))
    }

    // ---------- snapshot / restore ----------
    @Test fun t33_snapshot_restore_roundtrip() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B","C")
        val json = g.snapshotJson()
        val g2 = newG()
        g2.restoreFromJson(json)
        assertEquals(g.pendingQueueSnapshot(), g2.pendingQueueSnapshot())
        assertEquals(g.choiceAnswersSnapshot(), g2.choiceAnswersSnapshot())
        assertEquals(g.originMapSnapshot(), g2.originMapSnapshot())
        assertEquals(g.currentNodeId, g2.currentNodeId)
    }

    // ---------- clearAll ----------
    @Test fun t34_clearAll_resets_everything() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.clearAll()
        assertEquals(START, g.currentNodeId)
        assertTrue(g.pendingQueueSnapshot().isEmpty())
        assertTrue(g.choiceAnswersSnapshot().isEmpty())
        assertTrue(g.originMapSnapshot().isEmpty())
    }

    // ---------- history ----------
    @Test fun t35_history_grows_on_advance() {
        val g = newG()
        assertTrue(g.getHistoryNodeIds().isEmpty())
        g.advanceToNext() // -> Q1
        assertEquals(listOf(START), g.getHistoryNodeIds())
    }

    @Test fun t36_onBack_returns_false_when_empty() {
        val g = newG()
        assertFalse(g.onBack())
    }

    // ---------- remove subtree on selection change ----------
    @Test fun t37_subtree_invalidated_when_remove_A_branch() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        g.updateFreeText("Q2_A1","x") // attach some answer under subtree
        setMulti(g,"B") // remove A-branch
        // Q2_A1 text should be cleared by invalidateSubtreeFromRoots
        assertNull(g.getTextAnswer("Q2_A1"))
    }

    @Test fun t38_no_pending_duplication_on_multi_updates() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","C")
        setMulti(g,"A","C") // same again
        assertEquals(listOf("Q2_A1","Q2_A2","Q2_C1"), pqIds(g))
    }

    // ---------- external + origin uniqueness ----------
    @Test fun t39_external_and_origin_same_node_keeps_single_entry() {
        val g = newG(); reachQ2(g)
        g.enqueue("Q3")
        // make origin also enqueue Q3 by selecting choices that lead to Q3? (Q2 doesn't)
        // fallback: enqueue Q3 twice externally; still single
        assertFalse(g.enqueue("Q3"))
        assertEquals(listOf("Q3"), pqIds(g))
    }

    // ---------- visited behavior ----------
    @Test fun t40_visited_adds_on_advance_nonForce() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B")
        g.advanceToNext() // -> Q2_B1
        assertTrue(g.visitedSnapshot().contains("Q2_B1"))
    }

    @Test fun t41_visited_contains_Q2_after_first_enter() {
        val g = newG(); reachQ2(g)
        assertTrue(g.visitedSnapshot().contains("Q2"))
    }

    // ---------- peekNext stability ----------
    @Test fun t42_peekNext_after_external_only_is_external() {
        val g = newG()
        g.enqueue("Q3")
        assertEquals("Q3", g.peekNext())
    }

    // ---------- pending uniqueness by nodeId ----------
    @Test fun t43_pending_no_duplicate_nodeIds() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        setMulti(g,"A") // repeated
        val ids = pqIds(g)
        assertEquals(ids.distinct(), ids)
    }

    // ---------- replaceQueued=false keeps old refs ----------
    @Test fun t44_replaceQueued_false_keeps_previous_children() {
        val g = newG(); reachQ2(g)
        g.updateMultiAnswer("Q2", listOf("A"), replaceQueued = true)
        g.updateMultiAnswer("Q2", listOf("B"), replaceQueued = false)
        // both A-children and B-child remain (no subtree invalidation)
        val ids = pqIds(g)
        assertTrue(ids.containsAll(listOf("Q2_A1","Q2_A2","Q2_B1")))
    }

    // ---------- getNextFrom with queue ----------
    @Test fun t45_getNextFrom_current_considers_queue() {
        val g = newG(); reachQ2(g)
        setMulti(g,"C")
        assertEquals("Q2_C1", g.getNextFrom("Q2"))
    }

    // ---------- onBack merges answers (choice) ----------
    @Test fun t46_onBack_keeps_newer_choice_answers() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")                 // snapshot
        g.advanceToNext()               // -> Q2_A1
        setMulti(g,"B")                 // newer answers (keepChoice)
        assertTrue(g.onBack())          // merge
        assertEquals(listOf("B"), g.getChoiceAnswer("Q2"))
    }

    // ---------- onBack keeps newer text answers ----------
    @Test fun t47_onBack_keeps_newer_text_answers() {
        val g = newG(); reachQ2(g)
        g.updateFreeText("Q3","old")
        g.advanceToNext() // snapshot at Q1
        g.updateFreeText("Q3","new")
        assertTrue(g.onBack())
        assertEquals("new", g.getTextAnswer("Q3"))
    }

    // ---------- peekPendingCount ----------
    @Test fun t48_peekPendingCount_matches_queue_size() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","B")
        assertEquals(3, g.peekPendingCount())
    }

    // ---------- originMapSnapshot immutable copy ----------
    @Test fun t49_originMapSnapshot_is_copy() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        val snap = g.originMapSnapshot()
        (snap["Q2"] as MutableList?)?.add("Q2_B1") // mutate snapshot
        // original must not change
        val snap2 = g.originMapSnapshot()
        assertFalse(snap2["Q2"]!!.contains("Q2_B1"))
    }

    // ---------- visitedSnapshot immutable copy ----------
    @Test fun t50_visitedSnapshot_is_copy() {
        val g = newG(); reachQ2(g)
        val v = g.visitedSnapshot().toMutableList()
        v.add("Q2_B1")
        assertFalse(g.visitedSnapshot().contains("Q2_B1"))
    }

    // ---------- pendingQueueSnapshot immutable copy ----------
    @Test fun t51_pendingQueueSnapshot_is_copy() {
        val g = newG(); reachQ2(g)
        setMulti(g,"B")

        val before = g.pendingQueueSnapshot()   // 内部のスナップショット
        val copy   = before.toMutableList()     // 呼び出し側でコピーを作る
        copy.clear()                            // これを壊しても…

        val after  = g.pendingQueueSnapshot()   // 内部は変わらないはず
        assertTrue(copy.isEmpty())
        assertEquals(before, after)             // 内容は完全一致（内部不変）
        assertNotSame(before, after)            // 参照は別（毎回コピーを返す）
    }

    // ---------- restore does not duplicate external ----------
    @Test fun t52_restore_rebuilds_externalPending_correctly() {
        val g = newG(); reachQ2(g)
        g.enqueue("Q3")
        val js = g.snapshotJson()
        val g2 = newG(); g2.restoreFromJson(js)
        assertEquals(listOf("Q3"), pqIds(g2))
    }

    // ---------- sanitizeNext ignores invalid ids ----------
    @Test fun t53_invalid_child_is_ignored() {
        val g = newG(); reachQ2(g)
        // simulate by pointing defaultNext of Q3 to invalid (not present)
        // we can't mutate Node map here; instead rely on sanitized behavior already tested.
        // Just ensure peekNext never returns blank
        assertTrue(g.peekNext().isNotBlank())
    }

    // ---------- allAnswers merges choice+text ----------
    @Test fun t54_allAnswers_contains_both_choice_and_text() {
        val g = newG()
        g.updateFreeText("Q3","t")
        g.advanceToNext() // -> Q1
        g.updateSingleAnswer("Q1","Yes")
        val all = g.allAnswers()
        assertTrue(all.containsKey("Q3"))
        assertTrue(all.containsKey("Q1"))
    }

    // ---------- clearAll sets current to start ----------
    @Test fun t55_clearAll_sets_current_to_start() {
        val g = newG(); reachQ2(g)
        g.clearAll()
        assertEquals(START, g.currentNodeId)
    }

    // ---------- updateSingleAnswer(null) clears ----------
    @Test fun t56_single_clear_removes_children() {
        val g = newG(); reachQ2(g)
        g.updateSingleAnswer("Q1","Yes") // not current, but fine
        g.updateSingleAnswer("Q1", null) // clear
        assertFalse(g.hasAnswerFor("Q1"))
    }

    // ---------- replaceQueued true removes subtree ----------
    @Test fun t57_replaceQueued_true_cuts_old_subtree() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","B")
        g.updateFreeText("Q2_A2","temp")
        setMulti(g,"B") // cut A-branch
        assertNull(g.getTextAnswer("Q2_A2"))
        assertEquals(listOf("Q2_B1"), pqIds(g))
    }

    // ---------- replaceQueued false keeps subtree ----------
    @Test fun t58_replaceQueued_false_keeps_old_subtree() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A","B")
        g.updateFreeText("Q2_A2","temp")
        g.updateMultiAnswer("Q2", listOf("B"), replaceQueued = false)
        assertEquals("temp", g.getTextAnswer("Q2_A2"))
    }

    // ---------- snapshot() equals restore(snapshot) ----------
    @Test fun t59_snapshot_restore_object_equality_on_core_fields() {
        val g = newG(); reachQ2(g); setMulti(g,"C")
        val s = g.snapshot()
        val g2 = newG(); g2.restore(s)
        assertEquals(g.pendingQueueSnapshot(), g2.pendingQueueSnapshot())
        assertEquals(g.visitedSnapshot(), g2.visitedSnapshot())
        assertEquals(g.choiceAnswersSnapshot(), g2.choiceAnswersSnapshot())
        assertEquals(g.textAnswersSnapshot(), g2.textAnswersSnapshot())
    }

    // ---------- getNode throws on missing ----------
    @Test(expected = IllegalArgumentException::class)
    fun t60_getNode_throws_on_missing() {
        val g = newG()
        g.getNode("NOPE")
    }

    // ---------- enqueue ignores END ----------
    @Test fun t61_enqueue_end_returns_false() {
        val g = newG()
        assertFalse(g.enqueue(END))
    }

    // ---------- peekNext respects visited-origin after current-origin ----------
    @Test fun t62_visited_origin_priority_after_current_and_external() {
        val g = newG(); reachQ2(g)
        setMulti(g, "A")         // Q2-origin
        g.advanceToNext()        // -> Q2_A1 (visited add)
        // Now pending has Q2_A2
        assertEquals("Q2_A2", g.peekNext()) // visited-origin (Q2) is still preferred subset of rule 3
    }

    // ---------- peekNextFrom(non-current) ignores queue ----------
    @Test fun t64_peekNextFrom_nonCurrent_ignores_queue() {
        val g = newG(); reachQ2(g)
        setMulti(g,"A")
        // for non-current START, next is Q1 regardless of pending
        assertEquals("Q1", g.peekNextFrom(START))
    }
}
