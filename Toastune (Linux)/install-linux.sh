#!/bin/sh
# Compila Toastune e instálalo como un programa normal (menú de aplicaciones + comando `toastune`).
# Necesita: sudo pacman -S jdk17-openjdk mpv python-gobject   (python-gobject: widget de multimedia + icono de bandeja)
set -e
cd "$(dirname "$0")"
JDK=/usr/lib/jvm/java-17-openjdk
[ -d "$JDK" ] || { echo "Falta jdk17-openjdk: sudo pacman -S jdk17-openjdk"; exit 1; }

rm -f desktop/build/compose/jars/*.jar
JAVA_HOME=$JDK ./gradlew :desktop:packageUberJarForCurrentOS

DEST="$HOME/.local/share/toastune"
mkdir -p "$DEST" "$HOME/.local/bin" "$HOME/.local/share/applications" "$HOME/.local/share/icons/hicolor/512x512/apps"
cp "$(ls -t desktop/build/compose/jars/*.jar | head -1)" "$DEST/Toastune.jar"
cp desktop/src/main/resources/icon.png "$HOME/.local/share/icons/hicolor/512x512/apps/toastune.png"

cat > "$HOME/.local/bin/toastune" <<EOF
#!/bin/sh
exec $JDK/bin/java -jar "$DEST/Toastune.jar" "\$@"
EOF
chmod +x "$HOME/.local/bin/toastune"

cat > "$HOME/.local/share/applications/toastune.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Toastune
Comment=Reproductor de música libre
Exec=$HOME/.local/bin/toastune
Icon=toastune
Categories=AudioVideo;Audio;Player;
Terminal=false
EOF
python3 -c "import gi" 2>/dev/null || echo "Aviso: instala python-gobject (sudo pacman -S python-gobject) para el widget de multimedia y el icono de bandeja."
update-desktop-database "$HOME/.local/share/applications" 2>/dev/null || true
echo "Listo. Abre Toastune desde el menú o con el comando: toastune"
