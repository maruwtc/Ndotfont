#!/system/bin/sh
# Bind-mount the replacement fonts ourselves, so the module works even when the
# root manager does not mount module files (no metamodule, overlay failures, ...).
MODDIR=${0%/*}
LOG="$MODDIR/mount.log"
echo "boot $(date)" > "$LOG"
chcon -R u:object_r:system_file:s0 "$MODDIR/fonts" 2>/dev/null

DONE=" "
while IFS='|' read -r target srcrel; do
  [ -n "$target" ] || continue
  [ -n "$srcrel" ] || srcrel="fonts/NdotCJK-Regular.ttf"      # v1.0-1.3 list format
  # v1.6 and earlier also listed NotoColorEmojiFlags.ttf; leave the stock flags alone.
  case "$target" in *EmojiFlags*) echo "skip (flags) $target" >> "$LOG"; continue ;; esac
  src="$MODDIR/$srcrel"
  want=$(stat -c %s "$src" 2>/dev/null)
  if [ -z "$want" ]; then echo "skip (no source) $target" >> "$LOG"; continue; fi
  if [ ! -f "$target" ]; then echo "skip (absent) $target" >> "$LOG"; continue; fi
  # Some names are symlinks (DroidSans.ttf -> Roboto-Regular.ttf). Work on the real
  # file, and only once, so the same file is not bind-mounted twice.
  real=$(readlink -f "$target")
  [ "$real" = "$target" ] || echo "  $target -> $real" >> "$LOG"
  case "$DONE" in *" $real "*) echo "ok (same file) $target" >> "$LOG"; continue ;; esac
  DONE="$DONE$real "
  if [ "$(stat -L -c %s "$real" 2>/dev/null)" = "$want" ]; then
    echo "ok (already mounted) $target" >> "$LOG"; continue
  fi
  if mount -o bind "$src" "$real" 2>>"$LOG"; then echo "bind $target" >> "$LOG"
  else echo "FAIL $target" >> "$LOG"; fi
done < "$MODDIR/replaced.txt"

# Android 12+ can load a newer emoji font from /data/fonts (Play system updates).
# Cover it with the custom emoji so it does not win over /system.
EMOJI="$MODDIR/fonts/Emoji.ttf"
if [ -f "$EMOJI" ]; then
  for f in /data/fonts/files/*/*[Ee]moji*.ttf; do
    [ -f "$f" ] || continue
    mount -o bind "$EMOJI" "$f" 2>>"$LOG" && echo "bind $f" >> "$LOG"
  done
fi

# ---- Optional: early-mount colour emoji from another installed emoji module ----
# (e.g. an iOS emoji module shipping system/fonts/NotoColorEmoji.ttf). Mounting it
# here, before zygote, makes it work even without a metamodule.
# Skipped when a custom Emoji.ttf was supplied to this module (that one wins).
for e in /data/adb/modules/*/system/fonts/NotoColorEmoji.ttf; do
  [ -f "$EMOJI" ] && break
  [ -f "$e" ] || continue
  emod="${e%/system/fonts/NotoColorEmoji.ttf}"
  [ "$emod" = "$MODDIR" ] && continue
  [ -f "$emod/disable" ] || [ -f "$emod/remove" ] && continue
  chcon u:object_r:system_file:s0 "$e" 2>/dev/null
  esize=$(stat -c %s "$e")
  for t in /system/fonts/NotoColorEmoji.ttf /system/fonts/NotoColorEmojiLegacy.ttf \
           /product/fonts/NotoColorEmoji.ttf /system/fonts/SamsungColorEmoji.ttf; do
    [ -f "$t" ] || continue
    if [ "$(stat -c %s "$t" 2>/dev/null)" = "$esize" ]; then
      echo "emoji ok (already mounted) $t" >> "$LOG"
    elif mount -o bind "$e" "$t" 2>>"$LOG"; then
      echo "emoji bind $t <- $emod" >> "$LOG"
    else
      echo "emoji FAIL $t" >> "$LOG"
    fi
  done
  break
done
