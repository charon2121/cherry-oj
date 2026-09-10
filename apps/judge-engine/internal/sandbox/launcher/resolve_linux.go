package launcher

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

func marshalEvent(e Event) ([]byte, error) { return json.Marshal(e) }
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
	return "", fmt.Errorf("隔离根中没有可执行命令")
}
