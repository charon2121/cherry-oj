package contract

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math"
)

var limitNames = [...]string{"cpuNs", "clockNs", "memoryBytes", "maxProcesses", "stdoutMaxBytes", "stderrMaxBytes"}

// ExplicitLimits 将 Go literal 的所有值（包括 0）标为显式预算。
// 普通 literal 中为 0 的字段视为缺省；已有调用者无需改成指针字段。
func ExplicitLimits(l Limits) Limits {
	l.present = (1 << len(limitNames)) - 1
	return l
}

func (l Limits) values() [6]int64 {
	return [6]int64{l.CPUNs, l.ClockNs, l.MemoryBytes, int64(l.MaxProcesses), l.StdoutMaxBytes, l.StderrMaxBytes}
}

func limitsFrom(values [6]int64, present uint8) Limits {
	return Limits{CPUNs: values[0], ClockNs: values[1], MemoryBytes: values[2], MaxProcesses: int(values[3]), StdoutMaxBytes: values[4], StderrMaxBytes: values[5], present: present}
}

// Validate 校验表示范围，0 的执行含义由执行入口处理，不能在这里替换默认值。
func (l Limits) Validate() error {
	for i, value := range l.values() {
		if value < 0 || (i == 3 && value > math.MaxInt32) {
			return fmt.Errorf("limits.%s 超出允许范围: %d", limitNames[i], value)
		}
	}
	return nil
}

// WithDefaults 仅替换未提供的字段，结果的每一项都已明确。
// 默认值由节点配置传入，契约包不依赖配置，也不决定节点容量。
func (l Limits) WithDefaults(defaults Limits) (Limits, error) {
	if err := l.Validate(); err != nil {
		return Limits{}, err
	}
	if err := defaults.Validate(); err != nil {
		return Limits{}, fmt.Errorf("默认限额无效: %w", err)
	}
	values, fallback := l.values(), defaults.values()
	for i := range values {
		if l.present&(1<<i) == 0 && values[i] == 0 {
			values[i] = fallback[i]
		}
	}
	return ExplicitLimits(limitsFrom(values, 0)), nil
}

func (l Limits) MarshalJSON() ([]byte, error) {
	if err := l.Validate(); err != nil {
		return nil, err
	}
	fields := make(map[string]int64, len(limitNames))
	for i, value := range l.values() {
		if value != 0 || l.present&(1<<i) != 0 {
			fields[limitNames[i]] = value
		}
	}
	return json.Marshal(fields)
}

func (l *Limits) UnmarshalJSON(data []byte) error {
	decoder := json.NewDecoder(bytes.NewReader(data))
	token, err := decoder.Token()
	if err != nil || token != json.Delim('{') {
		return fmt.Errorf("limits 必须是对象")
	}
	var values [6]int64
	var present uint8
	for decoder.More() {
		token, err = decoder.Token()
		if err != nil {
			return fmt.Errorf("读取 limits 字段: %w", err)
		}
		index := -1
		for i, name := range limitNames {
			if name == token {
				index = i
				break
			}
		}
		if index < 0 {
			return fmt.Errorf("未知 limits 字段: %v", token)
		}
		if present&(1<<index) != 0 {
			return fmt.Errorf("重复 limits 字段: %v", token)
		}
		var value *int64
		if err := decoder.Decode(&value); err != nil {
			return fmt.Errorf("limits.%s: %w", limitNames[index], err)
		}
		if value == nil || *value < 0 || (index == 3 && *value > math.MaxInt32) {
			return fmt.Errorf("limits.%s 必须是允许范围内的非负整数", limitNames[index])
		}
		values[index] = *value
		present |= 1 << index
	}
	if _, err := decoder.Token(); err != nil {
		return fmt.Errorf("limits 对象未结束: %w", err)
	}
	*l = limitsFrom(values, present)
	return nil
}
