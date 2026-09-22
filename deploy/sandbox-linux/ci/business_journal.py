"""从私有服务日志导出有界、按白名单筛选的诊断事实。

The services log structured JSON (logstash), so a failing request leaves a machine-shaped
record behind: which service, which route, which status, how long it took. Those fields are
written by our own code and carry no submitted content, so they can be exported. Everything
else in the log can hold credentials, tokens or user data. Only the exact legacy gateway
error template is read from message; arbitrary message text is never exported.
A field that does not match its expected shape is dropped
rather than truncated: a truncated free-text field is still free text.
"""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any, Iterable

MAX_LOG_BYTES = 4 << 20
MAX_LINE_BYTES = 64 << 10
MAX_TRACE_BYTES = 64 << 10
MAX_ENTRIES = 40
MAX_EXCEPTIONS = 8
MAX_FRAMES = 12

CLASS = re.compile(r'[A-Za-z][A-Za-z0-9_.$]{0,200}')
ROUTE = re.compile(r'[A-Za-z0-9_/{}.*-]{1,200}')
IDENT = re.compile(r'[A-Za-z0-9_-]{1,128}')
HEX = re.compile(r'[0-9a-f]{1,64}')
# `Caused by:` chains are the part worth keeping; the text after the class name is not.
THROWN = re.compile(r'(?:\A|\n)(?:Caused by: |Suppressed: )?'
                    r'([A-Za-z][A-Za-z0-9_.$]{0,200}(?:Exception|Error|Throwable))(?::|\s*\n|\Z)')
FRAME = re.compile(r'\n\s*at (com\.cherryoj\.[A-Za-z0-9_.$]{1,200}\.[A-Za-z0-9_$<>]{1,80})\(')
JAVA_CLASS = re.compile(r'[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+')
APP_FRAME = re.compile(r'com\.cherryoj\.[A-Za-z0-9_.$]{1,200}\.[A-Za-z0-9_$<>]{1,80}')
GATEWAY_HANDLER = 'com.cherryoj.gatewayservice.api.ApiProblemHandler'
ERROR_HANDLERS = (GATEWAY_HANDLER, 'com.cherryoj.gatewayservice.api.UnhandledApiErrorObserver',
                  'com.cherryoj.problemservice.api.ProblemExceptionHandler')
ERROR_FIELDS = ('event', 'request_id', 'error_type', 'exception_types', 'application_frames')
LEGACY_ERROR = re.compile(r'Unhandled browser API error requestId=(req_[0-9a-f]{32}) errorType=(' +
                          JAVA_CLASS.pattern + r')')


def _text(value: Any, pattern: re.Pattern[str]) -> str | None:
    return value if isinstance(value, str) and pattern.fullmatch(value) else None


def _number(value: Any, low: int, high: int) -> int | None:
    # bool is an int in Python; a boolean status would be a corrupt log, not a number.
    return value if isinstance(value, int) and not isinstance(value, bool) and low <= value <= high else None


def _unique(values: Iterable[str], limit: int) -> list[str]:
    seen = []
    for value in values:
        if value not in seen:
            seen.append(value)
        if len(seen) == limit:
            break
    return seen


def selected(entry: dict[str, Any]) -> bool:
    """A failed request, or an error. A WARN needs a thrown exception to be worth a line; plain
    WARNs are Kafka/Hikari noise. An ERROR always counts — the one that explains a 500 may carry
    nothing but its logger name."""
    status = _number(entry.get('http_status'), 100, 599)
    if status is not None and status >= 400:
        return True
    level = entry.get('level')
    return level == 'ERROR' or (level == 'WARN' and isinstance(entry.get('stack_trace'), str))


def _safe_items(value: Any, pattern: re.Pattern[str], limit: int, max_length: int) -> list[str]:
    if not isinstance(value, list):
        return []
    return [item for item in value[:limit]
            if isinstance(item, str) and len(item) <= max_length and pattern.fullmatch(item)]


def _unexpected(entry: dict[str, Any], result: dict[str, Any]) -> tuple[list[str], list[str]]:
    if entry.get('level') != 'ERROR' or entry.get('logger_name') not in ERROR_HANDLERS:
        return [], []
    # A malformed new-format event must not resurrect different facts from an old message.
    if any(key in entry for key in ERROR_FIELDS):
        if entry.get('event') != 'api.unexpected_error':
            return [], []
        root = _safe_items([entry.get('error_type')], JAVA_CLASS, 1, 200)
        if not root:
            return [], []
        return (root + _safe_items(entry.get('exception_types'), JAVA_CLASS, MAX_EXCEPTIONS, 200),
                _safe_items(entry.get('application_frames'), APP_FRAME, MAX_FRAMES, 294))
    message = entry.get('message')
    if entry.get('logger_name') == GATEWAY_HANDLER and isinstance(message, str) and len(message) <= 512:
        match = LEGACY_ERROR.fullmatch(message)
        if match and len(match[2]) <= 200:
            result['requestId'] = match[1]
            return [match[2]], []
    return [], []


def fact(entry: dict[str, Any]) -> dict[str, Any]:
    result = {}
    for key, name, pattern in (('level', 'level', IDENT), ('logger_name', 'logger', CLASS),
                               ('event', 'event', ROUTE), ('http_method', 'method', IDENT),
                               ('http_route', 'route', ROUTE), ('request_id', 'requestId', IDENT),
                               ('trace_id', 'traceId', HEX), ('span_id', 'spanId', HEX)):
        value = _text(entry.get(key), pattern)
        if value is not None:
            result[name] = value
    for key, name, low, high in (('http_status', 'status', 100, 599),
                                 ('duration_ms', 'durationMs', 0, 1 << 40)):
        value = _number(entry.get(key), low, high)
        if value is not None:
            result[name] = value
    thrown, frames = _unexpected(entry, result)
    trace = entry.get('stack_trace')
    if isinstance(trace, str):
        trace = trace[:MAX_TRACE_BYTES]
        thrown += THROWN.findall(trace)
        frames += FRAME.findall(trace)
    # A class name has a bounded length; anything longer did not come from getName().
    thrown = [name for name in thrown if len(name) <= 200]
    thrown, frames = _unique(thrown, MAX_EXCEPTIONS), _unique(frames, MAX_FRAMES)
    if thrown:
        result['thrown'] = thrown
    if frames:
        result['frames'] = frames
    return result


def tail(path: Path) -> list[bytes]:
    """Read the end of a log without holding an unbounded service log in memory."""
    with open(path, 'rb') as log:
        log.seek(0, 2)
        size = log.tell()
        log.seek(max(0, size - MAX_LOG_BYTES))
        data = log.read(MAX_LOG_BYTES)
    if size > MAX_LOG_BYTES:
        data = data.partition(b'\n')[2]
    return data.splitlines()


def service_facts(path: Path) -> dict[str, Any]:
    found, dropped, scanned = [], 0, 0
    for line in tail(path):
        if len(line) > MAX_LINE_BYTES:
            continue
        try:
            entry = json.loads(line)
        except (ValueError, UnicodeError):
            continue  # JVM startup noise is not structured; it is also not evidence.
        if not isinstance(entry, dict):
            continue
        scanned += 1
        if not selected(entry):
            continue
        found.append(fact(entry))
        if len(found) > MAX_ENTRIES:
            # The failure that ended the suite is the newest event, so keep the tail.
            found.pop(0)
            dropped += 1
    # `scanned` separates "this service logged nothing worth reporting" from "the log is not in
    # the shape this reader expects". Without it an empty list would look like a clean run.
    return dict(facts=found, dropped=dropped, scanned=scanned)


def journal(directory: Path, services: Iterable[str]) -> dict[str, Any]:
    result = {}
    for name in services:
        try:
            result[name] = service_facts(directory / (name + '.log'))
        except OSError:
            result[name] = dict(unreadable=True)
    return result
