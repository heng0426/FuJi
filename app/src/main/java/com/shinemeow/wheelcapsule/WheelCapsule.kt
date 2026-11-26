/*
 * ============================================================================
 * WheelCapsule — 胶囊式滚轮选择器（单文件、零业务依赖）
 * ----------------------------------------------------------------------------
 * 从 Nikon_Tranf（ShineMeow）摄影参数胶囊滚轮中抽离而来，保留真机打磨过的：
 *   · 按住 130ms 才进入调值，避免在可滚动面板里误改参数
 *   · 判定窗口内越过 touchSlop 立即把手势让给父级滚动
 *   · 相邻项渐隐、松手吸附、过挡触感、外部值同步、写值失败归位
 *   · 长文案按实测宽度自动降字号，永不换行/溢出
 *
 * 依赖：仅 Jetpack Compose（ui / foundation / material3），无第三方库、无资源文件。
 * 移植：把本文件复制到目标工程并改 package 即可；也可直接依赖 :wheelcapsule 模块。
 *
 * 最简用法：
 * ```
 * var selected by remember { mutableIntStateOf(0) }
 * WheelCapsule(
 *     items = listOf("矩阵", "点测", "中央重点"),
 *     selected = selected,
 *     onSelect = { selected = it },
 *     modifier = Modifier.fillMaxWidth(),
 * )
 * ```
 *
 * 布局约定：组件自身高度恒为 itemHeight * 3（收起时不占位变化），
 * 胶囊在壳内居中、上下对称展开，因此不会推挤调用方的布局。
 * ----------------------------------------------------------------------------
 * 本地扩展（小福日常）：新增 loop 参数，末项与首项无缝衔接（时间轮：23→00、59→00）。
 * 该扩展尚未回灌上游 wheelcapsule 工程，合入上游时以本文件为准。
 * ============================================================================
 */
package com.shinemeow.wheelcapsule

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 胶囊滚轮的配色。
 *
 * 收起时只显示当前项；按住展开后，相邻项按 [WheelCapsuleDefaults] 的规则渐隐。
 * 禁用态由组件在 [contentColor] / [containerColor] 基础上统一压淡，不需要单独给色。
 *
 * @property contentColor 文字颜色（中心项用它，相邻项自动降透明度）
 * @property containerColor 胶囊底色
 */
@Immutable
data class WheelCapsuleColors(
    val contentColor: Color,
    val containerColor: Color,
)

/** [WheelCapsule] 的默认尺寸与行为参数。改这里即可全局调风格，或按调用点单独覆盖。 */
object WheelCapsuleDefaults {
    /** 单挡高度，也是展开时相邻两项的间距。 */
    val ItemHeight: Dp = 20.dp

    /** 收起时胶囊的高度（应 ≤ ItemHeight * 3）。 */
    val CollapsedHeight: Dp = 46.dp

    /** 文字左右安全边距：自动缩字号时按「胶囊宽 - 2 * 该值」计算可用宽度。 */
    val TextHorizontalPadding: Dp = 6.dp

    /** 中心项字号。 */
    val CenterTextSize: TextUnit = 16.sp

    /** 相邻项起始字号（放不下时会继续自动缩小）。 */
    val NeighborTextSize: TextUnit = 13.sp

    /** 自动缩字号的兜底下限，再小不再缩。 */
    val MinTextSize: TextUnit = 8.sp

    /** 按住多久（毫秒）判定为「要调值」；此窗口内划走则把手势让给父级滚动。 */
    const val EngageTimeoutMillis: Long = 130L

    /** 松手吸附 / 展开收起的动画时长（毫秒）。 */
    const val SnapAnimationMillis: Int = 150

    /** 外部值变化（如服务端回读）时的跟随动画时长（毫秒）。 */
    const val SyncAnimationMillis: Int = 180

    /** 跟随 Material 主题的默认配色。 */
    @Composable
    fun colors(
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    ): WheelCapsuleColors = WheelCapsuleColors(contentColor, containerColor)
}

/**
 * 胶囊式滚轮选择器。
 *
 * ## 交互
 * - **静止**：胶囊中心只显示 [selected] 对应项，加粗。
 * - **按住**：胶囊原地展开为 3 项高，上下相邻项渐隐（被胶囊裁切，不越界）。
 * - **滑动**：连续跟手换挡，每越过一项触发一次触感；上滑 = 序号增大。
 * - **松手**：吸附最近项，若与 [selected] 不同则回调 [onSelect]。
 * - **按住判定**：按下后 130ms 内移动超过 touchSlop，视为「用户想滚父容器」，
 *   组件完全不消费手势；静止按住超过 130ms 才进入调值。这样把滚轮铺在可滚动面板里
 *   也不会出现「想滚面板却把参数改了」。把 [engageTimeoutMillis] 设为 0 可关掉这段等待，
 *   按下即可直接滑动（代价是该区域不再把竖向手势让给父容器）。
 *
 * ## 状态同步
 * 组件是**受控**的：显示以 [selected] 为准。外部值变化（网络回读、重置等）时，
 * 非拖动状态下会用 [WheelCapsuleDefaults.SyncAnimationMillis] 动画跟随。
 * 若写值失败需要强制把滚轮拉回真值，把 [resetKey] 递增即可（真值没变时也能触发归位）。
 *
 * ## 布局
 * 组件自身高度恒为 `itemHeight * 3`，收起 / 展开都不改变占位；胶囊在壳内居中，
 * 上下对称预留展开空间。传入的 [modifier] 作用在最外层（padding、width、weight 都按常规语义生效）。
 *
 * ## 示例
 * ```
 * var iso by remember { mutableIntStateOf(2) }
 * WheelCapsule(
 *     items = listOf("100", "200", "400", "800", "1600"),
 *     selected = iso,
 *     onSelect = { iso = it },
 *     modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
 * )
 * ```
 *
 * @param items 候选文案（不能为空；为空时显示占位「—」）
 * @param selected 当前项下标，越界会自动收拢到合法范围
 * @param onSelect 用户确认新挡位时回调（只在值真正变化时触发）
 * @param modifier 作用在最外层的修饰符
 * @param enabled false = 灰置且不响应手势
 * @param loop true = 首尾无缝循环（滑过末项接首项、首项下滑接末项）。
 *   为保证同一窗口内的候选项不重复，循环模式建议 items.size ≥ 5（时间轮 24 / 60 项）。
 * @param colors 配色，默认跟随 Material 主题
 * @param itemHeight 单挡高度 / 相邻项间距
 * @param collapsedHeight 收起时胶囊高度
 * @param textHorizontalPadding 文字左右安全边距
 * @param centerTextSize 中心项字号
 * @param neighborTextSize 相邻项字号
 * @param minTextSize 自动缩字号下限
 * @param hapticsEnabled 是否在过挡时震动
 * @param resetKey 递增可强制把内部位置归位到 [selected]（写值失败场景）
 * @param engageTimeoutMillis 按住判定时长；<= 0 表示不等待，按下即可直接拖动
 * @param snapAnimationMillis 吸附与展开动画时长
 * @param a11yPreviousLabel 读屏「上一项」动作文案
 * @param a11yNextLabel 读屏「下一项」动作文案
 * @param contentDescription 读屏焦点名称；null 时只朗读当前值
 */
@Composable
fun WheelCapsule(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loop: Boolean = false,
    colors: WheelCapsuleColors = WheelCapsuleDefaults.colors(),
    itemHeight: Dp = WheelCapsuleDefaults.ItemHeight,
    collapsedHeight: Dp = WheelCapsuleDefaults.CollapsedHeight,
    textHorizontalPadding: Dp = WheelCapsuleDefaults.TextHorizontalPadding,
    centerTextSize: TextUnit = WheelCapsuleDefaults.CenterTextSize,
    neighborTextSize: TextUnit = WheelCapsuleDefaults.NeighborTextSize,
    minTextSize: TextUnit = WheelCapsuleDefaults.MinTextSize,
    hapticsEnabled: Boolean = true,
    resetKey: Int = 0,
    engageTimeoutMillis: Long = WheelCapsuleDefaults.EngageTimeoutMillis,
    snapAnimationMillis: Int = WheelCapsuleDefaults.SnapAnimationMillis,
    a11yPreviousLabel: String = "上一项",
    a11yNextLabel: String = "下一项",
    contentDescription: String? = null,
) {
    val pressedHeight = itemHeight * 3
    val density = LocalDensity.current

    if (items.isEmpty()) {
        // 空列表给占位而不是崩（调用方的能力表/候选表可能异步到达）
        Box(
            modifier.height(pressedHeight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentColor.copy(alpha = colors.contentColor.alpha * 0.4f)
            )
        }
        return
    }

    val lastIndex = items.lastIndex
    val count = items.size
    val selectedSafe = selected.coerceIn(0, lastIndex)
    val restHeight = collapsedHeight.coerceIn(0.dp, pressedHeight)
    val itemHeightPx = with(density) { itemHeight.toPx() }
    // 循环模式下 position 是无界（按 count 取模）的连续坐标：整数部分只表示"转了多少圈"，
    // 显示一律走 wrapIndex，因此同一位置可以等价地写成 0 / count / 2count。
    val looping = loop && count > 1

    var pressed by remember { mutableStateOf(false) }
    // ⚠️ 不要用 remember(selected) 重建 state：selected 变化会换掉 state 对象，
    // 而 pointerInput 闭包仍捕获旧对象 → 手势写旧值（触感在、UI 不动）。
    // 统一交给下面的 LaunchedEffect 做动画归位。
    var position by remember { mutableFloatStateOf(selectedSafe.toFloat()) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    /* ⚠️ pointerInput 的 lambda 只在 keys 变化时重建，而 snapAndCommit 会捕获创建那一刻的
     * selected / onSelect。若不用 rememberUpdatedState，就会出现「值没变就不写」用旧值判断：
     *   A(1) → B(2) 能写；再拖回 A 时 target(1) == 旧 selected(1) → 判定没变 → 跳过写入，
     *   松手后什么都没发生，而 position 已回到 A，显示与真实值不一致。
     * 所以闭包里一律读 selectedNow / onSelectNow。
     *
     * ⚠️ 不要再加「松手/超时后回弹到真值」的兜底：写值是异步的，松手瞬间 selected 还是旧值，
     * 任何此刻的回弹都会表现为「先滚回去、写值回来再滚回来」的不跟手。
     * 写值失败请由调用方递增 resetKey，或用状态轮询纠偏。 */
    val selectedNow = rememberUpdatedState(selectedSafe)
    val onSelectNow = rememberUpdatedState(onSelect)

    // 外部值同步：非拖动中 selected 变化 → position 动画归位
    // resetKey：调用方写值失败时递增，强制归位（失败后真值没变，必须把滚轮拉回去）
    LaunchedEffect(selectedSafe, resetKey, items.size, looping) {
        if (pressed) return@LaunchedEffect
        // 循环模式取与当前位置**同圈**的等价目标，避免 23 → 00 被反向绕一整圈
        val target = if (looping) {
            (selectedSafe + ((position - selectedSafe) / count).roundToInt() * count).toFloat()
        } else {
            selectedSafe.toFloat()
        }
        if (position != target) {
            val anim = Animatable(position)
            anim.animateTo(
                target,
                tween(WheelCapsuleDefaults.SyncAnimationMillis)
            ) { position = value }
            // 动画停在同值异圈的位置上，无动画收回到首圈，视觉零变化
            if (looping) position = wrapPosition(position, count)
        }
    }

    val capsuleHeight by animateDpAsState(
        if (pressed) pressedHeight else restHeight,
        tween(snapAnimationMillis),
        label = "capsuleHeight"
    )
    val reveal by animateFloatAsState(
        if (pressed) 1f else 0f,
        tween(snapAnimationMillis),
        label = "reveal"
    )
    // 灰置时文字一并压淡：只压背景在深色面板上看不出「不可用」
    val content = if (enabled) colors.contentColor
    else colors.contentColor.copy(alpha = colors.contentColor.alpha * 0.4f)
    val container = if (enabled) colors.containerColor
    else colors.containerColor.copy(alpha = colors.containerColor.alpha * 0.5f)

    fun snapAndCommit() {
        // 循环模式先吸附到"本圈"的整数位置（视觉上就是最近的一挡），再取模换算真实下标
        val rawTarget = if (looping) position.roundToInt()
                        else position.roundToInt().coerceIn(0, lastIndex)
        val target = if (looping) wrapIndex(rawTarget, count) else rawTarget
        scope.launch {
            val anim = Animatable(position)
            anim.animateTo(rawTarget.toFloat(), tween(snapAnimationMillis)) { position = value }
            // 停在别的圈上时无动画收回首圈：渲染按取模，视觉零跳变
            if (looping) position = wrapPosition(position, count)
            // 与**最新**值比较：相同才跳过（重复选同一挡不必打扰宿主）
            if (target != selectedNow.value && enabled) onSelectNow.value(target)
        }
    }

    // 外层高度固定 = 展开最大高：收起/展开都不推挤调用方布局
    Box(
        modifier.height(pressedHeight),
        contentAlignment = Alignment.Center
    ) {
        // BoxWithConstraints：拿胶囊实际宽 → 长文案自动缩字号。
        // 3 列网格里「RAW+JPG 精细★」这类文案按 16sp 放不下，换行后相邻项会叠字。
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(capsuleHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(container)
                // 单一手势：默认按住 130ms 才算「要调值」，在此之前划走 = 滚父容器。
                // engageTimeoutMillis <= 0 时跳过判定，按下就能直接跟手滑（时间轮用这种方式）。
                // ⚠️ 判定窗口必须用 withTimeoutOrNull 计时：只在 awaitPointerEvent 里比较
                //    System.currentTimeMillis() 的话，手指静止时收不到事件、永远判不出来，
                //    等到第一个 MOVE 时位移已经超过 slop → 被判成「滚动」（真机踩过）。
                .pointerInput(
                    items.size,
                    enabled,
                    itemHeightPx,
                    hapticsEnabled,
                    engageTimeoutMillis,
                    snapAnimationMillis,
                    looping
                ) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var engaged = false
                        var handOff = false          // true = 让给父级滚动，绝不写值
                        try {
                            if (engageTimeoutMillis <= 0L) {
                                // 无等待：按下即接管本区域手势，第一帧移动就开始改值；
                                // 展开延到出现真实位移时（见下方 pressed 赋值），轻点不会闪
                                engaged = true
                            } else {
                                // 判定窗口内**累计**位移：130ms 内越过 slop 就判为滚动（让给父级），
                                // 窗口到期仍未越过才算「要调值」。只看单个事件不够——真手指每帧
                                // 只移动十几像素，首个事件通常小于 slop。
                                val verdict = withTimeoutOrNull(engageTimeoutMillis) {
                                    var total = 0f
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id }
                                            ?: return@withTimeoutOrNull 1
                                        if (!ch.pressed) return@withTimeoutOrNull 1   // 只是点了一下
                                        total += ch.positionChange().getDistance()
                                        if (total > viewConfiguration.touchSlop) {
                                            return@withTimeoutOrNull 2              // 滚动
                                        }
                                    }
                                    @Suppress("UNREACHABLE_CODE") 1
                                }
                                when (verdict) {
                                    2 -> handOff = true
                                    1 -> {}                        // 抬手结束，什么都不做
                                    else -> { engaged = true; pressed = true }   // null = 超时 → 调值
                                }
                            }
                            if (!handOff) while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id }
                                if (ch == null || !ch.pressed) break
                                val delta = ch.positionChange()
                                if (!engaged) continue
                                if (delta.y != 0f) {
                                    ch.consume()
                                    // 展开留给"真的开始拖"那一刻：轻点不会闪一下展开动画
                                    if (!pressed) pressed = true
                                    val old = position.roundToInt()
                                    // 上滑（delta.y < 0）→ position 增 → 序号变大
                                    val moved = position - delta.y / itemHeightPx
                                    // 循环模式环绕而非夹紧：23 之后自然接 00、59 之后接 00
                                    position = if (looping) wrapPosition(moved, count)
                                               else moved.coerceIn(0f, lastIndex.toFloat())
                                    if (hapticsEnabled && position.roundToInt() != old) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            }
                        } finally {
                            pressed = false
                            if (engaged && !handOff) snapAndCommit()
                        }
                    }
                }
                .clearAndSetSemantics {
                    // 自绘滚轮默认会让读屏把 5 个可见项一起念出来，这里收敛成单节点：
                    // 名称（可选）+ 当前值 + 上下项切换动作。
                    contentDescription?.let { this.contentDescription = it }
                    stateDescription = items.getOrNull(selectedSafe).orEmpty()
                    if (!enabled) disabled()
                    val cur = selectedNow.value.coerceIn(0, lastIndex)
                    // 循环模式下读屏的前后项也首尾相接（00 的上一项是 23），与滑动手感一致
                    val prev = if (looping) wrapIndex(cur - 1, count) else cur - 1
                    val next = if (looping) wrapIndex(cur + 1, count) else cur + 1
                    customActions = listOf(
                        CustomAccessibilityAction(a11yPreviousLabel) {
                            if (enabled && prev in items.indices) {
                                onSelectNow.value(prev)
                                true
                            } else {
                                false
                            }
                        },
                        CustomAccessibilityAction(a11yNextLabel) {
                            if (enabled && next in items.indices) {
                                onSelectNow.value(next)
                                true
                            } else {
                                false
                            }
                        }
                    )
                },
        ) {
            val textStyle = LocalTextStyle.current
            val measurer = rememberTextMeasurer()
            // 可用宽 = 胶囊宽 - 左右安全边距（文字永不顶到圆角边）
            val fitWidthPx = with(density) {
                (maxWidth - textHorizontalPadding * 2).toPx().toInt().coerceAtLeast(0)
            }
            // 可见窗口：以当前项为中心的 ±2 项，各自独立定位（越远越淡）
            // 循环模式沿无界坐标展开再取模映射到真实下标，所以 23 的下一项就是 00
            val baseIdx = if (looping) position.roundToInt()
                          else position.roundToInt().coerceIn(0, lastIndex)
            for (rel in -2..2) {
                val slot = baseIdx + rel
                val i = if (looping) wrapIndex(slot, count) else slot
                if (i !in items.indices) continue
                // key(rel)：循环且项数 < 5 时同一项会在窗口里出现两次，用相对槽位保证 key 唯一；
                // remember 的键里带了 itemText，换项时字号仍会重算，不会张冠李戴。
                key(rel) {
                    val d = slot - position
                    val itemText = items[i]
                    val isCenter = rel == 0
                    val baseSize = if (isCenter) centerTextSize else neighborTextSize
                    // 单行自适应：放不下就按 0.5sp 递减（最多到 minTextSize），绝不换行/溢出
                    val fontSize = remember(itemText, fitWidthPx, baseSize, textStyle, minTextSize) {
                        fitSingleLineFontSize(
                            measurer, itemText, textStyle, fitWidthPx, baseSize, minTextSize
                        )
                    }
                    Text(
                        text = itemText,
                        fontSize = fontSize,
                        maxLines = 1,
                        softWrap = false,
                        fontWeight = if (isCenter) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isCenter) content else content.copy(alpha = content.alpha * 0.55f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .offset { IntOffset(0, (d * itemHeightPx).roundToInt()) }
                            .alpha(
                                if (isCenter) 1f
                                else (reveal * (1f - 0.35f * abs(d))).coerceIn(0f, 1f)
                            ),
                    )
                }
            }
        }
    }
}

/**
 * 环形取模：把任意整数下标折进 `0 until count`（负数也成立，如 -1 → count-1）。
 * 循环滚轮的所有「真实下标」都经过它，窗口计算仍用无界坐标。
 */
private fun wrapIndex(index: Int, count: Int): Int = ((index % count) + count) % count

/**
 * 环形位置归一：把连续坐标折进 `[0, count)`。
 * 整数部分表示转了几圈，小数部分决定挡位的跟手偏移，所以取模不改变任何可见像素。
 */
private fun wrapPosition(position: Float, count: Int): Float {
    val m = position % count
    return if (m < 0f) m + count else m
}

/**
 * 单行自适应字号：从 [maxSize] 起按 0.5sp 递减，返回第一个能塞进 [widthPx] 的档位。
 * 用 TextMeasurer 实测而非按字数估算（中英混排宽度差很大）。
 * 下限 [minSize]：再小就不可读了，宁可字号到底——此时仍单行、居中，不换行不溢出。
 */
private fun fitSingleLineFontSize(
    measurer: TextMeasurer,
    text: String,
    baseStyle: TextStyle,
    widthPx: Int,
    maxSize: TextUnit,
    minSize: TextUnit,
): TextUnit {
    if (widthPx <= 0) return maxSize
    var value = maxSize.value
    val floor = minSize.value
    while (value > floor) {
        val width = measurer.measure(
            text = text,
            style = baseStyle.copy(fontSize = value.sp),
            softWrap = false,
            maxLines = 1
        ).size.width
        if (width <= widthPx) return value.sp
        value -= 0.5f
    }
    return minSize
}

/* ============================================================================
 * 预览：直接复制到自己的项目里也能看到效果
 * ========================================================================== */

@Preview(showBackground = true, widthDp = 200, heightDp = 160, name = "WheelCapsule")
@Composable
private fun WheelCapsulePreview() {
    var selected by remember { mutableIntStateOf(1) }
    MaterialTheme {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            WheelCapsule(
                items = listOf("矩阵测光", "点测光", "中央重点测光"),
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
