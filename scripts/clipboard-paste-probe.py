# SPDX-License-Identifier: GPL-3.0-or-later
"""Opt-in local GTK clipboard/text-field probe for LiveClipboardPasteTest."""

import json
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import gi

gi.require_version("Gtk", "4.0")
from gi.repository import Gtk

text_value = ""


class Probe(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == "/text":
            body = text_value.encode("utf-8")
        elif self.path == "/focused":
            active = json.loads(subprocess.check_output(["hyprctl", "-j", "activewindow"]))
            body = str(active.get("class") == "com.avnc.PasteValidation").lower().encode()
        elif self.path == "/clipboard":
            result = subprocess.run(["wl-paste", "--no-newline"], capture_output=True, timeout=2)
            body = result.stdout
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class App(Gtk.Application):
    def do_activate(self):
        window = Gtk.ApplicationWindow(application=self, title="AVNC clipboard test")
        window.set_default_size(480, 240)
        entry = Gtk.TextView()
        entry.set_wrap_mode(Gtk.WrapMode.WORD_CHAR)
        buffer = entry.get_buffer()

        def changed(buffer):
            global text_value
            text_value = buffer.get_text(buffer.get_start_iter(), buffer.get_end_iter(), True)

        buffer.connect("changed", changed)
        window.set_child(entry)
        window.present()
        entry.grab_focus()


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 18081), Probe)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        App(application_id="com.avnc.PasteValidation").run()
    finally:
        server.shutdown()
