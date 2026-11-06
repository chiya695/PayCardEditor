package com.luoshui.paycardeditor.hook;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Adapts the module's interceptor contract to the XposedBridge API used by LSPosed 1.x. */
public final class HookCompat {
    private HookCompat() {}

    @FunctionalInterface
    public interface Interceptor {
        Object intercept(@NonNull Chain chain) throws Throwable;
    }

    public interface Chain {
        @Nullable Object getThisObject();
        @Nullable Object getArg(int index);
        @Nullable Object proceed() throws Throwable;
    }

    public static void log(@NonNull HookRuntime module, int priority, @NonNull String tag, @NonNull String message) {
        Log.println(priority, tag, message);
    }

    public static void hook(
            @NonNull HookRuntime module,
            @NonNull Method method,
            @NonNull Interceptor interceptor
    ) {
        XposedBridge.hookMethod(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam callback) throws Throwable {
                HookChain chain = new HookChain(callback);
                try {
                    callback.setResult(interceptor.intercept(chain));
                } catch (Throwable failure) {
                    if (chain.proceeded) {
                        if (chain.failure != null) callback.setThrowable(chain.failure);
                        else callback.setResult(chain.result);
                        return;
                    }
                    try {
                        callback.setResult(XposedBridge.invokeOriginalMethod(
                                callback.method, callback.thisObject, callback.args));
                    } catch (InvocationTargetException exception) {
                        callback.setThrowable(exception.getCause() != null ? exception.getCause() : exception);
                    } catch (Throwable originFailure) {
                        callback.setThrowable(originFailure);
                    }
                }
            }
        });
    }

    private static final class HookChain implements Chain {
        private final XC_MethodHook.MethodHookParam callback;
        private boolean proceeded;
        private Object result;
        private Throwable failure;

        private HookChain(XC_MethodHook.MethodHookParam callback) {
            this.callback = callback;
        }

        @Override public Object getThisObject() { return callback.thisObject; }
        @Override public Object getArg(int index) { return callback.args[index]; }

        @Override
        public Object proceed() throws Throwable {
            proceeded = true;
            try {
                result = XposedBridge.invokeOriginalMethod(callback.method, callback.thisObject, callback.args);
                return result;
            } catch (InvocationTargetException exception) {
                failure = exception.getCause() != null ? exception.getCause() : exception;
                throw failure;
            } catch (Throwable throwable) {
                failure = throwable;
                throw throwable;
            }
        }
    }
}
