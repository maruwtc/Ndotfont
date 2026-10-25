# Ndot Font Force (LSPosed / Vector module)

Makes selected apps draw text with the **system font** (Ndot CJK when the Ndot CJK
KernelSU module is installed) instead of the font they bundle or download.

Covers: fonts from app assets/files, `res/font`, AndroidX/Jetpack Compose fonts, and
downloadable fonts such as the Google Sans that Google apps get from Play services.
Keeps icon fonts (Material Symbols, Font Awesome, ...) and monospace/code fonts.
Flutter apps (e.g. Immich): their bundled fonts are redirected to the system font by adding
a small zip (in the app's own cache) to the app's AssetManager just before the Flutter
engine starts. The app itself is not modified, so updates and signatures are unaffected.
Does NOT cover: games with their own engines, and web pages inside apps.

## Build (Android Studio)
1. File > Open > select this `NdotFontForce` folder. Let Gradle sync
   (accept any SDK Platform 34 / build-tools install prompt).
2. Build > Build App Bundle(s) / APK(s) > Build APK(s).
3. The APK is at `app/build/outputs/apk/debug/NdotFontForce-v<version>-debug.apk`.
4. Optional: `python3 release.py` puts it next to the KernelSU module zip in `release/`.

## Install
1. Copy the APK to the phone and install it.
2. Open Vector (LSPosed) > Modules > enable **Ndot Font Force**.
3. In its scope, tick the apps to change (Play Store, Phone, Messages and Contacts are
   suggested; add others you want). Do NOT tick "System Framework".
4. Force-stop those apps (or reboot) and reopen them.

Logs: Vector > Logs, filter "NdotFontForce". A Flutter app shows
"redirected N font file(s)" when the redirect worked.

If an app shows boxes instead of icons, untick it from the scope and tell me its name.
