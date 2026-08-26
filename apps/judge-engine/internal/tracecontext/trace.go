// Package tracecontext 在 Java 与 Go HTTP 边界之间传播 W3C Trace Context。
// Trace 只存在于 transport header 和 context，不进入 JudgeRequest 或 RunSpec。
package tracecontext

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"sync/atomic"
	"time"
)

const (
	TraceParentHeader = "traceparent"
	TraceStateHeader  = "tracestate"
	BaggageHeader     = "baggage"
	RequestIDHeader   = "X-Request-Id"
)

type contextKey struct{}

type Trace struct {
	TraceID    string
	SpanID     string
	Flags      string
	TraceState string
	RequestID  string
}

func StartServer(ctx context.Context, headers http.Header) context.Context {
	traceID, flags, ok := parseTraceParent(headers.Get(TraceParentHeader))
	state := ""
	if !ok {
		traceID = randomHex(16)
		flags = "01"
	} else if incomingState := headers.Get(TraceStateHeader); validTraceState(incomingState) {
		state = incomingState
	}
	trace := Trace{
		TraceID:    traceID,
		SpanID:     randomHex(8),
		Flags:      flags,
		TraceState: state,
	}
	if requestID := headers.Get(RequestIDHeader); validRequestID(requestID) {
		trace.RequestID = requestID
	}
	return context.WithValue(ctx, contextKey{}, trace)
}

func FromContext(ctx context.Context) (Trace, bool) {
	trace, ok := ctx.Value(contextKey{}).(Trace)
	return trace, ok
}

func Logger(ctx context.Context, base *slog.Logger) *slog.Logger {
	trace, ok := FromContext(ctx)
	if !ok {
		return base
	}
	logger := base.With("trace_id", trace.TraceID, "span_id", trace.SpanID)
	if trace.RequestID != "" {
		logger = logger.With("request_id", trace.RequestID)
	}
	return logger
}

func Inject(headers http.Header, ctx context.Context) {
	headers.Del(BaggageHeader)
	trace, ok := FromContext(ctx)
	if !ok {
		headers.Del(TraceParentHeader)
		headers.Del(TraceStateHeader)
		headers.Del(RequestIDHeader)
		return
	}
	headers.Set(TraceParentHeader, fmt.Sprintf("00-%s-%s-%s", trace.TraceID, trace.SpanID, trace.Flags))
	if trace.TraceState == "" {
		headers.Del(TraceStateHeader)
	} else {
		headers.Set(TraceStateHeader, trace.TraceState)
	}
	if trace.RequestID == "" {
		headers.Del(RequestIDHeader)
	} else {
		headers.Set(RequestIDHeader, trace.RequestID)
	}
}

func parseTraceParent(value string) (traceID, flags string, ok bool) {
	if len(value) != 55 || value[2] != '-' || value[35] != '-' || value[52] != '-' || value[:2] != "00" {
		return "", "", false
	}
	if value != strings.ToLower(value) {
		return "", "", false
	}
	traceID, parentID, flags := value[3:35], value[36:52], value[53:55]
	if !validHex(traceID, 16) || !validHex(parentID, 8) || !validHex(flags, 1) {
		return "", "", false
	}
	if allZero(traceID) || allZero(parentID) {
		return "", "", false
	}
	return traceID, flags, true
}

func validHex(value string, bytes int) bool {
	if len(value) != bytes*2 {
		return false
	}
	decoded, err := hex.DecodeString(value)
	return err == nil && len(decoded) == bytes
}

func allZero(value string) bool {
	return strings.Trim(value, "0") == ""
}

func validTraceState(value string) bool {
	return len(value) <= 512 && !strings.ContainsAny(value, "\r\n")
}

func validRequestID(value string) bool {
	if value == "" || len(value) > 128 {
		return false
	}
	for _, ch := range value {
		if (ch < 'a' || ch > 'z') && (ch < 'A' || ch > 'Z') &&
			(ch < '0' || ch > '9') && ch != '_' && ch != '-' {
			return false
		}
	}
	return true
}

var fallbackCounter atomic.Uint64

func randomHex(size int) string {
	value := make([]byte, size)
	if _, err := rand.Read(value); err == nil {
		return hex.EncodeToString(value)
	}
	seed := fmt.Sprintf("%d:%d", time.Now().UnixNano(), fallbackCounter.Add(1))
	digest := sha256.Sum256([]byte(seed))
	return hex.EncodeToString(digest[:size])
}
