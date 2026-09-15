// Package wire 是节点控制协议的严格 JSON 解码。
//
// 注册、心跳、安装与探测都读取有界的 JSON 报文，对未知字段与尾随内容的要求一致：
// 一个报文只能有一个 JSON 值，出现结构体没有的字段就报错。少了这条，拼错的键会被静默忽略，
// 而拼接的第二个值会被悄悄丢掉。
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
