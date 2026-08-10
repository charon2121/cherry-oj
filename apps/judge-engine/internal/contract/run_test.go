package contract

import (
	"encoding/json"
	"testing"
)

func TestFileSourceUnmarshal(t *testing.T) {
	tests := []struct {
		name     string
		body     string
		wantText string
		wantRef  string
		wantErr  bool
	}{
		// 形态 1：裸字符串 → Text
		{"裸字符串", `"1 2\n"`, "1 2\n", "", false},
		{"空字符串", `""`, "", "", false},
		{"含转义", `"a\"b\\c"`, `a"b\c`, "", false},

		// 形态 2：对象，ref / text 二选一
		{"text 对象", `{"text":"1 2\n"}`, "1 2\n", "", false},
		{"ref 对象", `{"ref":"abc"}`, "", "abc", false},

		// 非法形态
		{"数字", `123`, "", "", true},
		{"布尔", `true`, "", "", true},
		{"null", `null`, "", "", true},
		{"空数组", `[]`, "", "", true},
		{"空对象", `{}`, "", "", true},
		{"未知字段", `{"invalid":"json"}`, "", "", true},
		{"ref 与 text 同时有", `{"ref":"abc","text":"hi"}`, "", "", true},
		{"text 空串等同未提供", `{"text":""}`, "", "", true},
		{"ref 空串等同未提供", `{"ref":""}`, "", "", true},
		{"非 JSON", `{`, "", "", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var got FileSource
			err := json.Unmarshal([]byte(tt.body), &got)
			if tt.wantErr {
				if err == nil {
					t.Fatalf("Unmarshal(%s) error = nil, want error", tt.body)
				}
				return
			}
			if err != nil {
				t.Fatalf("Unmarshal(%s) error = %v, want nil", tt.body, err)
			}
			if got.Text != tt.wantText {
				t.Errorf("Text = %q, want %q", got.Text, tt.wantText)
			}
			if got.Ref != tt.wantRef {
				t.Errorf("Ref = %q, want %q", got.Ref, tt.wantRef)
			}
		})
	}
}

func TestFileSourceUnmarshalInRunSpec(t *testing.T) {
	// 嵌在 RunSpec 里：stdin 裸字符串、inputs 对象，确认自定义 Unmarshal 仍生效
	const body = `{
		"command": ["/bin/cat"],
		"stdin": "hello\n",
		"inputs": {
			"main.cpp": {"text": "int main(){}"},
			"data": {"ref": "deadbeef"}
		},
		"limits": {"cpuNs": 1, "clockNs": 1, "memoryBytes": 1, "stdoutMaxBytes": 1, "stderrMaxBytes": 1}
	}`

	var spec RunSpec
	if err := json.Unmarshal([]byte(body), &spec); err != nil {
		t.Fatalf("Unmarshal RunSpec: %v", err)
	}
	if spec.Stdin == nil || spec.Stdin.Text != "hello\n" || spec.Stdin.Ref != "" {
		t.Fatalf("stdin = %+v, want Text=hello\\n", spec.Stdin)
	}
	in := spec.Inputs["main.cpp"]
	if in.Text != "int main(){}" || in.Ref != "" {
		t.Fatalf("inputs[main.cpp] = %+v", in)
	}
	data := spec.Inputs["data"]
	if data.Ref != "deadbeef" || data.Text != "" {
		t.Fatalf("inputs[data] = %+v", data)
	}
}

func TestFileSourceUnmarshalInRunSpecRejectsBadInput(t *testing.T) {
	const body = `{
		"command": ["/bin/true"],
		"inputs": {"x": {}},
		"limits": {"cpuNs": 1, "clockNs": 1, "memoryBytes": 1, "stdoutMaxBytes": 1, "stderrMaxBytes": 1}
	}`
	var spec RunSpec
	if err := json.Unmarshal([]byte(body), &spec); err == nil {
		t.Fatal("expected error for empty FileSource object in inputs")
	}
}
