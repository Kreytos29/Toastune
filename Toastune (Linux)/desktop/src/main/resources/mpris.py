#!/usr/bin/env python3
# Puente MPRIS para Toastune: expone org.mpris.MediaPlayer2.toastune en el bus de sesión.
# Entrada (stdin): líneas JSON con el estado. Salida (stdout): acciones del usuario.
import json, sys, time, threading
import gi
from gi.repository import Gio, GLib

XML = """
<node>
 <interface name="org.mpris.MediaPlayer2">
  <method name="Raise"/><method name="Quit"/>
  <property name="CanQuit" type="b" access="read"/>
  <property name="CanRaise" type="b" access="read"/>
  <property name="HasTrackList" type="b" access="read"/>
  <property name="Identity" type="s" access="read"/>
  <property name="DesktopEntry" type="s" access="read"/>
  <property name="SupportedUriSchemes" type="as" access="read"/>
  <property name="SupportedMimeTypes" type="as" access="read"/>
 </interface>
 <interface name="org.mpris.MediaPlayer2.Player">
  <method name="Next"/><method name="Previous"/><method name="Pause"/>
  <method name="PlayPause"/><method name="Stop"/><method name="Play"/>
  <method name="Seek"><arg direction="in" name="Offset" type="x"/></method>
  <method name="SetPosition"><arg direction="in" name="TrackId" type="o"/><arg direction="in" name="Position" type="x"/></method>
  <method name="OpenUri"><arg direction="in" name="Uri" type="s"/></method>
  <signal name="Seeked"><arg name="Position" type="x"/></signal>
  <property name="PlaybackStatus" type="s" access="read"/>
  <property name="LoopStatus" type="s" access="readwrite"/>
  <property name="Rate" type="d" access="readwrite"/>
  <property name="Shuffle" type="b" access="readwrite"/>
  <property name="Metadata" type="a{sv}" access="read"/>
  <property name="Volume" type="d" access="readwrite"/>
  <property name="Position" type="x" access="read"/>
  <property name="MinimumRate" type="d" access="read"/>
  <property name="MaximumRate" type="d" access="read"/>
  <property name="CanGoNext" type="b" access="read"/>
  <property name="CanGoPrevious" type="b" access="read"/>
  <property name="CanPlay" type="b" access="read"/>
  <property name="CanPause" type="b" access="read"/>
  <property name="CanSeek" type="b" access="read"/>
  <property name="CanControl" type="b" access="read"/>
 </interface>
</node>
"""

SNI_XML = """
<node>
 <interface name="org.kde.StatusNotifierItem">
  <method name="Activate"><arg direction="in" name="x" type="i"/><arg direction="in" name="y" type="i"/></method>
  <method name="SecondaryActivate"><arg direction="in" name="x" type="i"/><arg direction="in" name="y" type="i"/></method>
  <method name="ContextMenu"><arg direction="in" name="x" type="i"/><arg direction="in" name="y" type="i"/></method>
  <method name="Scroll"><arg direction="in" name="delta" type="i"/><arg direction="in" name="orientation" type="s"/></method>
  <property name="Category" type="s" access="read"/>
  <property name="Id" type="s" access="read"/>
  <property name="Title" type="s" access="read"/>
  <property name="Status" type="s" access="read"/>
  <property name="WindowId" type="i" access="read"/>
  <property name="IconName" type="s" access="read"/>
  <property name="ItemIsMenu" type="b" access="read"/>
  <property name="Menu" type="o" access="read"/>
  <signal name="NewIcon"/><signal name="NewStatus"><arg type="s"/></signal>
 </interface>
</node>
"""
MENU_XML = """
<node>
 <interface name="com.canonical.dbusmenu">
  <method name="GetLayout">
   <arg type="i" name="parentId" direction="in"/><arg type="i" name="recursionDepth" direction="in"/>
   <arg type="as" name="propertyNames" direction="in"/>
   <arg type="u" name="revision" direction="out"/><arg type="(ia{sv}av)" name="layout" direction="out"/>
  </method>
  <method name="GetGroupProperties">
   <arg type="ai" name="ids" direction="in"/><arg type="as" name="propertyNames" direction="in"/>
   <arg type="a(ia{sv})" name="properties" direction="out"/>
  </method>
  <method name="GetProperty"><arg type="i" name="id" direction="in"/><arg type="s" name="name" direction="in"/><arg type="v" name="value" direction="out"/></method>
  <method name="Event"><arg type="i" name="id" direction="in"/><arg type="s" name="eventId" direction="in"/><arg type="v" name="data" direction="in"/><arg type="u" name="timestamp" direction="in"/></method>
  <method name="AboutToShow"><arg type="i" name="id" direction="in"/><arg type="b" name="needUpdate" direction="out"/></method>
  <property name="Version" type="u" access="read"/>
  <property name="TextDirection" type="s" access="read"/>
  <property name="Status" type="s" access="read"/>
  <property name="IconThemePath" type="as" access="read"/>
  <signal name="LayoutUpdated"><arg type="u"/><arg type="i"/></signal>
 </interface>
</node>
"""
MENU_ITEMS = [(1, "Abrir Toastune", "Raise"), (2, "Reproducir / pausar", "PlayPause"), (3, "Siguiente", "Next"),
              (4, "Anterior", "Previous"), (5, "Salir", "Quit")]
SNI_OBJ = "/StatusNotifierItem"
MENU_OBJ = "/MenuBar"


def item_props(label):
    return {"label": GLib.Variant("s", label), "enabled": GLib.Variant("b", True), "visible": GLib.Variant("b", True)}


def sni_method(c, sender, path, iface, name, params, inv):
    if name in ("Activate", "SecondaryActivate"):
        emit("Raise")
    inv.return_value(None)


def sni_get(c, sender, path, iface, prop):
    return {"Category": GLib.Variant("s", "ApplicationStatus"), "Id": GLib.Variant("s", "toastune"),
            "Title": GLib.Variant("s", "Toastune"), "Status": GLib.Variant("s", "Active"),
            "WindowId": GLib.Variant("i", 0), "IconName": GLib.Variant("s", "toastune"),
            "ItemIsMenu": GLib.Variant("b", False), "Menu": GLib.Variant("o", MENU_OBJ)}.get(prop)


def menu_method(c, sender, path, iface, name, params, inv):
    a = params.unpack()
    if name == "GetLayout":
        kids = [GLib.Variant("(ia{sv}av)", (i, item_props(l), [])) for i, l, _ in MENU_ITEMS]
        root = (0, {"children-display": GLib.Variant("s", "submenu")}, kids)
        inv.return_value(GLib.Variant("(u(ia{sv}av))", (1, root)))
    elif name == "GetGroupProperties":
        inv.return_value(GLib.Variant("(a(ia{sv}))", ([(i, item_props(l)) for i, l, _ in MENU_ITEMS if not a[0] or i in a[0]],)))
    elif name == "GetProperty":
        inv.return_value(GLib.Variant("(v)", (GLib.Variant("s", ""),)))
    elif name == "Event":
        if a[1] == "clicked":
            for i, _, cmd in MENU_ITEMS:
                if i == a[0]:
                    emit(cmd)
        inv.return_value(None)
    elif name == "AboutToShow":
        inv.return_value(GLib.Variant("(b)", (False,)))
    else:
        inv.return_value(None)


def menu_get(c, sender, path, iface, prop):
    return {"Version": GLib.Variant("u", 3), "TextDirection": GLib.Variant("s", "ltr"),
            "Status": GLib.Variant("s", "normal"), "IconThemePath": GLib.Variant("as", [])}.get(prop)


def setup_tray(c):
    """Icono en la bandeja vía StatusNotifierItem (el que usan Steam/Discord, waybar, KDE, etc.)."""
    import os
    try:
        for i in Gio.DBusNodeInfo.new_for_xml(SNI_XML).interfaces:
            c.register_object(SNI_OBJ, i, sni_method, sni_get, None)
        for i in Gio.DBusNodeInfo.new_for_xml(MENU_XML).interfaces:
            c.register_object(MENU_OBJ, i, menu_method, menu_get, None)
    except Exception:
        return
    svc = "org.kde.StatusNotifierItem-%d-1" % os.getpid()
    registered = {"v": False}

    def register(*_):
        if registered["v"]:
            return
        try:
            c.call_sync("org.kde.StatusNotifierWatcher", "/StatusNotifierWatcher", "org.kde.StatusNotifierWatcher",
                        "RegisterStatusNotifierItem", GLib.Variant("(s)", (svc,)), None, Gio.DBusCallFlags.NONE, 2000, None)
            registered["v"] = True
            emit("Tray")
        except Exception:
            pass

    def watcher_gone(*_):
        registered["v"] = False
        emit("NoTray")

    Gio.bus_own_name_on_connection(c, svc, Gio.BusNameOwnerFlags.NONE, lambda *a: None, lambda *a: None)
    Gio.bus_watch_name_on_connection(c, "org.kde.StatusNotifierWatcher", Gio.BusNameWatcherFlags.NONE, register, watcher_gone)

ROOT = "org.mpris.MediaPlayer2"
PLAYER = "org.mpris.MediaPlayer2.Player"
OBJ = "/org/mpris/MediaPlayer2"

st = {"title": "", "artist": "", "album": "", "art": "", "len": 0, "status": "Stopped",
      "pos": 0, "volume": 1.0, "next": False, "prev": False, "id": "none", "loop": "None", "shuffle": False}
stamp = time.monotonic()
conn = None


def emit(line):
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def position():
    p = st["pos"]
    if st["status"] == "Playing":
        p += int((time.monotonic() - stamp) * 1_000_000)
    if st["len"] > 0:
        p = min(p, st["len"])
    return max(0, p)


def metadata():
    tid = "/app/toastune/track/" + "".join(c if c.isalnum() else "_" for c in st["id"])
    m = {"mpris:trackid": GLib.Variant("o", tid if st["id"] != "none" else "/org/mpris/MediaPlayer2/TrackList/NoTrack")}
    if st["id"] != "none":
        m["xesam:title"] = GLib.Variant("s", st["title"])
        m["xesam:artist"] = GLib.Variant("as", [st["artist"]] if st["artist"] else [])
        m["xesam:album"] = GLib.Variant("s", st["album"])
        m["mpris:length"] = GLib.Variant("x", int(st["len"]))
        if st["art"]:
            m["mpris:artUrl"] = GLib.Variant("s", st["art"])
    return m


def props(iface):
    if iface == ROOT:
        return {"CanQuit": GLib.Variant("b", True), "CanRaise": GLib.Variant("b", True),
                "HasTrackList": GLib.Variant("b", False), "Identity": GLib.Variant("s", "Toastune"),
                "DesktopEntry": GLib.Variant("s", "toastune"),
                "SupportedUriSchemes": GLib.Variant("as", []), "SupportedMimeTypes": GLib.Variant("as", [])}
    has = st["id"] != "none"
    return {"PlaybackStatus": GLib.Variant("s", st["status"]), "LoopStatus": GLib.Variant("s", st["loop"]),
            "Rate": GLib.Variant("d", 1.0), "Shuffle": GLib.Variant("b", st["shuffle"]),
            "Metadata": GLib.Variant("a{sv}", metadata()), "Volume": GLib.Variant("d", st["volume"]),
            "Position": GLib.Variant("x", position()), "MinimumRate": GLib.Variant("d", 1.0),
            "MaximumRate": GLib.Variant("d", 1.0), "CanGoNext": GLib.Variant("b", st["next"]),
            "CanGoPrevious": GLib.Variant("b", st["prev"]), "CanPlay": GLib.Variant("b", has),
            "CanPause": GLib.Variant("b", has), "CanSeek": GLib.Variant("b", has), "CanControl": GLib.Variant("b", True)}


def on_method(c, sender, path, iface, name, params, inv):
    if iface == "org.freedesktop.DBus.Properties":
        return
    a = params.unpack()
    if name == "Seek":
        emit("Seek:%d" % a[0])
    elif name == "SetPosition":
        emit("SetPos:%d" % a[1])
    elif name == "OpenUri":
        pass
    else:
        emit(name)
    inv.return_value(None)


def on_get(c, sender, path, iface, prop):
    return props(iface).get(prop)


def on_set(c, sender, path, iface, prop, value):
    if prop == "Volume":
        emit("Vol:%f" % value.unpack())
    elif prop == "Shuffle":
        emit("Shuffle")
    elif prop == "LoopStatus":
        emit("Loop")
    return True


def changed(iface, d):
    if conn is None:
        return
    conn.emit_signal(None, OBJ, "org.freedesktop.DBus.Properties", "PropertiesChanged",
                     GLib.Variant("(sa{sv}as)", (iface, d, [])))


def apply(msg):
    global stamp
    old_pos = position()
    prev = dict(st)
    for k, v in msg.items():
        if k in st:
            st[k] = v
    stamp = time.monotonic()
    p = {}
    full = props(PLAYER)
    for key, fields in (("PlaybackStatus", ("status",)), ("Metadata", ("title", "artist", "album", "art", "len", "id")),
                        ("Volume", ("volume",)), ("CanGoNext", ("next",)), ("CanGoPrevious", ("prev",)),
                        ("CanPlay", ("id",)), ("CanPause", ("id",)), ("CanSeek", ("id",)),
                        ("Shuffle", ("shuffle",)), ("LoopStatus", ("loop",))):
        if any(prev[f] != st[f] for f in fields):
            p[key] = full[key]
    if p:
        changed(PLAYER, p)
    # salto de posición (el usuario movió la barra) → Seeked
    if prev["id"] == st["id"] and abs(st["pos"] - old_pos) > 1_200_000 and "pos" in msg:
        conn.emit_signal(None, OBJ, PLAYER, "Seeked", GLib.Variant("(x)", (st["pos"],)))


def reader(loop):
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception:
            continue
        GLib.idle_add(lambda m=msg: (apply(m), False)[1])
    GLib.idle_add(loop.quit)


def main():
    global conn
    loop = GLib.MainLoop()
    info = Gio.DBusNodeInfo.new_for_xml(XML)

    def acquired(c, name):
        global conn
        conn = c
        for i in info.interfaces:
            c.register_object(OBJ, i, on_method, on_get, on_set)
        emit("Ready")
        setup_tray(c)

    def lost(c, name):
        emit("NameLost")
        loop.quit()

    Gio.bus_own_name(Gio.BusType.SESSION, "org.mpris.MediaPlayer2.toastune",
                     Gio.BusNameOwnerFlags.REPLACE | Gio.BusNameOwnerFlags.ALLOW_REPLACEMENT,
                     acquired, None, lost)
    threading.Thread(target=reader, args=(loop,), daemon=True).start()
    loop.run()


main()
