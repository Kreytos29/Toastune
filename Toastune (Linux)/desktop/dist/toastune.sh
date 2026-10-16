#!/bin/sh
# Uso: ./toastune.sh   (necesita java 17+ y mpv: sudo pacman -S jre17-openjdk mpv)
exec java -jar "$(dirname "$0")/Toastune.jar" "$@"
