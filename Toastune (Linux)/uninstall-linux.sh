#!/bin/sh
# Desinstala Toastune. Con --todo también borra tu biblioteca y las descargas.
set -e
rm -f "$HOME/.local/bin/toastune" \
      "$HOME/.local/share/applications/toastune.desktop" \
      "$HOME/.local/share/icons/hicolor/512x512/apps/toastune.png" \
      "$HOME/.local/share/toastune/Toastune.jar"
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true

if [ "$1" = "--todo" ]; then
    rm -rf "$HOME/.local/share/toastune"
    echo "Toastune desinstalado y datos borrados (favoritos, listas y descargas)."
else
    echo "Toastune desinstalado. Se conservaron tus favoritos, listas y descargas en ~/.local/share/toastune"
    echo "Para borrarlos también: ./uninstall-linux.sh --todo"
fi
