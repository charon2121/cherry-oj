package tracecontext

import (
	"bytes"
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
)

const incomingTraceParent = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"

func TestStartServerContinuesValidTraceAndRejectsInvalid(t *testing.T) {
	headers := make(http.Header)
	headers.Set(TraceParentHeader, incomingTraceParent)
	ctx := StartServer(context.Background(), headers)
	trace, ok := FromContext(ctx)
	if !ok {
		t.Fatal("缺少 Trace context")
	}
	if trace.TraceID != "0123456789abcdef0123456789abcdef" {
		t.Errorf("traceID=%q", trace.TraceID)
	}
	if trace.SpanID == "0123456789abcdef" || len(trace.SpanID) != 16 {
		t.Errorf("server spanID=%q", trace.SpanID)
	}

	invalidHeaders := make(http.Header)
	invalidHeaders.Set(TraceParentHeader, "not-w3c")
	invalid := StartServer(context.Background(), invalidHeaders)
	got, _ := FromContext(invalid)
	if got.TraceID == trace.TraceID || len(got.TraceID) != 32 {
		t.Errorf("非法 header 应建立新 Trace，got %q", got.TraceID)
	}
}

func TestMiddlewareAndTransportKeepOneTrace(t *testing.T) {
	var logOutput bytes.Buffer
	logger := slog.New(slog.NewJSONHandler(&logOutput, nil))

	var received http.Header
	downstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		received = r.Header.Clone()
		w.WriteHeader(http.StatusNoContent)
	}))
	t.Cleanup(downstream.Close)

	client := &http.Client{Transport: Transport(nil)}
	handler := Middleware(logger, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, downstream.URL, nil)
		if err != nil {
			t.Fatal(err)
		}
		req.Header.Set(BaggageHeader, "must-not-propagate=true")
		resp, err := client.Do(req)
		if err != nil {
			t.Fatal(err)
		}
		_ = resp.Body.Close()
		w.WriteHeader(http.StatusAccepted)
	}))

	request := httptest.NewRequest(http.MethodPost, "/judge", nil)
	request.Header.Set(TraceParentHeader, incomingTraceParent)
	request.Header.Set(RequestIDHeader, "req_1234567890abcdef")
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)

	if got := received.Get(TraceParentHeader); len(got) != 55 || got[:35] != incomingTraceParent[:35] {
		t.Errorf("traceparent=%q", got)
	}
	if got := received.Get(BaggageHeader); got != "" {
		t.Errorf("baggage 不得传播，got %q", got)
	}
	if got := received.Get(RequestIDHeader); got != "req_1234567890abcdef" {
		t.Errorf("request ID=%q", got)
	}

	var event map[string]any
	if err := json.Unmarshal(logOutput.Bytes(), &event); err != nil {
		t.Fatal(err)
	}
	if event["trace_id"] != "0123456789abcdef0123456789abcdef" {
		t.Errorf("event=%v", event)
	}
	if event["request_id"] != "req_1234567890abcdef" {
		t.Errorf("event=%v", event)
	}
	if event["http_method"] != http.MethodPost || event["http_status"] != float64(http.StatusAccepted) {
		t.Errorf("event=%v", event)
	}
}
