from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import pytest

from shadow.clients.mobile import MobileClient, MobileClientError


class _Handler(BaseHTTPRequestHandler):
    token = "mobile-test-token"

    def _reply(self, status: int, body: dict):
        raw = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path == "/health":
            self._reply(200, {"ok": True, "data": {"status": "ok"}})
            return
        self._reply(404, {"ok": False, "error": "not found"})

    def do_POST(self):
        if self.headers.get("Authorization") != f"Bearer {self.token}":
            self._reply(401, {"ok": False, "error": "unauthorized"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length))
        self._reply(200, {"id": body["id"], "ok": True, "data": {"echo": body}})

    def log_message(self, *_):
        return


@pytest.fixture
def server():
    httpd = ThreadingHTTPServer(("127.0.0.1", 0), _Handler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{httpd.server_port}"
    finally:
        httpd.shutdown()
        httpd.server_close()
        thread.join(timeout=2)


def test_mobile_health(server):
    assert MobileClient(server).health()["data"]["status"] == "ok"


def test_mobile_run_goal_uses_gateway(server):
    client = MobileClient(server, token="mobile-test-token")
    result = client.run_goal("remember this")
    assert result["ok"] is True
    assert result["data"]["echo"]["action"] == "run_goal"
    assert result["data"]["echo"]["payload"] == {"goal": "remember this"}


def test_mobile_rejects_invalid_goal(server):
    with pytest.raises(ValueError):
        MobileClient(server).run_goal(" ")


def test_mobile_fails_closed_without_token(server):
    with pytest.raises(MobileClientError, match="HTTP 401"):
        MobileClient(server).run_goal("safe request")
