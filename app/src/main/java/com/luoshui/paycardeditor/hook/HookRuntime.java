package com.luoshui.paycardeditor.hook;

import java.lang.reflect.Method;

/** Runtime services shared by the legacy Xposed entry point and hook installers. */
public interface HookRuntime {
    boolean deoptimize(Method method);
    String getApiVersion();
    String getFrameworkName();
    String getFrameworkVersion();
    int getFrameworkVersionCode();
}
