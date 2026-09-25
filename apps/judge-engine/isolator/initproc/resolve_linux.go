//go:build linux && amd64

package initproc

import (
	"cherry-oj/judge-engine/isolator/startup"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

func marshalEvent(e startup.Event) ([]byte, error) { return json.Marshal(e) }

// resolveCommand 只在已切换的隔离根内调用，name 已由 Request.Validate 限定为裸名称。
// 工作区产物优先于固定工具目录；客户端 Env 中的 PATH 不参与可信启动阶段的解析。
func resolveCommand(name string) (string, error) {
	for _, dir := range []string{"/work", "/usr/bin", "/bin"} {
		p := filepath.Join(dir, name)
		st, err := os.Stat(p)
		if err == nil && st.Mode().IsRegular() && st.Mode()&0111 != 0 {
			return p, nil
		}
		if err != nil && !os.IsNotExist(err) {
			return "", err
		}
	}
	return "", fmt.Errorf("no executable command in the isolation root")
}
