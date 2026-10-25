#!/system/bin/sh
# KernelSU Next / SukiSU "Action" button: show what this module replaces and whether it is live.
MODDIR=${0%/*}
echo "Ndot CJK System Font"
if [ -f "$MODDIR/fonts/Emoji.ttf" ]; then
  echo "Emoji: custom Emoji.ttf in this module"
else
  emo=$(ls /data/adb/modules/*/system/fonts/NotoColorEmoji.ttf 2>/dev/null | grep -v "^$MODDIR/" | head -1)
  if [ -z "$emo" ]; then echo "Emoji: stock"
  elif [ "$(stat -c %s /system/fonts/NotoColorEmoji.ttf 2>/dev/null)" = "$(stat -c %s "$emo")" ]; then
    echo "Emoji: LIVE from ${emo%/system/fonts/*}"
  else echo "Emoji: module ${emo%/system/fonts/*} found, not active yet (reboot)"; fi
fi
echo "Status of each replaced file (LIVE = replacement active):"
while IFS='|' read -r t s; do
  [ -n "$t" ] || continue
  [ -n "$s" ] || s="fonts/NdotCJK-Regular.ttf"
  want=$(stat -c %s "$MODDIR/$s")
  if [ "$(stat -c %s "$t" 2>/dev/null)" = "$want" ]; then echo "  LIVE   $t"; else echo "  STOCK  $t"; fi
done < "$MODDIR/replaced.txt"
n=$(grep -c "gms bind" "$MODDIR/mount.log" 2>/dev/null)
if [ -f "$MODDIR/gms_fonts" ] || [ -f /sdcard/NdotCJK/gms_fonts ]; then
  echo "Google apps Google Sans override: ON (${n:-0} files covered at boot)"
else
  echo "Google apps Google Sans override: OFF (default)"
fi
echo
echo "Boot log:"
cat "$MODDIR/mount.log" 2>/dev/null || echo "  (no log yet - reboot first)"
