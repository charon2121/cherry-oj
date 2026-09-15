package contract

import (
	"bytes"
	"encoding/json"
	"fmt"
	"math"
)

// 字段编号同时索引值与 present 位；按字段名映射，不能靠调用者记住下标。
const (
	limitCPU = iota
	limitClock
	limitMemory
	limitProcesses
	limitStdout
	limitStderr
	limitFieldCount
)

var limitNames = [limitFieldCount]string{
	limitCPU: "cpuNs", limitClock: "clockNs", limitMemory: "memoryBytes",
	limitProcesses: "maxProcesses", limitStdout: "stdoutMaxBytes", limitStderr: "stderrMaxBytes",
}

// ExplicitLimits 将 Go literal 的所有值（包括 0）标为显式预算。
// 普通 literal 中为 0 的字段视为缺省；已有调用者无需改成指针字段。
func ExplicitLimits(l Limits) Limits {
	l.present = (1 << len(limitNames)) - 1
	return l
}

func (l Limits) values() [limitFieldCount]int64 {
	return [limitFieldCount]int64{limitCPU: l.CPUNs, limitClock: l.ClockNs, limitMemory: l.MemoryBytes, limitProcesses: int64(l.MaxProcesses), limitStdout: l.StdoutMaxBytes, limitStderr: l.StderrMaxBytes}
}

func limitsFrom(values [limitFieldCount]int64, present uint8) Limits {
	return Limits{CPUNs: values[limitCPU], ClockNs: values[limitClock], MemoryBytes: values[limitMemory], MaxProcesses: int(values[limitProcesses]), StdoutMaxBytes: values[limitStdout], StderrMaxBytes: values[limitStderr], present: present}
}

// Validate 校验表示范围，0 的执行含义由执行入口处理，不能在这里替换默认值。
func (l Limits) Validate() error {
	for i, value := range l.values() {
		if value < 0 || (i == limitProcesses && value > math.MaxInt32) {
			return fmt.Errorf("limits.%s is out of the allowed range: %d", limitNames[i], value)
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
		return Limits{}, fmt.Errorf("invalid default limits: %w", err)
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
		return fmt.Errorf("limits must be an object")
	}
	var values [limitFieldCount]int64
	var present uint8
	for decoder.More() {
		token, err = decoder.Token()
		if err != nil {
			return fmt.Errorf("read limits field: %w", err)
		}
		index := limitFieldCount
		for i, name := range limitNames {
			if name == token {
				index = i
				break
			}
		}
		if index == limitFieldCount {
			return fmt.Errorf("unknown limits field: %v", token)
		}
		if present&(1<<index) != 0 {
			return fmt.Errorf("duplicate limits field: %v", token)
		}
		var value *int64
		if err := decoder.Decode(&value); err != nil {
			return fmt.Errorf("limits.%s: %w", limitNames[index], err)
		}
		if value == nil || *value < 0 || (index == limitProcesses && *value > math.MaxInt32) {
			return fmt.Errorf("limits.%s must be a non-negative integer within the allowed range", limitNames[index])
		}
		values[index] = *value
		present |= 1 << index
	}
	if _, err := decoder.Token(); err != nil {
		return fmt.Errorf("limits object was not terminated: %w", err)
	}
	*l = limitsFrom(values, present)
	return nil
}
