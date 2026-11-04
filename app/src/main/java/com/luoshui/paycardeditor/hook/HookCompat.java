package com.luoshui.paycardeditor.hook;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** Adapts the API 100 before-hook contract and module logging. */
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

    public static void log(@NonNull XposedModule module, int priority, @NonNull String tag, @NonNull String message) {
        String line = tag + ": " + message;
        module.log(line);
    }
    public static void hook(
            @NonNull XposedModule module,
            @NonNull Method method,
            @NonNull Interceptor interceptor
    ) {
        module.hook(method, new XposedInterface.Hooker<Method>() {
            @Override
            public void before(@NonNull XposedInterface.BeforeHookCallback<Method> callback) {
                HookChain chain = new HookChain(callback);
                try {
                    Object result = interceptor.intercept(chain);
                    callback.returnAndSkip(result);
                } catch (Throwable failure) {
                    if (chain.proceeded) {
                        if (chain.failure != null) callback.throwAndSkip(chain.failure);
                        else callback.returnAndSkip(chain.result);
                        return;
                    }
                    try {
                        callback.returnAndSkip(callback.invokeOrigin());
                    } catch (InvocationTargetException exception) {
                        callback.throwAndSkip(exception.getCause() != null ? exception.getCause() : exception);
                    } catch (Throwable originFailure) {
                        callback.throwAndSkip(originFailure);
                    }
                }
            }

            @Override
            public void after(@NonNull XposedInterface.AfterHookCallback<Method> callback) {
                // All adapted interceptors run in before(); the original is explicitly invoked there.
            }
        });
    }

    private static final class HookChain implements Chain {
        private final XposedInterface.BeforeHookCallback<Method> callback;
        private boolean proceeded;
        private Object result;
        private Throwable failure;

        private HookChain(XposedInterface.BeforeHookCallback<Method> callback) {
            this.callback = callback;
        }

        @Override
        public Object getThisObject() {
            return callback.getThis();
        }

        @Override
        public Object getArg(int index) {
            return callback.getArg(index);
        }

        @Override
        public Object proceed() throws Throwable {
            proceeded = true;
            try {
                result = callback.invokeOrigin();
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