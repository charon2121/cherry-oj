package config

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"reflect"
	"strconv"
	"strings"
	"time"
)

// EnvPrefix 是所有配置环境变量的前缀。
const EnvPrefix = "CHERRY_OJ"

func bytesReader(b []byte) io.Reader { return bytes.NewReader(b) }

// applyEnv 用环境变量覆盖已加载的配置。
//
// 变量名由 yaml 路径推出来，全大写、下划线分隔：
//
//	judge.testdataRoot        → CHERRY_OJ_JUDGE_TESTDATA_ROOT
//	judge.compile.cpuNs       → CHERRY_OJ_JUDGE_COMPILE_CPU_NS
//	sandbox.store.maxBlobBytes → CHERRY_OJ_SANDBOX_STORE_MAX_BLOB_BYTES
//
// 名字是**算出来的**，不是手写一张映射表——加一个配置项不用记得同步改两处。
func applyEnv(cfg *Config) error {
	return walk(reflect.ValueOf(cfg).Elem(), EnvPrefix)
}

func walk(v reflect.Value, prefix string) error {
	t := v.Type()
	for i := range t.NumField() {
		field := t.Field(i)
		tag := field.Tag.Get("yaml")
		if tag == "" || tag == "-" {
			continue
		}
		name := prefix + "_" + camelToUpperSnake(strings.Split(tag, ",")[0])
		fv := v.Field(i)

		// 嵌套结构体（Duration 除外，它虽是具名类型但要当标量处理）
		if fv.Kind() == reflect.Struct && fv.Type() != reflect.TypeOf(Duration(0)) {
			if err := walk(fv, name); err != nil {
				return err
			}
			continue
		}

		raw, ok := os.LookupEnv(name)
		if !ok {
			continue
		}
		if err := setScalar(fv, raw); err != nil {
			return fmt.Errorf("环境变量 %s=%q: %w", name, raw, err)
		}
	}
	return nil
}

func setScalar(fv reflect.Value, raw string) error {
	// Duration 单独处理：接受 "60s" 这种人类写法
	if fv.Type() == reflect.TypeOf(Duration(0)) {
		d, err := time.ParseDuration(raw)
		if err != nil {
			return fmt.Errorf("应当是时长如 \"60s\": %w", err)
		}
		fv.Set(reflect.ValueOf(Duration(d)))
		return nil
	}

	switch fv.Kind() {
	case reflect.String:
		fv.SetString(raw)
	case reflect.Bool:
		b, err := strconv.ParseBool(raw)
		if err != nil {
			return fmt.Errorf("应当是 true/false: %w", err)
		}
		fv.SetBool(b)
	case reflect.Int, reflect.Int8, reflect.Int16, reflect.Int32, reflect.Int64:
		n, err := strconv.ParseInt(raw, 10, 64)
		if err != nil {
			return fmt.Errorf("应当是整数: %w", err)
		}
		if fv.OverflowInt(n) {
			return fmt.Errorf("超出 %s 的范围", fv.Kind())
		}
		fv.SetInt(n)
	default:
		return fmt.Errorf("不支持的字段类型 %s", fv.Kind())
	}
	return nil
}

// camelToUpperSnake 把 yaml 里的 camelCase 键名变成环境变量片段。
//
//	testdataRoot   → TESTDATA_ROOT
//	cpuNs          → CPU_NS
//	sandboxURL     → SANDBOX_URL   （连续大写不拆开）
//	maxBlobBytes   → MAX_BLOB_BYTES
func camelToUpperSnake(s string) string {
	var b strings.Builder
	runes := []rune(s)
	for i, r := range runes {
		if i > 0 && isUpper(r) {
			prev := runes[i-1]
			next := rune(0)
			if i+1 < len(runes) {
				next = runes[i+1]
			}
			// 前一个是小写/数字 → 这是一个新词的开头（cpuNs 的 N）
			// 前一个是大写、后一个是小写 → 缩写结束、新词开始（URLPath 的 P）
			if !isUpper(prev) || (next != 0 && !isUpper(next)) {
				b.WriteByte('_')
			}
		}
		b.WriteRune(r)
	}
	return strings.ToUpper(b.String())
}

func isUpper(r rune) bool { return r >= 'A' && r <= 'Z' }
