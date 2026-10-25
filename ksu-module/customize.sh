#!/system/bin/sh
# Install-time setup (KernelSU Next / SukiSU Ultra / Magisk source this file).
# Finds the ROM's UI font files, points each one at fonts/NdotCJK-Regular.ttf and
# writes replaced.txt (<device path>|<source rel path>) for post-fs-data.sh to mount.
# Recreated for v1.7 from HANDOFF.md (the v1.6 original was not kept on the device).
FONT="$MODPATH/fonts/NdotCJK-Regular.ttf"
LIST="$MODPATH/replaced.txt"
OLD=/data/adb/modules/ndot_cjk_font        # installed copy when upgrading
: > "$LIST"

ui_print "- Ndot CJK System Font"
[ -f "$FONT" ] || abort "! fonts/NdotCJK-Regular.ttf missing from the zip"

# Same file in system/ too, for root managers that mount module files themselves.
link() {  # link <source> <device path>
  case "$2" in
    /system/*) d="$MODPATH$2" ;;
    *)         d="$MODPATH/system$2" ;;     # /product, /system_ext live under system/
  esac
  mkdir -p "${d%/*}"
  ln -f "$1" "$d" 2>/dev/null || cp -f "$1" "$d"
}

# ---- UI fonts ----
# Patterns keep the hyphen where needed: NotoSerif-* must not catch NotoSerifArmenian-VF etc.
n=0
for dir in /system/fonts /product/fonts /system_ext/fonts; do
  [ -d "$dir" ] || continue
  for f in "$dir"/*; do
    [ -f "$f" ] || continue
    b=${f##*/}
    case "$b" in *.ttf|*.otf) ;; *) continue ;; esac
    case "$b" in *Mono*|*Emoji*|*CJK*|*Symbol*|*Icon*) continue ;; esac
    case "$b" in
      Roboto-*|RobotoStatic-*|RobotoFlex-*|NotoSerif-*|DroidSans*) ;;   # AOSP
      GoogleSans-*|GoogleSansText-*|GoogleSansFlex*|GoogleSansClock*) ;; # Pixel
      MiSans*|Mitype*|MiLanPro*) ;;                                     # MIUI / HyperOS
      SysSans*|OPlusSans*|OplusSans*) ;;                                # ColorOS
      SamsungOne*|SECRoboto*) ;;                                        # One UI
      VivoFont*|vivo*Sans*) ;;                                          # vivo
      HONORSans*|HarmonyOS_Sans*) ;;                                    # Honor
      *) continue ;;
    esac
    echo "$f|fonts/NdotCJK-Regular.ttf" >> "$LIST"
    # Symlinks (DroidSans.ttf -> Roboto-Regular.ttf) are covered by their target.
    [ -L "$f" ] || link "$FONT" "$f"
    n=$((n + 1))
  done
done
ui_print "- $n UI font files will use Ndot CJK"
[ "$n" -gt 0 ] || ui_print "! No known UI font found; nothing will change"

# ---- Optional colour emoji ----
# Must be CBDT or COLR: Apple's sbix format does not render on Android.
EMOJI=""
for e in "$MODPATH/emoji/Emoji.ttf" /sdcard/NdotCJK/Emoji.ttf /sdcard/Download/Emoji.ttf \
         "$OLD/fonts/Emoji.ttf" /data/adb/modules/*/system/fonts/NotoColorEmoji.ttf; do
  [ -f "$e" ] || continue
  case "$e" in "$OLD"/system/*|"$MODPATH"/system/*) continue ;; esac
  if head -c 2048 "$e" | grep -aq -e CBDT -e COLR; then EMOJI="$e"; break; fi
  ui_print "! $e is not CBDT/COLR, skipped"
done
rm -rf "$MODPATH/emoji"
if [ -n "$EMOJI" ]; then
  cp -f "$EMOJI" "$MODPATH/fonts/Emoji.ttf"
  ui_print "- Emoji: $EMOJI"
  for t in /system/fonts/NotoColorEmoji.ttf /system/fonts/NotoColorEmojiLegacy.ttf \
           /product/fonts/NotoColorEmoji.ttf /system/fonts/SamsungColorEmoji.ttf; do
    [ -f "$t" ] || continue
    echo "$t|fonts/Emoji.ttf" >> "$LIST"
    [ -L "$t" ] || link "$MODPATH/fonts/Emoji.ttf" "$t"
  done
else
  ui_print "- Emoji: stock (no Emoji.ttf found)"
fi

# Keep the optional Google Sans override flag across upgrades.
[ -f "$OLD/gms_fonts" ] && touch "$MODPATH/gms_fonts"

# Some emoji modules disable Play services' font provider; Play Store then shows boxes for CJK.
pm enable com.google.android.gms/com.google.android.gms.fonts.provider.FontsProvider >/dev/null 2>&1
pm enable com.google.android.gms/com.google.android.gms.fonts.update.UpdateSchedulerService >/dev/null 2>&1

set_perm_recursive "$MODPATH" 0 0 0755 0644
chcon -R u:object_r:system_file:s0 "$MODPATH/fonts" "$MODPATH/system" 2>/dev/null
ui_print "- Done. Turn OFF \"Umount modules\" in KernelSU settings, then reboot."
