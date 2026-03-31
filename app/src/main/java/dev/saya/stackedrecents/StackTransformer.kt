package dev.saya.stackedrecents

import android.view.View
import kotlin.math.abs
import kotlin.math.max

/**
 * 叠加式卡片变换计算器。
 *
 * 视觉目标（参考 MIUI 堆叠 + iOS 风格）：
 *  - 当前焦点卡片：全尺寸，在最前
 *  - 后面的卡片：逐级缩小、向上移动、透明度降低
 *  - 前面已划走的卡片：向下移出屏幕
 *
 *  scrollOffset = 当前滚动位置（像素）
 *  pageWidth    = 单张卡片的宽度（也是每页的步长）
 *  pageIndex    = 本张卡片的索引（从 0 开始）
 */
object StackTransformer {

    // 每级叠加的缩放降幅（0.06 ≈ MIUI 的视觉比例）
    private const val SCALE_STEP = 0.06f
    // 每级叠加向上偏移的比例（相对卡片高度）
    private const val TRANSLATE_Y_STEP = 0.10f
    // 叠加层数上限（超出部分完全透明）
    private const val MAX_STACK_DEPTH = 5
    // 向上叠加时最低透明度
    private const val MIN_ALPHA = 0.0f

    /**
     * 计算并应用变换到 [view]。
     *
     * @param view       TaskView 实例
     * @param scrollX    RecentsView 当前的 scrollX
     * @param pageIndex  该卡片在任务列表中的索引
     * @param pageWidth  单页（卡片）宽度，含间距
     * @param totalTasks 任务总数
     */
    fun applyTransform(
        view: View,
        scrollX: Int,
        pageIndex: Int,
        pageWidth: Int,
        totalTasks: Int
    ) {
        if (pageWidth == 0) return

        // 当前滚动位置对应的"焦点页"（可以是小数，表示两页之间）
        val focusPage = scrollX.toFloat() / pageWidth

        // 本卡片相对焦点的偏移量（正值 = 在焦点右侧 = 还未看到）
        val delta = pageIndex - focusPage

        when {
            // ---- 当前焦点卡片（delta ≈ 0）或向右滑动中的过渡 ----
            delta >= 0 -> {
                // delta=0 完全展示，delta=1 完全叠入下一张
                val depth = delta.coerceIn(0f, MAX_STACK_DEPTH.toFloat())

                val scale = (1f - depth * SCALE_STEP).coerceAtLeast(0.7f)
                val translateY = -depth * TRANSLATE_Y_STEP * view.height
                val alpha = (1f - depth / MAX_STACK_DEPTH).coerceAtLeast(MIN_ALPHA)

                view.scaleX = scale
                view.scaleY = scale
                view.translationY = translateY
                view.alpha = alpha

                // Z 轴：焦点卡片在最上面
                view.translationZ = (totalTasks - pageIndex).toFloat()
            }

            // ---- 已划过的卡片（delta < 0）：向下滑出 ----
            else -> {
                // delta = -1 时卡片已完全离开屏幕下方
                val exitProgress = (-delta).coerceIn(0f, 1f)

                view.scaleX = 1f
                view.scaleY = 1f
                view.translationY = exitProgress * view.height * 0.3f
                view.alpha = (1f - exitProgress * 2f).coerceAtLeast(0f)
                view.translationZ = 0f
            }
        }
    }

    /**
     * 重置卡片变换（用于模块禁用时还原）
     */
    fun resetTransform(view: View) {
        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
        view.translationZ = 0f
        view.alpha = 1f
    }
}
