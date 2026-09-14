// 使用包内测试核对不能在 seccomp 后动态编码的私有 ready 消息。
package launcher

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestInitReadyMessageMatchesEventProtocol(t *testing.T) {
	var event Event
	decoder := json.NewDecoder(strings.NewReader(initReadyMessage))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&event); err != nil {
		t.Fatal(err)
	}
	if want := (Event{Version: Version, Kind: "ready"}); event != want {
		t.Fatalf("过滤后发送的固定消息与事件协议不符: got=%+v want=%+v", event, want)
	}
}
