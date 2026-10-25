#!/system/bin/sh
# Runs after boot.
# 1) Custom emoji (if supplied): also cover Google Play services' EmojiCompat copy,
#    used by apps that bundle EmojiCompat instead of the system emoji.
# 2) OPTIONAL, off by default: cover the Google Sans that Google apps download through
#    Play services. Enable by creating the file  /data/adb/modules/ndot_cjk_font/gms_fonts
#    (or /sdcard/NdotCJK/gms_fonts) and rebooting. Off by default because Play services
#    re-downloads these files and some Google apps lose their CJK fallback.
MODDIR=${0%/*}
FONT="$MODDIR/fonts/NdotCJK-Regular.ttf"
EMOJI="$MODDIR/fonts/Emoji.ttf"
LOG="$MODDIR/mount.log"
GMS_ON=0
if [ -f "$MODDIR/gms_fonts" ] || [ -f /sdcard/NdotCJK/gms_fonts ]; then GMS_ON=1; fi
[ -f "$EMOJI" ] || [ "$GMS_ON" = 1 ] || exit 0

until [ "$(getprop sys.boot_completed)" = "1" ]; do sleep 5; done
sleep 15

FSIZE=$(stat -c %s "$FONT")
ESIZE=""
[ -f "$EMOJI" ] && ESIZE=$(stat -c %s "$EMOJI")
D=/data/data/com.google.android.gms/files/fonts/opentype
[ -d "$D" ] || exit 0

for f in "$D"/*; do
  [ -f "$f" ] || continue
  n=$(basename "$f" | tr 'A-Z' 'a-z')
  case "$n" in
    *emoji*)
      [ -n "$ESIZE" ] || continue
      [ "$(stat -c %s "$f")" = "$ESIZE" ] && continue
      mount -o bind "$EMOJI" "$f" 2>>"$LOG" && echo "gms emoji bind $f" >> "$LOG" ;;
    *symbol*|*icon*|*material*|*mono*|*code*) ;;
    *google*sans*|*product*sans*)
      [ "$GMS_ON" = 1 ] || continue
      [ "$(stat -c %s "$f")" = "$FSIZE" ] && continue
      mount -o bind "$FONT" "$f" 2>>"$LOG" && echo "gms bind $f" >> "$LOG" ;;
  esac
done
