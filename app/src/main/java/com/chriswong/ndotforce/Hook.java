package com.chriswong.ndotforce;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.File;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * In every app selected in LSPosed / Vector, fonts the app loads itself (bundled assets,
 * res/font, files, Google Sans fetched from Play services) are swapped for the system
 * default typeface - which is Ndot CJK when the Ndot CJK module is installed.
 *
 * Icon fonts are kept: a font is only replaced when it really draws Latin text itself
 * (its "a-z" width differs from the system font) and does not turn words like "home"
 * into a single ligature glyph (Material Symbols style).
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "NdotFontForce: ";
    private static final String KEY_HINT = "ndotforce_hint";
    private static final String[] KEEP_WORDS = {
            "icon", "symbol", "awesome", "material", "glyph", "emoji", "ionic", "feather",
            "fontello", "octicon", "weather", "mdi", "fa-", "fa_", "mono", "code", "math"
    };
    private static final String LATIN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final Map<Typeface, Boolean> VERDICT =
            Collections.synchronizedMap(new WeakHashMap<Typeface, Boolean>());
    private static final ThreadLocal<Boolean> BUSY = new ThreadLocal<>();
    private static volatile boolean hooked = false;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.packageName) || "com.chriswong.ndotforce".equals(lpparam.packageName)) {
            return;
        }
        synchronized (Hook.class) {
            if (hooked) return;           // several packages can share one process
            hooked = true;
        }

        safeHook(() -> XposedHelpers.findAndHookMethod(Typeface.class, "createFromAsset",
                AssetManager.class, String.class, new Swap(1)));
        safeHook(() -> XposedHelpers.findAndHookMethod(Typeface.class, "createFromFile",
                String.class, new Swap(0)));
        safeHook(() -> XposedHelpers.findAndHookMethod(Typeface.class, "createFromFile",
                File.class, new Swap(0)));
        safeHook(() -> XposedHelpers.findAndHookMethod(Resources.class, "getFont",
                int.class, new Swap(-1)));

        // Typeface.Builder: remember the file/asset name given to the constructor
        safeHook(() -> XposedBridge.hookAllConstructors(Typeface.Builder.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                for (Object a : param.args) {
                    if (a instanceof String || a instanceof File) {
                        XposedHelpers.setAdditionalInstanceField(param.thisObject, KEY_HINT, a.toString());
                        break;
                    }
                }
            }
        }));
        safeHook(() -> XposedHelpers.findAndHookMethod(Typeface.Builder.class, "build", new Swap(-1)));

        // AndroidX / Jetpack Compose / downloadable fonts (FontsContract) on Android 10+
        safeHook(() -> XposedHelpers.findAndHookMethod(Typeface.CustomFallbackBuilder.class, "build", new Swap(-1)));

        // Flutter apps load bundled fonts natively; redirect them via the AssetManager
        safeHook(() -> FlutterFonts.hook(lpparam.classLoader));

        XposedBridge.log(TAG + "active in " + lpparam.packageName);
    }

    /** True for names of icon / symbol / monospace fonts that must not be replaced. */
    static boolean isKeepName(String name) {
        String h = name.toLowerCase(Locale.ROOT);
        for (String w : KEEP_WORDS) if (h.contains(w)) return true;
        return false;
    }

    private interface HookCall { void run() throws Throwable; }

    private static void safeHook(HookCall c) {
        try {
            c.run();
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hook failed: " + t);
        }
    }

    /** After-hook: if the returned Typeface is an app text font, return the system font instead. */
    private static final class Swap extends XC_MethodHook {
        private final int hintArg;

        Swap(int hintArg) { this.hintArg = hintArg; }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.hasThrowable() || Boolean.TRUE.equals(BUSY.get())) return;
            Object r = param.getResult();
            if (!(r instanceof Typeface)) return;
            Typeface tf = (Typeface) r;

            String hint = null;
            if (hintArg >= 0 && param.args.length > hintArg && param.args[hintArg] != null) {
                hint = param.args[hintArg].toString();
            } else if (param.thisObject != null) {
                Object h = XposedHelpers.getAdditionalInstanceField(param.thisObject, KEY_HINT);
                if (h != null) hint = h.toString();
            }

            BUSY.set(Boolean.TRUE);
            try {
                if (shouldReplace(tf, hint)) param.setResult(toSystem(tf));
            } catch (Throwable t) {
                XposedBridge.log(TAG + t);
            } finally {
                BUSY.set(Boolean.FALSE);
            }
        }
    }

    private static boolean shouldReplace(Typeface tf, String hint) {
        if (hint != null && isKeepName(hint)) return false;
        Boolean cached = VERDICT.get(tf);
        if (cached != null) return cached;

        boolean replace;
        Paint p = new Paint();
        p.setTextSize(100f);
        p.setTypeface(tf);
        if (p.hasGlyph("home") || p.hasGlyph("search") || p.hasGlyph("settings")) {
            replace = false;                         // ligature icon font
        } else {
            float own = p.measureText(LATIN);
            p.setTypeface(Typeface.create(Typeface.DEFAULT, tf.getWeight(), tf.isItalic()));
            float sys = p.measureText(LATIN);
            // Same width => the font has no Latin of its own (falls back to system): icon/symbol font
            replace = Math.abs(own - sys) > 0.5f;
        }
        VERDICT.put(tf, replace);
        return replace;
    }

    private static Typeface toSystem(Typeface tf) {
        return Typeface.create(Typeface.DEFAULT, tf.getWeight(), tf.isItalic());
    }
}
