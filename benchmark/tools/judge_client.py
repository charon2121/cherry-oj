"""向 judge 发 HTTP 请求（判题调用协议见 docs/protocols/judge-api.md）。

用标准库的 http.client：它不读环境里的代理设置，所以本机有 HTTP 代理时请求也不会被拦下。
"""

from __future__ import annotations

import http.client
import json
from typing import Any
from urllib.parse import urlsplit


class ClientError(Exception):
    """连不上 judge，或响应不是 HTTP。"""


class JudgeClient:
    def __init__(self, url: str) -> None:
        parts = urlsplit(url)
        if parts.scheme != "http" or not parts.hostname:
            raise ClientError(f"judge 地址 {url!r} 必须是 http://主机:端口")
        self.url = url.rstrip("/")
        self.host, self.port = parts.hostname, parts.port or 80

    def _request(self, method: str, path: str, body: bytes | None, timeout: float) -> tuple[int, Any]:
        connection = http.client.HTTPConnection(self.host, self.port, timeout=timeout)
        try:
            connection.request(method, path, body, {"Content-Type": "application/json"} if body else {})
            response = connection.getresponse()
            data = response.read(64 << 20)
        except (OSError, http.client.HTTPException) as error:
            raise ClientError(f"{method} {self.url}{path}：{error}") from error
        finally:
            connection.close()
        try:
            return response.status, json.loads(data)
        except ValueError:
            return response.status, data.decode(errors="replace")

    def version(self) -> dict[str, Any]:
        status, payload = self._request("GET", "/version", None, 10)
        if status != 200 or not isinstance(payload, dict):
            raise ClientError(f"GET {self.url}/version 返回 HTTP {status}，不像 judge")
        return payload

    def judge(self, request: dict[str, Any], timeout: float) -> tuple[int, Any]:
        return self._request("POST", "/judge", json.dumps(request).encode(), timeout)
