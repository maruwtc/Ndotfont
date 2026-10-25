# Ndot CJK System Font

Nothing's **Ndot** dot-matrix font as the whole-system font, with Chinese (HK/TW/CN forms),
Japanese and Korean redrawn in the same dot style. Noto CJK stays as the fallback.

Works on KernelSU Next, SukiSU Ultra and Magisk. No metamodule needed: the module
bind-mounts the fonts itself at boot.

## Install
1. Flash the zip in your root manager.
2. KernelSU: turn **off** "Umount modules" in settings, or apps keep the stock font.
3. Reboot. The module's **Action** button shows each replaced file as LIVE or STOCK.

## Optional colour emoji
Put a CBDT or COLR emoji font at `/sdcard/NdotCJK/Emoji.ttf` (or `/sdcard/Download/Emoji.ttf`)
before installing. An emoji font already in this module, or in another installed emoji module,
is picked up too. Flags stay stock.

## Apps with their own fonts
Use the **Ndot Font Force** LSPosed/Vector module (`NdotFontForce-*.apk`) for those.

Fonts: SIL Open Font License 1.1, see `OFL.txt`.
