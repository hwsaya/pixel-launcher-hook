package dev.saya.stackedrecents

import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * 对 RecentsView 的核心 Hook，针对 Android 16 / PixelOS 16.x 适配。
 *
 * Android 16 架构变化（相比 Android 14）：
 *   1. Pixel Launcher 中实际实例化的是 LauncherRecentsView（继承 RecentsView）
 *   2. 普通任务卡片从 TaskView 升级为 GroupedTaskView（继承 TaskView）
 *   3. 分屏任务使用 DesktopTaskView（同样继承 TaskView）
 *
 * Hook 策略：
 *   - 优先 hook LauncherRecentsView，找不到则回退到 RecentsView
 *   - 卡片识别：检查是否为 TaskView 的子类（包含 GroupedTaskView/DesktopTaskView）
 *   - 触发时机：onScrollChanged + onLayout + setCurrentPage
 */
object RecentsHook {

    // Android 16：Pixel Launcher 实际用的子类
    private const val LAUNCHER_RECENTS_VIEW_CLASS =
        "com.android.quickstep.views.LauncherRecentsView"

    // 基类（回退用）
    private const val RECENTS_VIEW_CLASS =
        "com.android.quickstep.views.RecentsView"

    // Android 16 的普通任务卡片（继承 TaskView）
    private const val GROUPED_TASK_VIEW_CLASS =
        "com.android.quickstep.views.GroupedTaskView"

    // 基础 TaskView（作为类型判断的基类）
    private const val TASK_VIEW_CLASS =
        "com.android.quickstep.views.TaskView"

    // PagedView（setCurrentPage 所在的基类）
    private const val PAGED_VIEW_CLASS =
        "com.android.launcher3.PagedView"

    fun install(classLoader: ClassLoader) {
        // 优先找 LauncherRecentsView，Android 16 Pixel Launcher 专用
        val recentsClass =
            XposedHelpers.findClassIfExists(LAUNCHER_RECENTS_VIEW_CLASS, classLoader)
                ?: XposedHelpers.findClassIfExists(RECENTS_VIEW_CLASS, classLoader)
                ?: run {
                    XposedBridge.log("[StackedRecents] ERROR: Cannot find RecentsView class")
                    return
                }

        XposedBridge.log("[StackedRecents] Hooking class: ${recentsClass.name}")

        hookOnScrollChanged(recentsClass)
        hookOnLayout(recentsClass)
        hookSetCurrentPage(classLoader, recentsClass)

        XposedBridge.log("[StackedRecents] All hooks installed")
    }

    // ------------------------------------------------------------------
    // 1. onScrollChanged —— 滑动时实时更新叠加变换
    // ------------------------------------------------------------------
    private fun hookOnScrollChanged(recentsClass: Class<*>) {
        XposedBridge.hookAllMethods(recentsClass, "onScrollChanged", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                applyStackTransform(param.thisObject as? ViewGroup ?: return)
            }
        })
    }

    // ------------------------------------------------------------------
    // 2. onLayout —— 布局完成后初始化叠加状态
    // ------------------------------------------------------------------
    private fun hookOnLayout(recentsClass: Class<*>) {
        XposedBridge.hookAllMethods(recentsClass, "onLayout", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                applyStackTransform(param.thisObject as? ViewGroup ?: return)
            }
        })
    }

    // ------------------------------------------------------------------
    // 3. setCurrentPage —— 页面切换完成时更新
    // ------------------------------------------------------------------
    private fun hookSetCurrentPage(classLoader: ClassLoader, recentsClass: Class<*>) {
        val pagedViewClass = XposedHelpers.findClassIfExists(PAGED_VIEW_CLASS, classLoader)
            ?: return

        XposedBridge.hookAllMethods(pagedViewClass, "setCurrentPage", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val view = param.thisObject as? ViewGroup ?: return
                // 只处理 RecentsView 及其子类
                if (recentsClass.isInstance(view)) {
                    applyStackTransform(view)
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // 核心变换逻辑
    // ------------------------------------------------------------------
    private fun applyStackTransform(recentsView: ViewGroup) {
        val scrollX = recentsView.scrollX
        val childCount = recentsView.childCount
        if (childCount == 0) return

        val pageWidth = getPageWidth(recentsView) ?: return

        // 收集所有 TaskView 子类（GroupedTaskView / DesktopTaskView / TaskView）
        val taskViews = mutableListOf<View>()
        for (i in 0 until childCount) {
            val child = recentsView.getChildAt(i)
            if (isTaskView(child)) {
                taskViews.add(child)
            }
        }

        val total = taskViews.size
        taskViews.forEachIndexed { index, taskView ->
            StackTransformer.applyTransform(
                view = taskView,
                scrollX = scrollX,
                pageIndex = index,
                pageWidth = pageWidth,
                totalTasks = total
            )
        }
    }

    /**
     * 判断 View 是否为 TaskView 或其子类。
     * Android 16 中实际类型是 GroupedTaskView，类名检查需覆盖继承链。
     */
    private fun isTaskView(view: View): Boolean {
        var clazz: Class<*>? = view.javaClass
        while (clazz != null) {
            val name = clazz.name
            if (name == TASK_VIEW_CLASS ||
                name == GROUPED_TASK_VIEW_CLASS ||
                name.endsWith("TaskView")  // 兜底：匹配所有 *TaskView 类
            ) return true
            clazz = clazz.superclass
        }
        return false
    }

    /**
     * 估算单张卡片的宽度（含外边距）。
     * 回退到 RecentsView 宽度的 85%。
     */
    private fun getPageWidth(recentsView: ViewGroup): Int? {
        for (i in 0 until recentsView.childCount) {
            val child = recentsView.getChildAt(i)
            if (isTaskView(child)) {
                val lp = child.layoutParams
                val margin = if (lp is ViewGroup.MarginLayoutParams) {
                    lp.leftMargin + lp.rightMargin
                } else 0
                val w = child.measuredWidth + margin
                if (w > 0) return w
            }
        }
        val fallback = (recentsView.width * 0.85f).toInt()
        return if (fallback > 0) fallback else null
    }
}
