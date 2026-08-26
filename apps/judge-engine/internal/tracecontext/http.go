package tracecontext

import (
	"log/slog"
	"net/http"
	"time"
)

func Middleware(logger *slog.Logger, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		started := time.Now()
		ctx := StartServer(r.Context(), r.Header)
		r = r.WithContext(ctx)
		response := &statusWriter{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(response, r)

		route := r.Pattern
		if route == "" {
			route = "unmatched"
		}
		Logger(ctx, logger).Info("http.server.completed",
			"event", "http.server.completed",
			"http_method", r.Method,
			"http_route", route,
			"http_status", response.status,
			"duration_ms", time.Since(started).Milliseconds(),
		)
	})
}

func Transport(base http.RoundTripper) http.RoundTripper {
	if base == nil {
		base = http.DefaultTransport
	}
	return roundTripperFunc(func(request *http.Request) (*http.Response, error) {
		outgoing := request.Clone(request.Context())
		Inject(outgoing.Header, outgoing.Context())
		return base.RoundTrip(outgoing)
	})
}

type statusWriter struct {
	http.ResponseWriter
	status      int
	wroteHeader bool
}

func (w *statusWriter) WriteHeader(status int) {
	if w.wroteHeader {
		return
	}
	w.wroteHeader = true
	w.status = status
	w.ResponseWriter.WriteHeader(status)
}

func (w *statusWriter) Write(p []byte) (int, error) {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
	return w.ResponseWriter.Write(p)
}

func (w *statusWriter) Unwrap() http.ResponseWriter {
	return w.ResponseWriter
}

type roundTripperFunc func(*http.Request) (*http.Response, error)

func (f roundTripperFunc) RoundTrip(request *http.Request) (*http.Response, error) {
	return f(request)
}
