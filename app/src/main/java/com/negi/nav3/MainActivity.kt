// file: AnimatedActivity.kt
package com.negi.nav3

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch

// nav3 (あなたの実装に合わせて)
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.ui.NavDisplay

@Serializable
data class PathKey(val path: String) : NavKey

/**
 * GraphController:
 * SurveyGraph をラップし、Compose に必要な State と API を提供
 */
class GraphController(
    private val graph: SurveyGraph,
    private val enableDebug: Boolean = true
) {
    private val LOG_TAG = "GraphController"

    private val _currentNode = mutableStateOf(graph.currentNode())
    val currentNode: State<Node> = _currentNode

    // 回答更新のたびに version を上げて再Compose
    private val _answersVersion = mutableStateOf(0L)
    val answersVersion: State<Long> = _answersVersion
    private fun bumpAnswers() { _answersVersion.value = _answersVersion.value + 1 }

    // SurveyGraph に合わせたユーティリティ
    fun nodeExists(id: String): Boolean = try { graph.getNode(id); true } catch (_: Throwable) { false }
    fun nodeById(id: String): Node = graph.getNode(id)
    fun getChoiceAnswer(nodeId: String): List<String>? = graph.getChoiceAnswer(nodeId)
    fun peekNext(): String = graph.peekNext()
    fun canGoBack(): Boolean = graph.canGoBack()

    // --- 回答更新（no-bump）
    @Synchronized
    fun updateMultiAnswerNoBump(nodeId: String, selections: List<String>) {
        graph.updateMultiAnswer(nodeId, selections)
        if (enableDebug) {
            Log.i(LOG_TAG, "updateMultiAnswerNoBump node=$nodeId selections=$selections")
            try { graph.debugDump("after_updateMultiAnswerNoBump:$nodeId") } catch (_: Throwable) {}
        }
    }

    @Synchronized
    fun updateSingleAnswerNoBump(nodeId: String, selection: String?) {
        graph.updateSingleAnswer(nodeId, selection)
        if (enableDebug) {
            Log.i(LOG_TAG, "updateSingleAnswerNoBump node=$nodeId selection=$selection")
            try { graph.debugDump("after_updateSingleAnswerNoBump:$nodeId") } catch (_: Throwable) {}
        }
    }

    // --- 回答更新（bump あり）
    @Synchronized
    fun updateMultiAnswer(nodeId: String, selections: List<String>) {
        graph.updateMultiAnswer(nodeId, selections)
        if (enableDebug) {
            Log.i(LOG_TAG, "updateMultiAnswer node=$nodeId selections=$selections")
            try { graph.debugDump("after_updateMultiAnswer:$nodeId") } catch (_: Throwable) {}
        }
        bumpAnswers()
    }

    @Synchronized
    fun updateSingleAnswer(nodeId: String, selection: String?) {
        graph.updateSingleAnswer(nodeId, selection)
        if (enableDebug) {
            Log.i(LOG_TAG, "updateSingleAnswer node=$nodeId selection=$selection")
            try { graph.debugDump("after_updateSingleAnswer:$nodeId") } catch (_: Throwable) {}
        }
        bumpAnswers()
    }

    // --- navigation
    @Synchronized
    fun advanceToNext(): String {
        if (enableDebug) {
            Log.i(LOG_TAG, "advanceToNext: current=${_currentNode.value.id} (before)")
            try { graph.debugDump("before_advanceToNext") } catch (_: Throwable) {}
        }

        val next = graph.advanceToNext()

        // sync current
        try { _currentNode.value = graph.currentNode() } catch (t: Throwable) {
            Log.w(LOG_TAG, "advanceToNext: failed to read currentNode()", t)
        }

        if (enableDebug) {
            Log.i(LOG_TAG, "advanceToNext -> $next")
            try { graph.debugDump("after_advanceToNext") } catch (_: Throwable) {}
        }
        return next
    }

    @Synchronized
    fun onBack(): Boolean {
        if (enableDebug) {
            Log.i(LOG_TAG, "onBack: current=${_currentNode.value.id} (before)")
            try { graph.debugDump("before_onBack") } catch (_: Throwable) {}
        }

        val ok = graph.onBack()
        if (ok) {
            try { _currentNode.value = graph.currentNode() } catch (_: Throwable) {}
            bumpAnswers()
        }

        if (enableDebug) {
            Log.i(LOG_TAG, "onBack -> $ok")
            try { graph.debugDump("after_onBack") } catch (_: Throwable) {}
        }
        return ok
    }
}

/**
 * 次候補の計算:
 *  - optionOrder 優先、なければ選択キーの昇順
 *  - 見つからなければ peekNext()、さらに defaultNext
 */
fun computeTentativeNext(node: Node, selectedSet: Set<String>, controller: GraphController): String {
    val baseOrder = node.optionOrder ?: emptyList()
    val remainder = selectedSet.sorted().filter { it !in baseOrder }
    val order = if (baseOrder.isEmpty()) selectedSet.sorted() else baseOrder + remainder

    for (optKey in order) {
        if (optKey !in selectedSet) continue
        val targets = node.options[optKey] ?: emptyList()
        for (t in targets) {
            if (t.isNotBlank() && controller.nodeExists(t)) return t
        }
    }

    val peek = controller.peekNext()
    if (peek.isNotBlank() && peek != END && controller.nodeExists(peek)) return peek

    return node.defaultNext.takeIf { it.isNotBlank() && it != END && controller.nodeExists(it) } ?: END
}

/** 次ノードの本文をボタンラベルに */
@Composable
private fun rememberNextTitle(tentativeNext: String, controller: GraphController): String {
    return remember(tentativeNext) {
        if (tentativeNext.isNotBlank() && tentativeNext != END && controller.nodeExists(tentativeNext)) {
            controller.nodeById(tentativeNext).text.ifBlank { tentativeNext }
        } else ""
    }
}

/** Activity */
class AnimatedActivity : ComponentActivity() {
    private val graph: SurveyGraph = sampleBuild()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEdgeToEdgeConfig()

        setContent {
            val controller = remember { GraphController(graph) }
            val rootKey = PathKey(controller.currentNode.value.id)
            val backStack = rememberNavBackStack(rootKey)

            Scaffold { padding ->
                SurveyNavDisplay(
                    backStack = backStack,
                    controller = controller,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

/** NavDisplay wrapper */
@Composable
fun SurveyNavDisplay(
    backStack: NavBackStack,
    controller: GraphController,
    modifier: Modifier = Modifier,
    onRootBack: () -> Unit = {}
) {
    NavDisplay(
        backStack = backStack,
        onBack = {
            val handled = controller.onBack()
            if (handled) backStack.removeLastOrNull() else onRootBack()
        },
        entryProvider = entryProvider {
            entry<PathKey> { key ->
                NodeScreen(
                    keyPath = key.path,
                    controller = controller,
                    onPush = { id -> backStack.add(PathKey(id)) },
                    onPop = { backStack.removeLastOrNull() },
                    modifier = modifier
                )
            }
        },
        transitionSpec = { slideInHorizontally(initialOffsetX = { it }) togetherWith slideOutHorizontally(targetOffsetX = { -it }) },
        popTransitionSpec = { slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(targetOffsetX = { it }) },
        predictivePopTransitionSpec = { slideInHorizontally(initialOffsetX = { -it }) togetherWith slideOutHorizontally(targetOffsetX = { it }) },
        modifier = modifier
    )
}

@Composable
fun NodeScreen(
    keyPath: String,
    controller: GraphController,
    onPush: (String) -> Unit,
    onPop: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 現在ノード（SurveyGraph 側更新に追従）
    val currentNode = controller.currentNode.value

    // 表示対象ノードを決定
    val node: Node = if (currentNode.id == keyPath) currentNode else controller.nodeById(keyPath)

    // 表示順
    val optionKeys = node.optionOrder ?: node.options.keys.toList()

    // 回答版数（変更のたびに再計算）
    val answersVersionValue = controller.answersVersion.value

    // 現在の回答
    val selectedSet: Set<String> = controller.getChoiceAnswer(node.id)?.toSet() ?: emptySet()

    // 次候補
    val tentativeNext = remember(answersVersionValue, selectedSet, node.id) {
        computeTentativeNext(node, selectedSet, controller)
    }
    val nextTitle = rememberNextTitle(tentativeNext, controller)

    val meetsMin = selectedSet.size >= node.minSelect
    val isEnd = node.id == END
    val showNext = !isEnd && (meetsMin || (tentativeNext != END && tentativeNext.isNotBlank()))

    ContentOrange(title = "Screen: $keyPath", modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Question", style = MaterialTheme.typography.titleMedium)
                    Text(text = node.text, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Options
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OptionsList(
                    optionKeys = optionKeys,
                    optionsMap = node.options,
                    selectedSet = selectedSet,
                    allowMulti = node.allowMulti,
                    maxSelect = node.maxSelect,
                    onToggle = { optKey, isChecked ->
                        if (node.allowMulti) {
                            if (isChecked && !selectedSet.contains(optKey) && selectedSet.size >= node.maxSelect) return@OptionsList
                            val newSet = selectedSet.toMutableSet().also {
                                if (isChecked) it.add(optKey) else it.remove(optKey)
                            }
                            controller.updateMultiAnswer(node.id, newSet.toList())
                        } else {
                            if (isChecked) controller.updateSingleAnswer(node.id, optKey)
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                FooterControls(
                    controller = controller,
                    onPush = onPush,
                    onPop = onPop,
                    showBack = controller.canGoBack(),
                    showNext = showNext,
                    tentativeNext = tentativeNext,
                    nextTitle = tentativeNext
                )
            }
        }
    }
}

/** FooterControls */
@Composable
fun FooterControls(
    controller: GraphController,
    onPush: (String) -> Unit,
    onPop: () -> Unit,
    showBack: Boolean,
    showNext: Boolean,
    tentativeNext: String,
    nextTitle: String
) {
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .animateContentSize(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showBack) {
            OutlinedButton(onClick = {
                if (isProcessing) return@OutlinedButton
                scope.launch {
                    isProcessing = true
                    try {
                        val ok = controller.onBack()
                        if (ok) onPop()
                    } finally {
                        isProcessing = false
                    }
                }
            }) {
                Text("BACK")
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (showNext) {
            ElevatedButton(
                onClick = {
                    if (isProcessing) return@ElevatedButton
                    scope.launch {
                        isProcessing = true
                        try {
                            val nextId = controller.advanceToNext()
                            if (controller.nodeExists(nextId)) {
                                onPush(nextId)
                            } else if (controller.nodeExists(END)) {
                                onPush(END)
                            }
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.defaultMinSize(minWidth = 120.dp)
            ) {
                Text(if (nextTitle.isNotEmpty()) "次へ: $nextTitle" else "次へ")
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

/** OptionsList / OptionRow */
@Composable
fun OptionsList(
    optionKeys: List<String>,
    optionsMap: Map<String, List<String>>,
    selectedSet: Set<String>,
    allowMulti: Boolean,
    maxSelect: Int,
    onToggle: (String, Boolean) -> Unit
) {
    Column {
        optionKeys.forEach { optKey ->
            val checked = selectedSet.contains(optKey)
            val label = optKey // ラベルは暫定でキー
            if (allowMulti) {
                OptionRowCheckbox(
                    key = optKey,
                    label = label,
                    checked = checked,
                    disabled = !checked && selectedSet.size >= maxSelect,
                    onCheckedChange = { isChecked -> onToggle(optKey, isChecked) }
                )
            } else {
                OptionRowRadio(
                    key = optKey,
                    label = label,
                    selected = checked,
                    onSelected = { onToggle(optKey, true) }
                )
            }
        }
    }
}

@Composable
fun OptionRowCheckbox(
    key: String,
    label: String,
    checked: Boolean,
    disabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (!disabled) onCheckedChange(it) },
            enabled = !disabled || checked
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$key: $label", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun OptionRowRadio(
    key: String,
    label: String,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelected)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$key: $label", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColorBlock(
    title: String,
    color: Color,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(color)
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.Black)
                content?.invoke()
            }
        }
    }
}

@Composable
fun ContentOrange(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)? = null
) = ColorBlock(title, Color(0xFFFFE0B2), modifier, content)

fun ComponentActivity.setEdgeToEdgeConfig() {
    enableEdgeToEdge()
}
