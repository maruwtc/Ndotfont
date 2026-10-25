package com.chriswong.ndotforce;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Flutter apps read their bundled fonts natively from the APK (flutter_assets/FontManifest.json),
 * never through android.graphics.Typeface. Asset lookups go through the app's AssetManager,
 * where the most recently added asset path wins. So just before the Flutter engine starts we
 * add a small zip (in the app's cache) that contains the system font under each bundled
 * font's asset name. Icon fonts are skipped. Nothing in the app itself is modified.
 */
final class FlutterFonts {

    private static final String TAG = "NdotFontForce/Flutter: ";
    private static final String[] SYSTEM_FONT_CANDIDATES = {
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/RobotoStatic-Regular.ttf",
    };
    private static final Set<AssetManager> DONE =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<AssetManager, Boolean>()));

    private FlutterFonts() { }

    static void hook(ClassLoader cl) {
        Class<?> jni = XposedHelpers.findClassIfExists("io.flutter.embedding.engine.FlutterJNI", cl);
        if (jni == null) return;                      // not a Flutter app
        XposedBridge.hookAllMethods(jni, "runBundleAndSnapshotFromLibrary", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                for (Object a : param.args) {
                    if (a instanceof AssetManager) {
                        inject((AssetManager) a);
                        break;
                    }
                }
            }
        });
        XposedBridge.log(TAG + "Flutter engine found, font redirect armed");
    }

    static void inject(AssetManager am) {
        if (!DONE.add(am)) return;
        try {
            File sys = systemFont();
            if (sys == null) { XposedBridge.log(TAG + "no readable system font"); return; }

            String manifest;
            try (InputStream in = am.open("flutter_assets/FontManifest.json")) {
                manifest = new String(readAll(in), "UTF-8");
            }
            List<String> assets = new ArrayList<>();
            JSONArray families = new JSONArray(manifest);
            for (int i = 0; i < families.length(); i++) {
                JSONObject fam = families.getJSONObject(i);
                if (Hook.isKeepName(fam.optString("family", ""))) continue;
                JSONArray fonts = fam.optJSONArray("fonts");
                if (fonts == null) continue;
                for (int j = 0; j < fonts.length(); j++) {
                    String asset = fonts.getJSONObject(j).optString("asset", "");
                    if (!asset.isEmpty() && !Hook.isKeepName(asset)) assets.add(asset);
                }
            }
            if (assets.isEmpty()) { XposedBridge.log(TAG + "no text fonts bundled"); return; }

            Context ctx = currentApplication();
            if (ctx == null) { XposedBridge.log(TAG + "no application context yet"); return; }
            File dir = ctx.getCacheDir();
            String key = Integer.toHexString((manifest + "|" + sys.length() + "|" + sys.lastModified()).hashCode());
            File zip = new File(dir, "ndotforce_flutter_" + key + ".zip");
            if (!zip.isFile()) build(dir, zip, sys, assets);

            Object cookie = XposedHelpers.callMethod(am, "addAssetPath", zip.getAbsolutePath());
            XposedBridge.log(TAG + "redirected " + assets.size() + " font file(s), cookie=" + cookie);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "failed: " + t);
        }
    }

    private static void build(File dir, File zip, File sys, List<String> assets) throws Exception {
        File[] old = dir.listFiles();
        if (old != null) for (File f : old) if (f.getName().startsWith("ndotforce_flutter_")) f.delete();

        byte[] font;
        try (InputStream in = new FileInputStream(sys)) { font = readAll(in); }
        File tmp = new File(dir, zip.getName() + ".tmp");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tmp))) {
            out.setLevel(Deflater.BEST_SPEED);    // Ndot compresses ~15x, keeps the zip tiny
            for (String a : assets) {
                out.putNextEntry(new ZipEntry("assets/flutter_assets/" + a));
                out.write(font);
                out.closeEntry();
            }
        }
        if (!tmp.renameTo(zip)) throw new IllegalStateException("rename failed");
    }

    private static File systemFont() {
        for (String p : SYSTEM_FONT_CANDIDATES) {
            File f = new File(p);
            if (f.isFile() && f.canRead()) return f;
        }
        return null;
    }

    private static Context currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            return (Context) at.getMethod("currentApplication").invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
