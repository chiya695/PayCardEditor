package com.luoshui.paycardeditor.hook

import android.util.Log
import com.luoshui.paycardeditor.core.HookEnvironment
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Method

class ModuleMain : IXposedHookLoadPackage, HookRuntime {

    companion object {
        private const val TAG = "PayCardEditorHook"

        init {
            System.loadLibrary("dexkit")
        }
    }

    private val installer = PayCardHookInstaller(this)

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != HookEnvironment.TARGET_PACKAGE) return
        if (param.processName != HookEnvironment.TARGET_PACKAGE) {
            HookCompat.log(this, Log.INFO, TAG, "skip hook install in subprocess ${param.processName}")
            return
        }
        HookCompat.log(this, Log.INFO, TAG, "legacy Xposed entry loaded in ${param.processName}; framework=${getFrameworkName()} ${getFrameworkVersion()}")
        installer.install(param.classLoader, param.appInfo.sourceDir.orEmpty())
    }

    override fun deoptimize(method: Method): Boolean {
        return try {
            val deoptimize = XposedBridge::class.java.getMethod("deoptimizeMethod", java.lang.reflect.Member::class.java)
            deoptimize.invoke(null, method) as? Boolean ?: true
        } catch (_: NoSuchMethodException) {
            false
        }
    }

    override fun getApiVersion(): String = "XposedBridge API 82"
    override fun getFrameworkName(): String = "LSPosed / XposedBridge"
    override fun getFrameworkVersion(): String = XposedBridge.getXposedVersion().toString()
    override fun getFrameworkVersionCode(): Int = XposedBridge.getXposedVersion()
}
