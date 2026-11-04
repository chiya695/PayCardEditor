package com.luoshui.paycardeditor.hook

import android.util.Log
import com.luoshui.paycardeditor.core.HookEnvironment
import io.github.libxposed.api.XposedContext
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

class ModuleMain(base: XposedContext, param: ModuleLoadedParam) : XposedModule(base, param) {

    companion object {
        private const val TAG = "PayCardEditorHook"
        init {
            System.loadLibrary("dexkit")
        }
    }

    private val installer = PayCardHookInstaller(this)
    private val processName = param.processName

    init {
        HookCompat.log(this, Log.INFO, TAG, "module loaded in $processName; framework=$frameworkName $frameworkVersion")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (!param.isFirstPackage || param.packageName != HookEnvironment.TARGET_PACKAGE) return
        if (processName.isNotEmpty() && processName != HookEnvironment.TARGET_PACKAGE) {
            HookCompat.log(this, Log.INFO, TAG, "skip hook install in subprocess $processName")
            return
        }
        installer.install(param.classLoader, param.appInfo.sourceDir.orEmpty())
    }
}