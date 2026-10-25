package de.robv.android.xposed;

/** Compile-only stub. Real class is provided by LSPosed / Vector at runtime. */
public final class XposedHelpers {
    private XposedHelpers() { }
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        throw new UnsupportedOperationException();
    }
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        throw new UnsupportedOperationException();
    }
    public static Object callMethod(Object obj, String methodName, Object... args) {
        throw new UnsupportedOperationException();
    }
    public static Object setAdditionalInstanceField(Object obj, String key, Object value) {
        throw new UnsupportedOperationException();
    }
    public static Object getAdditionalInstanceField(Object obj, String key) {
        throw new UnsupportedOperationException();
    }
}
