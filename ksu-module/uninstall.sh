#!/system/bin/sh
# Font overlays disappear when the module is removed; just drop cached font configs.
rm -rf /data/system/theme/fonts 2>/dev/null
