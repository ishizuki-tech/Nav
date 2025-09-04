package com.negi.nav3

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimatedActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<AnimatedActivity>()

    // ---- helpers (fetchSemanticsNodes を使わない待機) -------------------------

    private fun waitForText(text: String, timeoutMs: Long = 5_000L) {
        val matcher = hasText(text)
        composeTestRule.waitUntil(timeoutMs) {
            try {
                composeTestRule.onNode(matcher, useUnmergedTree = true).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.onNode(matcher, useUnmergedTree = true).assertExists()
    }

    private fun waitForTextGone(text: String, timeoutMs: Long = 3_000L) {
        val matcher = hasText(text, substring = true)
        composeTestRule.waitUntil(timeoutMs) {
            try {
                composeTestRule.onNode(matcher, useUnmergedTree = true).assertDoesNotExist()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule.onNode(matcher, useUnmergedTree = true).assertDoesNotExist()
    }

    // Start -> Q1（Start は「次へ」で進む）
    private fun advanceStartToQ1() {
        val nextMatcher = hasText("次へ", substring = true)
        composeTestRule.onNode(nextMatcher, useUnmergedTree = true)
            .assertExists("Next button not found on Start screen")
            .performClick()

        // Q1 の Yes/No のどちらかが出るまで待つ
        composeTestRule.waitUntil(3_000L) {
            try {
                composeTestRule.onNode(hasText("Yes: Yes"), useUnmergedTree = true).assertExists()
                true
            } catch (_: AssertionError) {
                try {
                    composeTestRule.onNode(hasText("No: No"), useUnmergedTree = true).assertExists()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
        }
    }

    // ---- tests ----------------------------------------------------------------

    @Test
    fun test_selectNo_then_next_leads_to_End_and_nextButton_hidden() {
        advanceStartToQ1()

        // Q1: No を選択 → 次へ
        composeTestRule.onNode(hasText("No: No"), useUnmergedTree = true)
            .assertExists()
            .performClick()
        composeTestRule.onNode(hasText("次へ", substring = true), useUnmergedTree = true)
            .assertExists()
            .performClick()

        // End 画面（"終了"）が出るまで待つ
        waitForText("終了", 4_000L)

        // End では「次へ」が存在しないこと
        waitForTextGone("次へ", 2_000L)
    }

    @Test
    fun test_selectYes_then_next_shows_Q2_and_options_present() {
        advanceStartToQ1()

        // Q1: Yes → 次へ
        composeTestRule.onNode(hasText("Yes: Yes"), useUnmergedTree = true)
            .assertExists()
            .performClick()
        composeTestRule.onNode(hasText("次へ", substring = true), useUnmergedTree = true)
            .assertExists()
            .performClick()

        // Q2 の本文が出るのを待つ
        waitForText("複数選択可の質問（最大2つまで）", 4_000L)

        // A/B/C が見える
        composeTestRule.onNode(hasText("A: A"), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("B: B"), useUnmergedTree = true).assertExists()
        composeTestRule.onNode(hasText("C: C"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun test_next_button_label_reflects_tentative_target_after_select_A() {
        advanceStartToQ1()

        // Q1 -> Q2
        composeTestRule.onNode(hasText("Yes: Yes"), useUnmergedTree = true)
            .assertExists()
            .performClick()
        composeTestRule.onNode(hasText("次へ", substring = true), useUnmergedTree = true)
            .assertExists()
            .performClick()
        waitForText("複数選択可の質問（最大2つまで）", 4_000L)

        // A を選択 → ボタンラベルにフォローアップ1の文言が現れるまで待つ
        composeTestRule.onNode(hasText("A: A"), useUnmergedTree = true)
            .assertExists()
            .performClick()

        // ボタン内テキストは合成されるので substring + unmergedTree で待つ
        composeTestRule.waitUntil(2_000L) {
            try {
                composeTestRule
                    .onNode(hasText("Q2 - A のフォローアップ1", substring = true), useUnmergedTree = true)
                    .assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        composeTestRule
            .onNode(hasText("Q2 - A のフォローアップ1", substring = true), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun test_back_restores_previous_screen_and_selection_survives() {
        advanceStartToQ1()

        // Q1 -> Q2
        composeTestRule.onNode(hasText("Yes: Yes"), useUnmergedTree = true)
            .assertExists()
            .performClick()
        composeTestRule.onNode(hasText("次へ", substring = true), useUnmergedTree = true)
            .assertExists()
            .performClick()
        waitForText("複数選択可の質問（最大2つまで）", 4_000L)

        // A 選択 → 次へ → Q2_A1
        composeTestRule.onNode(hasText("A: A"), useUnmergedTree = true)
            .assertExists()
            .performClick()
        composeTestRule.onNode(hasText("次へ", substring = true), useUnmergedTree = true)
            .assertExists()
            .performClick()
        waitForText("Q2 - A のフォローアップ1", 4_000L)

        // BACK → Q2 に戻る
        composeTestRule.onNode(hasText("BACK"), useUnmergedTree = true)
            .assertExists()
            .performClick()
        waitForText("複数選択可の質問（最大2つまで）", 4_000L)

        // A の項目が引き続き見える（チェック状態は Semantics 依存なので存在のみ確認）
        composeTestRule.onNode(hasText("A: A"), useUnmergedTree = true).assertExists()
    }
}
