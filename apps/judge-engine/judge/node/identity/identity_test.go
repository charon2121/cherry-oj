package identity

import (
	"testing"

	"cherry-oj/judge-engine/judge/config"
)

// 身份只有 nodeId、每次启动都变的 sessionId、地址和语言；同一配置两次启动必须是两个会话。
func TestEachProcessGetsANewSessionUnderTheSameNodeID(t *testing.T) {
	c := config.Default().Judge
	c.Node.AdvertiseURL = "http://127.0.0.1:5051/"
	first, err := New(c)
	if err != nil {
		t.Fatal(err)
	}
	second, err := New(c)
	if err != nil {
		t.Fatal(err)
	}
	a, b := first.Registration(), second.Registration()
	if a.NodeID != c.Node.ID || b.NodeID != c.Node.ID {
		t.Fatalf("nodeId 应取自配置: %q %q", a.NodeID, b.NodeID)
	}
	if a.SessionID == b.SessionID {
		t.Fatal("两次启动复用了同一个 sessionId")
	}
	if a.Endpoint != "http://127.0.0.1:5051" {
		t.Errorf("endpoint 应去掉末尾斜杠: %q", a.Endpoint)
	}
	if len(a.Languages) != 1 || a.Languages[0] != "cpp" {
		t.Errorf("languages = %v", a.Languages)
	}
	a.Languages[0] = "changed"
	if first.Registration().Languages[0] != "cpp" {
		t.Error("调用方修改副本改到了节点身份")
	}
}
