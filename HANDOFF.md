# Ndot CJK — Handoff

Make Nothing's **Ndot** dot-matrix font the whole-system font on a rooted Pixel, with
readable **Traditional + Simplified Chinese** (plus Japanese/Korean) drawn in the same
dot style. The project has three parts:

| Part | Folder | Status |
|---|---|---|
| Font build (Python/fontTools) | `font-build/` | Done, v2.1 font in use |
| KernelSU module (system font + emoji) | `ksu-module/` | v1.7 installed and working |
| LSPosed/Vector module (apps with their own fonts) | `lsposed-module/` | v1.1 confirmed working, incl. Flutter (Immich); v1.2 adds an app icon |

---

## 1. Target device

- Pixel, codename **blazer**, Android 16
- **KernelSU-Next v3.4.0** (built-in GKI2), Hook mode Hybrid, **Metamodule: not installed**
- SuSFS v2.3.0, ReZygisk, **Vector** (LSPosed fork, `zygisk_vector`)
- Other modules: Iconify, PixelXpert-Next, TA_utl, mountify, playintegrityfix, specter,
  susfs4ksu, tricky_store, zygisksu. **iOS_Emoji_KSU should be uninstalled**
  (its emoji now lives in this module; see §5).
- KernelSU setting **"Umount modules" must be OFF**. Otherwise every non-root app,
  SystemUI included, gets module mounts removed and keeps the stock font.

## 2. Font build (`font-build/`)

Output: `NdotCJK-Regular.ttf` (~19.8 MB, UPM 1000). One file serves every font name on the ROM.

- **Latin, Hangul, rare CJK:** Ndot 77 JP Extended (from `nothingfont-main.zip`, OFL;
  includes Misaki / Miseki / QuanPixel)
- **CJK main set:** Ark Pixel Font **12px** glyph PNGs (TakWolf, OFL), 22,237 chars,
  regional variants HK/TW/CN/JA/KO
- **Gap fill, in priority order:**
  1. Cubic 11 (989 chars)
  2. Boutique Bitmap 9×9 (494 chars)
  3. Ndot77 7×7 (5,234 chars, incl. Hangul)
  4. Boutique Bitmap 7×7 (135 chars)
- **Dot rendering:**
  - Every pixel is a round dot: pitch **80** units, diameter **72**.
  - Glyphs are **composites** referencing one shared `ndotdot` glyph, which keeps the file small.
  - Coarser fallback grids are centred in the ideograph box with proportionally larger dots.
- **Default forms are Hong Kong.** A `locl` GSUB feature switches to:
  - **ZHS** (zh-Hans): 6,731 variants
  - **ZHT** (zh-TW): 1,249 variants
  - **JAN**: 3,472 variants
  - **KOR**: 3,050 variants
- **Removed from the font:**
  - BMP codepoints with Emoji_Presentation (☕ ⚡ ✅ …), so colour emoji still win
  - the Private Use Area
  - U+3031/3032, which hung ~1,000 units below the baseline
- **Metrics:**
  - hhea/typo ascent/descent 928 / −244
  - head yMin/yMax forced to Roboto's −271 / 1056
  - Fixes extra bottom space caused by Android's `includeFontPadding`.
- **Name:** family "Ndot CJK", PostScript `NdotCJK-Regular`, version 2.0

### Rebuild

```bash
# sources
git clone --depth 1 https://github.com/TakWolf/ark-pixel-font            # 12px PNGs (full clone is fast; blobless is very slow)
git clone --depth 1 https://github.com/TakWolf/fusion-pixel-font          # assets/fonts: cubic-11, boutique-bitmap-9x9/7x7
pip install fonttools brotli pillow                                        # brotli needed for Cubic_11.woff2; Pillow needs raqm for locl tests

python3 font-build/build_font2.py \
  <nothingfont>/fonts/Ndot77JPExtended.ttf \
  ark-pixel-font \
  fusion-pixel-font/assets/fonts/cubic-11/Cubic_11.woff2 \
  fusion-pixel-font/assets/fonts/boutique-bitmap-9x9/BoutiqueBitmap9x9.ttf \
  fusion-pixel-font/assets/fonts/boutique-bitmap-7x7/BoutiqueBitmap7x7.ttf \
  NdotCJK-Regular-v2.ttf                 # ~6 min (pure-Python rasterising)
python3 font-build/fix_bbox.py NdotCJK-Regular-v2.ttf NdotCJK-Regular-v2.1.ttf
cp NdotCJK-Regular-v2.1.ttf ksu-module/fonts/NdotCJK-Regular.ttf
```

- **Tunables** at the top of `build_font2.py`:
  - `P` (pitch), `DOT` (diameter)
  - `BASE_ROW`
  - `CJK_RANGES`
- **Cubic 11 alignment:** auto-calibrated against Ark glyphs (dx −1, dy 0).
- **Superseded:** `font-build/build_font.py` is the v1 build (7×7 only).

## 3. KernelSU module (`ksu-module/`, id `ndot_cjk_font`, v1.7)

**In the repo:** recovered from the phone's installed copy (`/data/adb/modules/ndot_cjk_font`).
`customize.sh`, `README.md` and `META-INF/` were not kept by KernelSU at install, so they are
**missing**: a v1.7 zip can't be packaged until they are recovered (from the v1.6 zip or the chat
that built it). Until then, update the installed module by copying the changed scripts:
```bash
adb push ksu-module/post-fs-data.sh ksu-module/action.sh ksu-module/module.prop /sdcard/
adb shell su -c "cp /sdcard/post-fs-data.sh /sdcard/action.sh /sdcard/module.prop /data/adb/modules/ndot_cjk_font/ && rm -f /data/adb/modules/ndot_cjk_font/system/fonts/NotoColorEmojiFlags.ttf"
```
The text below describes `customize.sh` from the original build.


Works on KernelSU Next and SukiSU Ultra; Magisk-compatible.

- **`customize.sh`** runs at install:
  - **Finds font files:** scans `/system/fonts`, `/product/fonts` and `/system_ext/fonts` for
    known UI font names (AOSP Roboto*, NotoSerif*, DroidSans*; Pixel GoogleSans*; MIUI/HyperOS;
    ColorOS; One UI; vivo; Honor).
  - **Skips** names containing Mono, Emoji, CJK, Symbol or Icon.
  - **Hard-links** every target name to `fonts/NdotCJK-Regular.ttf`, falling back to cp.
  - **Writes** `replaced.txt` as `<device path>|<source rel path>`.
  - **Optional emoji**, taken from the first of:
    - `emoji/Emoji.ttf` in the zip
    - `/sdcard/NdotCJK/Emoji.ttf`
    - `/sdcard/Download/Emoji.ttf`
    - another installed module's `system/fonts/NotoColorEmoji.ttf` (e.g. iOS_Emoji_KSU)
  - The emoji must be CBDT or COLR; Apple's sbix format doesn't render on Android.
  - **Re-enables** Google Play services' `fonts.provider.FontsProvider` and
    `fonts.update.UpdateSchedulerService` (see §5).
- **`post-fs-data.sh`:**
  - **Bind-mounts** every entry of `replaced.txt` itself, so no metamodule is needed.
    It skips a target already mounted (same size).
  - **Symlinks** (v1.7): names like `DroidSans.ttf` are symlinks to `Roboto-Regular.ttf`. They are
    resolved with `readlink -f` and sizes read with `stat -L`, and each real file is mounted
    once. Before v1.7, `stat` read the symlink itself, so DroidSans showed STOCK and was
    bind-mounted onto Roboto again every boot.
  - **Flags** (v1.7): `*EmojiFlags*` entries are skipped, so stock Noto flags stay. Old
    `replaced.txt` files from v1.6 still list it; the skip covers them.
    `customize.sh` (not in the repo) should also stop matching `NotoColorEmojiFlags.ttf`.
  - **Emoji:** mounts the emoji over `/data/fonts/files/*/*Emoji*.ttf`. If no custom emoji was
    supplied, it mounts another emoji module's file early instead.
  - **Log:** written to `mount.log`.
- **`service.sh`:**
  - **Custom emoji:** binds it over Google Play services' `Noto_COLR_Emoji_Compat` file.
  - **Optional Google Sans override:** off unless the file `gms_fonts` exists. **It didn't work
    on this device**; the user removed the flag. Leave it off and use the LSPosed module.
- **`action.sh`:**
  - Shows each target as LIVE/STOCK (by size, following symlinks), emoji status and override state.
  - Prints the boot log.
- **`uninstall.sh`:** clears the MIUI theme font cache.
- **Package:**
  ```bash
  cd ksu-module && zip -X -r -9 ../NdotCJK-KSUNext-vX.Y.zip module.prop customize.sh post-fs-data.sh service.sh action.sh uninstall.sh README.md OFL.txt fonts META-INF
  ```
  Bump `version` and `versionCode` in `module.prop` each release.

**How CJK reaches every app:**
- Minikin always uses the **first** font family for a character it covers (kFirstFontScore).
- Making the default sans-serif (Roboto) contain CJK therefore puts Chinese in Ndot everywhere.
- Noto CJK is left untouched as the fallback for anything Ndot lacks.

## 4. LSPosed / Vector module (`lsposed-module/`, `com.chriswong.ndotforce`)

An Android Studio project (AGP 8.5.2, Gradle 8.7, compileSdk 34, minSdk 29, Java 17).
- **Xposed API:** comes from compile-only stubs in `:xposed-stubs`, so no api.xposed.info repo is
  needed. Their signatures must stay identical to the real Xposed API.
- **Build:** Android Studio → Build APK(s) → `app/build/outputs/apk/debug/app-debug.apk`.
  The user builds it on their Windows PC, where the Android SDK lives under
  `%LOCALAPPDATA%\Android\Sdk`.

`Hook.java`, in scoped apps:
- **Hooks the font-loading calls:**
  - `Typeface.createFromAsset`, `createFromFile` (String and File), `Resources.getFont`
  - `Typeface.Builder.build`, with constructors hooked to remember the file/asset name
  - `Typeface.CustomFallbackBuilder.build` (covers AndroidX, Compose, and downloadable fonts
    such as Google Sans from Google Play services)
- **Replacement:** the result becomes `Typeface.create(Typeface.DEFAULT, weight, italic)`.
- **Keeps icon fonts**, by three tests:
  - the name contains icon/symbol/awesome/material/glyph/emoji/mono/code/…
  - `Paint.hasGlyph("home"/"search"/"settings")` is true (ligature icon font)
  - Latin a–Z0–9 width equals the system font's (no Latin of its own)
- **Results** are cached in a WeakHashMap.

`FlutterFonts.java` (v1.1, **confirmed working on Immich**):
- **Hook:** before `io.flutter.embedding.engine.FlutterJNI.runBundleAndSnapshotFromLibrary`.
- **Reads** `flutter_assets/FontManifest.json`, skipping icon families and assets.
- **Builds** `cache/ndotforce_flutter_<hash>.zip`: the system font stored under
  `assets/flutter_assets/<asset>`, deflated (Ndot compresses ~15×).
- **Injects** it with `AssetManager.addAssetPath`.
- **Relies on:** `AssetManager2::OpenNonAsset` searching ApkAssets last-added-first, and
  `ApkAssets` accepting a zip with no `resources.arsc`.
- **Logs:** `NdotFontForce/Flutter: redirected N font file(s)`.

Suggested scope: Play Store, Phone, Messages, Contacts. The user adds Immich and the chat app.

**App icon** (v1.2): adaptive icon, `res/mipmap-anydpi-v26/ic_launcher*.xml`, with vector
layers in `res/drawable/`:
- a dot-matrix "N" on black, with a red centre dot
- a monochrome layer for themed icons

## 5. Problems hit and what fixed them

| Symptom | Cause | Fix |
|---|---|---|
| Module "installed but not effective" | KSU Next without a metamodule doesn't mount `system/` | Module bind-mounts in `post-fs-data.sh` |
| Works only in root-granted apps | KSU "Umount modules" default on (SuSFS umounts too) | User turned the setting off |
| Chinese "rectangles" / hard to read | 7×7 grid with touching dots | Rebuilt CJK on a 12px grid with gapped dots (v2 font) |
| Extra space under text | U+3031/3032 pushed head yMin to −1036 | Dropped them; head bbox set to Roboto's |
| Play Store shows boxes for CJK | **iOS_Emoji_KSU v4.1** `pm disable`s Google Play services' FontsProvider every boot (it also wipes `/data/fonts` and Google Play services' font dir) | `pm enable` both components + clear Play's cache; disable/uninstall that module (v1.6 re-enables at install) |
| Google Sans override never stuck | Google Play services re-downloads its font files; bind mounts in `/data/data` don't reach Play | Off by default; LSPosed module handles Google apps instead |
| `umount: Invalid argument` from adb su | Shell is in a different mount namespace | Use `su -M -c` for the global namespace |
| v1.7 Action shows only its first line; font back to stock | Scripts got CRLF endings from a Windows checkout; `sh` stops at the first `if … then` | `dos2unix` the scripts on the phone; `.gitattributes` now forces LF for `*.sh`/`*.prop` |
| English stays app font, Chinese is Ndot | App bundles a Latin-only font; CJK falls back to system | LSPosed module (Flutter: v1.1) |

## 6. Open items / ideas

1. **Recover `customize.sh`, `README.md`, `META-INF/`** (see §3), then exclude
   `NotoColorEmojiFlags.ttf` in `customize.sh` too.
2. **Out of reach:** games with their own engines, and in-app web pages.

Dropped: a Bold weight (bigger dots for weights ≥ 600). Regular only is fine.
Done: the Flutter redirect is confirmed on Immich (v1.1). DroidSans STOCK and the over-wide
emoji pattern are fixed in v1.7 (§3), confirmed on device: DroidSans.ttf and DroidSans-Bold.ttf
are both symlinks to Roboto-Regular.ttf on blazer and show LIVE.

## 7. Licensing

- All bundled fonts are **SIL OFL 1.1**, and the combined texts are in `ksu-module/OFL.txt`.
- The Ndot copyright says "Nothing Japanese Font Project"; no Reserved Font Name is declared.
  The derivative is still renamed "Ndot CJK".
- **Don't bundle** Apple's emoji in anything distributed. It's imported on-device from a
  module the user already has.
