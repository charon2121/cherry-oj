"""Bounded, allowlisted facts from the private service logs.

The services log structured JSON (logstash), so a failing request leaves a machine-shaped
record behind: which service, which route, which status, how long it took. Those fields are
written by our own code and carry no submitted content, so they can be exported. Everything
else in the log — `message`, exception text, MDC values — can hold credentials, tokens or
user data, and is never read out. A field that does not match its expected shape is dropped
rather than truncated: a truncated free-text field is still free text.
"""
import json
import re

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


def _text(value, pattern):
    return value if isinstance(value, str) and pattern.fullmatch(value) else None


def _number(value, low, high):
    # bool is an int in Python; a boolean status would be a corrupt log, not a number.
    return value if isinstance(value, int) and not isinstance(value, bool) and low <= value <= high else None


def _unique(values, limit):
    seen = []
    for value in values:
        if value not in seen:
            seen.append(value)
        if len(seen) == limit:
            break
    return seen


def selected(entry):
    """A failed request, or an error that carries a thrown exception. Plain WARN lines are noise."""
    status = _number(entry.get('http_status'), 100, 599)
    if status is not None and status >= 400:
        return True
    return entry.get('level') in ('ERROR', 'WARN') and isinstance(entry.get('stack_trace'), str)


def fact(entry):
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
    trace = entry.get('stack_trace')
    if isinstance(trace, str):
        trace = trace[:MAX_TRACE_BYTES]
        thrown = _unique(THROWN.findall(trace), MAX_EXCEPTIONS)
        frames = _unique(FRAME.findall(trace), MAX_FRAMES)
        if thrown:
            result['thrown'] = thrown
        if frames:
            result['frames'] = frames
    return result


def tail(path):
    """Read the end of a log without holding an unbounded service log in memory."""
    with open(path, 'rb') as log:
        log.seek(0, 2)
        size = log.tell()
        log.seek(max(0, size - MAX_LOG_BYTES))
        data = log.read(MAX_LOG_BYTES)
    if size > MAX_LOG_BYTES:
        data = data.partition(b'\n')[2]
    return data.splitlines()


def service_facts(path):
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


def journal(directory, services):
    result = {}
    for name in services:
        try:
            result[name] = service_facts(directory / (name + '.log'))
        except OSError:
            result[name] = dict(unreadable=True)
    return result
