package wire

import (
	"encoding/json"
	"fmt"
	"io"
)

// Decode 读取恰好一个 JSON 值，拒绝未知字段与尾随内容。
func Decode(r io.Reader, value any) error {
	d := json.NewDecoder(r)
	d.DisallowUnknownFields()
	if err := d.Decode(value); err != nil {
		return err
	}
	var extra any
	if err := d.Decode(&extra); err != io.EOF {
		return fmt.Errorf("trailing JSON")
	}
	return nil
}
