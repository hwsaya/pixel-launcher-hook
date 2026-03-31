package dev.saya.stackedrecents

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 模块入口。
 * LSPosed 会在目标 App 启动时调用 handleLoadPackage。
 * 我们只在 Pixel Launcher 进程里注入，避免影响其他进程。
 */
class MainHook : IXposedHookLoadPackage {

    companion object {
        // PixelOS 使用 Google 版 Pixel Launcher
        // AOSP 原生或其他 ROM 可换成 com.android.launcher3
        private const val TARGET_PACKAGE = "com.google.android.apps.nexuslauncher"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        XposedBridge.log("[StackedRecents] Injecting into $TARGET_PACKAGE")

        try {
            RecentsHook.install(lpparam.classLoader)
        } catch (e: Throwable) {
            XposedBridge.log("[StackedRecents] Hook failed: ${e.message}")
        }
    }
}
