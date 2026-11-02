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

// 字符串列表用逗号分隔，前后空白去掉；空项多半是拼写错误，整条拒绝而不是悄悄丢掉。
func TestStringListsAreCommaSeparated(t *testing.T) {
	type settings struct {
		Languages []string `yaml:"languages"`
	}
	t.Setenv(EnvPrefix+"_LANGUAGES", "cpp, c")
	var got settings
	if err := applyEnv(&got); err != nil {
		t.Fatal(err)
	}
	if len(got.Languages) != 2 || got.Languages[0] != "cpp" || got.Languages[1] != "c" {
		t.Fatalf("languages = %q", got.Languages)
	}
	for _, raw := range []string{"cpp,,c", "", " , "} {
		t.Setenv(EnvPrefix+"_LANGUAGES", raw)
		if err := applyEnv(&settings{}); err == nil {
			t.Errorf("%q should be rejected", raw)
		}
	}
}
