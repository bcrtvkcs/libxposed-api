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
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import io.github.libxposed.api.utils.DexParser;

/**
 * Wrap of {@link XposedInterface} used by the modules for the purpose of shielding framework implementation details.
 */
public class XposedInterfaceWrapper implements XposedInterface {

    private XposedInterface mBase;

    /**
     * Attaches the framework interface to the module. Modules should never call this method.
     *
     * @param base The framework interface
     */
    @SuppressWarnings("unused")
    public final void attachFramework(@NonNull XposedInterface base) {
        if (mBase != null) {
            throw new IllegalStateException("Framework already attached");
        }
        mBase = base;
    }

    private void ensureAttached() {
        if (mBase == null) {
            throw new IllegalStateException("Framework not attached");
        }
    }

    @Override
    public final int getApiVersion() {
        ensureAttached();
        return mBase.getApiVersion();
    }

    @NonNull
    @Override
    public final String getFrameworkName() {
        ensureAttached();
        return mBase.getFrameworkName();
    }

    @NonNull
    @Override
    public final String getFrameworkVersion() {
        ensureAttached();
        return mBase.getFrameworkVersion();
    }

    @Override
    public final long getFrameworkVersionCode() {
        ensureAttached();
        return mBase.getFrameworkVersionCode();
    }

    @Override
    public final long getFrameworkCapabilities() {
        ensureAttached();
        return mBase.getFrameworkCapabilities();
    }

    @NonNull
    @Override
    public final HookHandle<Method> hook(@NonNull Method origin, @NonNull MethodHooker hooker) {
        ensureAttached();
        return mBase.hook(origin, hooker);
    }

    @NonNull
    @Override
    public final <T> HookHandle<Constructor<T>> hook(@NonNull Constructor<T> origin, @NonNull CtorHooker<T> hooker) {
        ensureAttached();
        return mBase.hook(origin, hooker);
    }

    @NonNull
    @Override
    public HookHandle<Method> hook(@NonNull Method origin, int priority, @NonNull MethodHooker hooker) {
        ensureAttached();
        return mBase.hook(origin, priority, hooker);
    }

    @NonNull
    @Override
    public final HookHandle<Method> hookClassInitializer(@NonNull Class<?> origin, @NonNull MethodHooker hooker) {
        ensureAttached();
        return mBase.hookClassInitializer(origin, hooker);
    }

    @NonNull
    @Override
    public <T> HookHandle<Constructor<T>> hook(@NonNull Constructor<T> origin, int priority, @NonNull CtorHooker<T> hooker) {
        ensureAttached();
        return mBase.hook(origin, priority, hooker);
    }

    @Override
    public final boolean deoptimize(@NonNull Executable executable) {
        ensureAttached();
        return mBase.deoptimize(executable);
    }

    @NonNull
    @Override
    public HookHandle<Method> hookClassInitializer(@NonNull Class<?> origin, int priority, @NonNull MethodHooker hooker) {
        ensureAttached();
        return mBase.hookClassInitializer(origin, priority, hooker);
    }

    @NonNull
    @Override
    public MethodInvoker getInvoker(@NonNull Method method, @Nullable Integer priority) {
        ensureAttached();
        return mBase.getInvoker(method, priority);
    }

    @NonNull
    @Override
    public <T> CtorInvoker<T> getInvoker(@NonNull Constructor<T> constructor, @Nullable Integer priority) {
        ensureAttached();
        return mBase.getInvoker(constructor, priority);
    }

    @Override
    public final void log(int priority, @Nullable String tag, @NonNull String msg) {
        ensureAttached();
        mBase.log(priority, tag, msg, null);
    }

    @Override
    public final void log(int priority, @Nullable String tag, @NonNull String msg, @Nullable Throwable tr) {
        ensureAttached();
        mBase.log(priority, tag, msg, tr);
    }

    @Nullable
    @Override
    public final DexParser parseDex(@NonNull ByteBuffer dexData, boolean includeAnnotations) throws IOException {
        ensureAttached();
        return mBase.parseDex(dexData, includeAnnotations);
    }

    @NonNull
    @Override
    public final SharedPreferences getRemotePreferences(@NonNull String name) {
        ensureAttached();
        return mBase.getRemotePreferences(name);
    }

    @NonNull
    @Override
    public final ApplicationInfo getApplicationInfo() {
        ensureAttached();
        return mBase.getApplicationInfo();
    }

    @NonNull
    @Override
    public final String[] listRemoteFiles() {
        ensureAttached();
        return mBase.listRemoteFiles();
    }

    @NonNull
    @Override
    public final ParcelFileDescriptor openRemoteFile(@NonNull String name) throws FileNotFoundException {
        ensureAttached();
        return mBase.openRemoteFile(name);
    }
}
