package helper

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"path/filepath"
)

func LoadConfig(path string) (Config, error) {
	var c Config
	if !filepath.IsAbs(path) {
		return c, fmt.Errorf("配置需要绝对路径")
	}
	if err := checkConfigPath(path); err != nil {
		return c, err
	}
	f, err := os.Open(path)
	if err != nil {
		return c, err
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		return c, err
	}
	if !st.Mode().IsRegular() || st.Size() > 64<<10 {
		return c, fmt.Errorf("配置不是有界普通文件")
	}
	d := json.NewDecoder(f)
	d.DisallowUnknownFields()
	if err = d.Decode(&c); err != nil {
		return c, err
	}
	var extra any
	if d.Decode(&extra) != io.EOF {
		return c, fmt.Errorf("配置有尾随内容")
	}
	return c, c.Validate()
}
