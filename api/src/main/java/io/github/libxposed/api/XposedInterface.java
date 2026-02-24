package io.github.libxposed.api;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import io.github.libxposed.api.errors.HookFailedError;
import io.github.libxposed.api.utils.DexParser;

/**
 * Xposed interface for modules to operate on application processes.
 */
@SuppressWarnings("unused")
public interface XposedInterface {
    /**
     * The framework has the capability to hook system_server and other system processes.
     */
    long CAP_SYSTEM = 1L;
    /**
     * The framework provides remote preferences and remote files support.
     */
    long CAP_REMOTE = 1L << 1;
    /**
     * The framework allows dynamically loaded code to use Xposed APIs.
     */
    long CAP_RT_DYNAMIC_CODE_API_ACCESS = 1L << 2;

    /**
     * The default hook priority.
     */
    int PRIORITY_DEFAULT = 50;
    /**
     * Execute the hook callback late.
     */
    int PRIORITY_LOWEST = -10000;
    /**
     * Execute the hook callback early.
     */
    int PRIORITY_HIGHEST = 10000;

    /**
     * Contextual interface for before invocation callbacks.
     */
    interface BeforeHookCallback<T extends Executable> {
        /**
         * Gets the method / constructor being hooked.
         */
        @NonNull
        T getExecutable();

        /**
         * Gets the {@code this} object, or {@code null} if the method is static.
         */
        @Nullable
        Object getThisObject();

        /**
         * Gets the arguments passed to the method / constructor. You can modify the arguments.
         */
        @NonNull
        Object[] getArgs();

        /**
         * Sets the return value of the method and skip the invocation. If the procedure is a constructor,
         * the {@code result} param will be ignored.
         * Note that the after invocation callback will still be called.
         *
         * @param result The return value
         */
        void returnAndSkip(@Nullable Object result);

        /**
         * Throw an exception from the method / constructor and skip the invocation.
         * Note that the after invocation callback will still be called.
         *
         * @param throwable The exception to be thrown
         */
        void throwAndSkip(@Nullable Throwable throwable);
    }

    /**
     * Contextual interface for after invocation callbacks.
     */
    interface AfterHookCallback<T extends Executable> {
        /**
         * Gets the method / constructor being hooked.
         */
        @NonNull
        T getExecutable();

        /**
         * Gets the {@code this} object, or {@code null} if the method is static.
         */
        @Nullable
        Object getThisObject();

        /**
         * Gets all arguments passed to the method / constructor.
         */
        @NonNull
        Object[] getArgs();

        /**
         * Gets the return value of the method or the before invocation callback. If the procedure is a
         * constructor, a void method or an exception was thrown, the return value will be {@code null}.
         */
        @Nullable
        Object getResult();

        /**
         * Gets the exception thrown by the method / constructor or the before invocation callback. If the
         * procedure call was successful, the return value will be {@code null}.
         */
        @Nullable
        Throwable getThrowable();

        /**
         * Gets whether the invocation was skipped by the before invocation callback.
         */
        boolean isSkipped();

        /**
         * Sets the return value of the method and skip the invocation. If the procedure is a constructor,
         * the {@code result} param will be ignored.
         *
         * @param result The return value
         */
        void setResult(@Nullable Object result);

        /**
         * Sets the exception thrown by the method / constructor.
         *
         * @param throwable The exception to be thrown.
         */
        void setThrowable(@Nullable Throwable throwable);
    }

    /**
     * Interface for method / constructor hooking.
     *
     * <p>Example usage:</p>
     *
     * <pre>{@code
     *   public class ExampleHooker implements SimpleHooker<Method> {
     *
     *       @Override
     *       void before(@NonNull BeforeHookCallback<Method> callback) {
     *           // Pre-hooking logic goes here
     *       }
     *
     *       @Override
     *       void after(@NonNull AfterHookCallback<Method> callback) {
     *           // Post-hooking logic goes here
     *       }
     *   }
     *
     *   public class ExampleHookerWithContext implements ContextualHooker<Method, MyContext> {
     *
     *       @Override
     *       MyContext before(@NonNull BeforeHookCallback<Method> callback) {
     *           // Pre-hooking logic goes here
     *           return new MyContext();
     *       }
     *
     *       @Override
     *       void after(@NonNull AfterHookCallback<Method> callback, MyContext context) {
     *           // Post-hooking logic goes here
     *       }
     *   }
     * }</pre>
     */
    interface Hooker<T extends Executable> {
        /**
         * Returns the priority of the hook. Hooks with higher priority will be executed first. The default
         * priority is {@link #PRIORITY_DEFAULT}. Make sure the value is consistent after the hooker is installed,
         * otherwise the behavior is undefined.
         *
         * @return The priority of the hook
         */
        default int getPriority() {
            return PRIORITY_DEFAULT;
        }
    }

    /**
     * A hooker without context.
     */
    interface SimpleHooker<T extends Executable> extends Hooker<T> {

        default void before(@NonNull BeforeHookCallback<T> callback) {
        }

        default void after(@NonNull AfterHookCallback<T> callback) {
        }
    }

    /**
     * A hooker with context. The context object is guaranteed to be the same between before and after
     * invocation.
     *
     * @param <C> The type of the context
     */
    interface ContextualHooker<T extends Executable, C> extends Hooker<T> {
        default C before(@NonNull BeforeHookCallback<T> callback) {
            return null;
        }

        default void after(@NonNull AfterHookCallback<T> callback, @Nullable C context) {
        }
    }

    /**
     * Handle for a hook.
     *
     * @param <T> {@link Method} or {@link Constructor}
     */
    interface HookHandle<T extends Executable> {
        /**
         * Gets the method / constructor being hooked.
         */
        @NonNull
        T getExecutable();

        /**
         * Cancels the hook. The behavior of calling this method multiple times is undefined.
         */
        void unhook();
    }

    /**
     * Handle for a method hook.
     */
    interface MethodHookHandle extends HookHandle<Method> {
        /**
         * Invoke the original method, but keeps all higher priority hooks.
         *
         * @param thisObject For non-static calls, the {@code this} pointer, otherwise {@code null}
         * @param args       The arguments used for the method call
         * @return The result returned from the invoked method
         * @see Method#invoke(Object, Object...)
         */
        @Nullable
        Object invokeOrigin(@Nullable Object thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;
    }

    /**
     * Handle for a constructor hook.
     *
     * @param <T> The type of the constructor
     */
    interface CtorHookHandle<T> extends HookHandle<Constructor<T>> {
        /**
         * Invoke the original constructor as a method, but keeps all higher priority hooks.
         *
         * @param thisObject The instance to be constructed
         * @param args       The arguments used for the construction
         * @see Constructor#newInstance(Object...)
         */
        void invokeOrigin(@NonNull T thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;

        /**
         * Invoke the original constructor, but keeps all higher priority hooks.
         *
         * @param args The arguments used for the construction
         * @return The instance created and initialized by the constructor
         * @see Constructor#newInstance(Object...)
         */
        @NonNull
        T newInstanceOrigin(Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException;
    }

    /**
     * Gets the Xposed API version of current implementation.
     *
     * @return API version
     */
    int getApiVersion();

    /**
     * Gets the Xposed framework name of current implementation.
     *
     * @return Framework name
     */
    @NonNull
    String getFrameworkName();

    /**
     * Gets the Xposed framework version of current implementation.
     *
     * @return Framework version
     */
    @NonNull
    String getFrameworkVersion();

    /**
     * Gets the Xposed framework version code of current implementation.
     *
     * @return Framework version code
     */
    long getFrameworkVersionCode();

    /**
     * Gets the Xposed framework capabilities.
     * Capabilities with prefix CAP_RT_ may change among launches.
     *
     * @return Framework capabilities
     */
    long getFrameworkCapabilities();

    /**
     * Hook a method.
     *
     * @param origin The method to be hooked
     * @param hooker The hooker object
     * @return Handle for the hook
     * @throws IllegalArgumentException if origin is abstract, framework internal or {@link Method#invoke},
     *                                  or hooker is invalid
     * @throws HookFailedError          if hook fails due to framework internal error
     */
    @NonNull
    MethodHookHandle hook(@NonNull Method origin, @NonNull Hooker<Method> hooker);

    /**
     * Hook a constructor.
     *
     * @param origin The constructor to be hooked
     * @param hooker The hooker object
     * @return Handle for the hook
     * @throws IllegalArgumentException if origin is framework internal or {@link Constructor#newInstance},
     *                                  or hooker is invalid
     * @throws HookFailedError          if hook fails due to framework internal error
     */
    @NonNull
    <T> CtorHookHandle<T> hook(@NonNull Constructor<T> origin, @NonNull Hooker<Constructor<T>> hooker);

    /**
     * Hook the static initializer of a class.
     * <p>
     * Note: If the class is initialized, the hook will never be called.
     * </p>
     *
     * @param origin The class to be hooked
     * @param hooker The hooker object
     * @return Handle for the hook
     * @throws IllegalArgumentException if class has no static initializer or hooker is invalid
     * @throws HookFailedError          if hook fails due to framework internal error
     */
    @NonNull
    MethodHookHandle hookClassInitializer(@NonNull Class<?> origin, @NonNull Hooker<Method> hooker);

    /**
     * Deoptimizes a method / constructor in case hooked callee is not called because of inline.
     *
     * <p>By deoptimizing the method, the method will back all callee without inlining.
     * For example, when a short hooked method B is invoked by method A, the callback to B is not invoked
     * after hooking, which may mean A has inlined B inside its method body. To force A to call the hooked B,
     * you can deoptimize A and then your hook can take effect.</p>
     *
     * <p>Generally, you need to find all the callers of your hooked callee and that can be hardly achieve
     * (but you can still search all callers by using {@link DexParser}). Use this method if you are sure
     * the deoptimized callers are all you need. Otherwise, it would be better to change the hook point or
     * to deoptimize the whole app manually (by simply reinstalling the app without uninstall).</p>
     *
     * @param executable The method / constructor to deoptimize
     * @return Indicate whether the deoptimizing succeed or not
     */
    boolean deoptimize(@NonNull Executable executable);

    /**
     * Basically the same as {@link Method#invoke(Object, Object...)}, but skips all Xposed hooks.
     * If you do not want to skip higher priority hooks, use {@link MethodHookHandle#invokeOrigin(Object, Object...)} instead.
     *
     * @param method     The method to be called
     * @param thisObject For non-static calls, the {@code this} pointer, otherwise {@code null}
     * @param args       The arguments used for the method call
     * @return The result returned from the invoked method
     * @see Method#invoke(Object, Object...)
     */
    @Nullable
    Object invokeOrigin(@NonNull Method method, @Nullable Object thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;

    /**
     * Invoke the constructor as a method, but skips all Xposed hooks.
     * If you do not want to skip higher priority hooks, use {@link CtorHookHandle#invokeOrigin(Object, Object...)} instead.
     *
     * @param constructor The constructor to create and initialize a new instance
     * @param thisObject  The instance to be constructed
     * @param args        The arguments used for the construction
     * @param <T>         The type of the instance
     * @see Constructor#newInstance(Object...)
     */
    <T> void invokeOrigin(@NonNull Constructor<T> constructor, @NonNull T thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;

    /**
     * Basically the same as {@link Constructor#newInstance(Object...)}, but skips all Xposed hooks.
     * If you do not want to skip higher priority hooks, use {@link CtorHookHandle#newInstanceOrigin(Object...)} instead.
     *
     * @param <T>         The type of the constructor
     * @param constructor The constructor to create and initialize a new instance
     * @param args        The arguments used for the construction
     * @return The instance created and initialized by the constructor
     * @see Constructor#newInstance(Object...)
     */
    @NonNull
    <T> T newInstanceOrigin(@NonNull Constructor<T> constructor, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException;

    /**
     * Invokes a special (non-virtual) method on a given object instance, similar to the functionality of
     * {@code CallNonVirtual<type>Method} in JNI, which invokes an instance (nonstatic) method on a Java
     * object. This method is useful when you need to call a specific method on an object, bypassing any
     * overridden methods in subclasses and directly invoking the method defined in the specified class.
     *
     * <p>This method is useful when you need to call {@code super.xxx()} in a hooked constructor.</p>
     *
     * @param method     The method to be called
     * @param thisObject For non-static calls, the {@code this} pointer, otherwise {@code null}
     * @param args       The arguments used for the method call
     * @return The result returned from the invoked method
     * @see Method#invoke(Object, Object...)
     */
    @Nullable
    Object invokeSpecial(@NonNull Method method, @NonNull Object thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;

    /**
     * Invokes a special (non-virtual) method on a given object instance, similar to the functionality of
     * {@code CallNonVirtual<type>Method} in JNI, which invokes an instance (nonstatic) method on a Java
     * object. This method is useful when you need to call a specific method on an object, bypassing any
     * overridden methods in subclasses and directly invoking the method defined in the specified class.
     *
     * <p>This method is useful when you need to call {@code super.xxx()} in a hooked constructor.</p>
     *
     * @param constructor The constructor to create and initialize a new instance
     * @param thisObject  The instance to be constructed
     * @param args        The arguments used for the construction
     * @see Constructor#newInstance(Object...)
     */
    <T> void invokeSpecial(@NonNull Constructor<T> constructor, @NonNull T thisObject, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException;

    /**
     * Creates a new instance of the given subclass, but initialize it with a parent constructor. This could
     * leave the object in an invalid state, where the subclass constructor are not called and the fields
     * of the subclass are not initialized.
     *
     * <p>This method is useful when you need to initialize some fields in the subclass by yourself.</p>
     *
     * @param <T>         The type of the parent constructor
     * @param <U>         The type of the subclass
     * @param constructor The parent constructor to initialize a new instance
     * @param subClass    The subclass to create a new instance
     * @param args        The arguments used for the construction
     * @return The instance of subclass initialized by the constructor
     * @see Constructor#newInstance(Object...)
     */
    @NonNull
    <T, U> U newInstanceSpecial(@NonNull Constructor<T> constructor, @NonNull Class<U> subClass, Object... args) throws InvocationTargetException, IllegalArgumentException, IllegalAccessException, InstantiationException;

    /**
     * Writes a message to the Xposed log.
     *
     * @param priority The log priority, see {@link android.util.Log}
     * @param tag      The log tag
     * @param msg      The log message
     */
    default void log(int priority, @Nullable String tag, @NonNull String msg) {
        log(priority, tag, msg, null);
    }

    /**
     * Writes a message to the Xposed log.
     *
     * @param priority The log priority, see {@link android.util.Log}
     * @param tag      The log tag
     * @param msg      The log message
     * @param tr       An exception to log
     */
    void log(int priority, @Nullable String tag, @NonNull String msg, @Nullable Throwable tr);

    /**
     * Parse a dex file in memory.
     *
     * @param dexData            The content of the dex file
     * @param includeAnnotations Whether to include annotations
     * @return The {@link DexParser} of the dex file
     * @throws IOException if the dex file is invalid
     */
    @Nullable
    DexParser parseDex(@NonNull ByteBuffer dexData, boolean includeAnnotations) throws IOException;

    /**
     * Gets the application info of the module.
     */
    @NonNull
    ApplicationInfo getApplicationInfo();

    /**
     * Gets remote preferences stored in Xposed framework. Note that those are read-only in hooked apps.
     *
     * @param group Group name
     * @return The preferences
     * @throws UnsupportedOperationException If the framework is embedded
     */
    @NonNull
    SharedPreferences getRemotePreferences(@NonNull String group);

    /**
     * List all files in the module's shared data directory.
     *
     * @return The file list
     * @throws UnsupportedOperationException If the framework is embedded
     */
    @NonNull
    String[] listRemoteFiles();

    /**
     * Open a file in the module's shared data directory. The file is opened in read-only mode.
     *
     * @param name File name, must not contain path separators and . or ..
     * @return The file descriptor
     * @throws FileNotFoundException         If the file does not exist or the path is forbidden
     * @throws UnsupportedOperationException If the framework is embedded
     */
    @NonNull
    ParcelFileDescriptor openRemoteFile(@NonNull String name) throws FileNotFoundException;
}
