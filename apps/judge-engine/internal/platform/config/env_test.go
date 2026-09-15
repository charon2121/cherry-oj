package config

import "testing"

func TestCamelToUpperSnake(t *testing.T) {
	tests := []struct{ in, want string }{
		{"testdataRoot", "TESTDATA_ROOT"},
		{"cpuNs", "CPU_NS"},
		{"httpAddr", "HTTP_ADDR"},
		{"maxBlobBytes", "MAX_BLOB_BYTES"},
		{"sandboxURL", "SANDBOX_URL"},
		{"judge", "JUDGE"},
		{"revealExpected", "REVEAL_EXPECTED"},
		{"inlineThresholdBytes", "INLINE_THRESHOLD_BYTES"},
	}
	for _, tt := range tests {
		if got := camelToUpperSnake(tt.in); got != tt.want {
			t.Errorf("camelToUpperSnake(%q)=%q want %q", tt.in, got, tt.want)
		}
	}
}
