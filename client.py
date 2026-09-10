from __future__ import annotations

import json
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4
from typing import Any

from shadow.clients.gateway.protocol import ClientRequest


class MobileClientError(RuntimeError):
    """Raised when the mobile transport cannot complete a request."""


class MobileClient:
    """Small dependency-free mobile transport for the authenticated Shadow gateway.

    The mobile client is transport-only: it sends goals to Shadow and never receives
    or executes tool credentials. Voice/audio capture belongs to the platform UI.
    """

    def __init__(self, base_url: str = "http://127.0.0.1:8787", token: str | None = None,
                 *, timeout: float = 30.0):
        self.base_url = base_url.rstrip("/")
        self.token = token
        if timeout <= 0:
            raise ValueError("timeout must be > 0")
        self.timeout = timeout

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json", "Accept": "application/json"}
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        return headers

    def health(self) -> dict[str, Any]:
        return self._get("/health")

    def request(self, action: str, payload: dict[str, Any] | None = None,
                context: dict[str, Any] | None = None, *, request_id: str | None = None) -> dict[str, Any]:
        req = ClientRequest(
            request_id or uuid4().hex,
            action,
            payload or {},
            context or {},
        )
        try:
            body = req.to_json().encode("utf-8")
            with urlopen(Request(self.base_url + "/v1/request", data=body,
                                 headers=self._headers(), method="POST"), timeout=self.timeout) as response:
                return self._decode(response.read())
        except HTTPError as exc:
            detail = self._read_error(exc)
            raise MobileClientError(f"gateway returned HTTP {exc.code}: {detail}") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise MobileClientError(f"gateway unavailable: {exc}") from exc

    def run_goal(self, goal: str, *, context: dict[str, Any] | None = None,
                 request_id: str | None = None) -> dict[str, Any]:
        if not isinstance(goal, str) or not goal.strip():
            raise ValueError("goal is required")
        return self.request("run_goal", {"goal": goal}, context, request_id=request_id)

    def _get(self, path: str) -> dict[str, Any]:
        try:
            with urlopen(Request(self.base_url + path, headers=self._headers(), method="GET"),
                         timeout=self.timeout) as response:
                return self._decode(response.read())
        except HTTPError as exc:
            detail = self._read_error(exc)
            raise MobileClientError(f"gateway returned HTTP {exc.code}: {detail}") from exc
        except (URLError, TimeoutError, OSError) as exc:
            raise MobileClientError(f"gateway unavailable: {exc}") from exc

    @staticmethod
    def _decode(raw: bytes) -> dict[str, Any]:
        try:
            value = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise MobileClientError("gateway returned invalid JSON") from exc
        if not isinstance(value, dict):
            raise MobileClientError("gateway returned a non-object response")
        return value

    @classmethod
    def _read_error(cls, exc: HTTPError) -> str:
        try:
            value = cls._decode(exc.read())
            error = value.get("error")
            return error if isinstance(error, str) and error else "request failed"
        except Exception:
            return "request failed"
