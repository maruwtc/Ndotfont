package de.robv.android.xposed;

import java.util.Set;

/** Compile-only stub. Real class is provided by LSPosed / Vector at runtime. */
public final class XposedBridge {
    private XposedBridge() { }
    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        throw new UnsupportedOperationException();
    }
    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        throw new UnsupportedOperationException();
    }
    public static void log(String text) { }
}
