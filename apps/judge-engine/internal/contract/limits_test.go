package contract_test

import (
	"encoding/json"
	"math"
	"os"
	"reflect"
	"testing"

	"cherry-oj/judge-engine/internal/contract"
)

func TestLimitsPreserveOmissionAndZero(t *testing.T) {
	defaults := contract.Limits{CPUNs: 100, ClockNs: 200, MemoryBytes: 300, MaxProcesses: 40, StdoutMaxBytes: 50, StderrMaxBytes: 60}
	for _, tc := range []struct {
		name, body  string
		cpu, stdout int64
	}{
		{"limits缺省", `{"command":["main"]}`, 100, 50},
		{"空对象", `{"limits":{}}`, 100, 50},
		{"显式零", `{"limits":{"cpuNs":0,"stdoutMaxBytes":0}}`, 0, 0},
		{"部分限额", `{"limits":{"cpuNs":7}}`, 7, 50},
	} {
		t.Run(tc.name, func(t *testing.T) {
			var spec contract.RunSpec
			if err := json.Unmarshal([]byte(tc.body), &spec); err != nil {
				t.Fatal(err)
			}
			// 经过再次序列化仍应保持缺省和显式 0，防止 helper 传输时丢失语义。
			encoded, err := json.Marshal(spec)
			if err != nil {
				t.Fatal(err)
			}
			var roundTrip contract.RunSpec
			if err = json.Unmarshal(encoded, &roundTrip); err != nil {
				t.Fatal(err)
			}
			got, err := roundTrip.Limits.WithDefaults(defaults)
			if err != nil {
				t.Fatal(err)
			}
			if got.CPUNs != tc.cpu || got.StdoutMaxBytes != tc.stdout || got.ClockNs != 200 || got.MaxProcesses != 40 {
				t.Fatalf("limits=%+v", got)
			}
		})
	}
}

func TestLimitsRejectInvalidWireValues(t *testing.T) {
	for _, body := range []string{
		`null`, `[]`, `1`, `{"cpuNs":null}`, `{"cpuNs":-1}`, `{"cpuNs":9223372036854775808}`,
		`{"cpuNs":1.5}`, `{"cpuNs":"1"}`, `{"maxProcesses":2147483648}`, `{"maxProcesses":-1}`,
		`{"cpuns":1}`, `{"cpuNs":1,"cpuNs":0}`, `{"memoryBytes":true}`, `{"stderrMaxBytes":-1}`,
	} {
		t.Run(body, func(t *testing.T) {
			var spec contract.RunSpec
			if err := json.Unmarshal([]byte(`{"limits":`+body+`}`), &spec); err == nil {
				t.Fatal("invalid limits accepted")
			}
		})
	}
}

func TestLimitsGoConstructionAndDecodeReuse(t *testing.T) {
	defaults := contract.Limits{CPUNs: 10, StdoutMaxBytes: 20}
	omitted, err := (contract.Limits{}).WithDefaults(defaults)
	if err != nil {
		t.Fatal(err)
	}
	explicit, err := contract.ExplicitLimits(contract.Limits{}).WithDefaults(defaults)
	if err != nil {
		t.Fatal(err)
	}
	if omitted.CPUNs != 10 || explicit.CPUNs != 0 {
		t.Fatalf("omitted=%+v explicit=%+v", omitted, explicit)
	}
	encoded, err := json.Marshal(explicit)
	if err != nil {
		t.Fatal(err)
	}
	var fields map[string]int64
	if err = json.Unmarshal(encoded, &fields); err != nil {
		t.Fatal(err)
	}
	if len(fields) != 6 {
		t.Fatalf("explicit zero fields lost: %s", encoded)
	}
	var reused contract.Limits
	if err = json.Unmarshal([]byte(`{"cpuNs":0}`), &reused); err != nil {
		t.Fatal(err)
	}
	if err = json.Unmarshal([]byte(`{}`), &reused); err != nil {
		t.Fatal(err)
	}
	result, err := reused.WithDefaults(defaults)
	if err != nil {
		t.Fatal(err)
	}
	if result.CPUNs != 10 {
		t.Fatal("previous decode presence leaked")
	}
	for _, invalid := range []contract.Limits{{CPUNs: -1}, {MemoryBytes: -1}, {MaxProcesses: -1}} {
		if _, err = invalid.WithDefaults(defaults); err == nil {
			t.Fatal("invalid Go literal accepted")
		}
		if _, err = json.Marshal(invalid); err == nil {
			t.Fatal("invalid Go literal marshaled")
		}
	}
}

func TestRunSchemaLimitsAndSignalAlign(t *testing.T) {
	data, err := os.ReadFile("../../../../contracts/run.schema.json")
	if err != nil {
		t.Fatal(err)
	}
	var schema struct {
		Definitions map[string]struct {
			AdditionalProperties *bool `json:"additionalProperties"`
			Properties           map[string]struct {
				Type        string
				Minimum     int64
				Maximum     json.Number
				Description string
			}
			Enum []string
		}
	}
	if err = json.Unmarshal(data, &schema); err != nil {
		t.Fatal(err)
	}
	limits := schema.Definitions["Limits"]
	if limits.AdditionalProperties == nil || *limits.AdditionalProperties {
		t.Fatal("unknown limits fields allowed")
	}
	typ := reflect.TypeFor[contract.Limits]()
	for i := 0; i < typ.NumField(); i++ {
		field := typ.Field(i)
		if !field.IsExported() {
			continue
		}
		name := field.Tag.Get("json")
		if name == "maxProcesses,omitempty" {
			name = "maxProcesses"
		}
		rule, ok := limits.Properties[name]
		if !ok || rule.Type != "integer" || rule.Minimum != 0 {
			t.Fatalf("missing numeric rule for %s", name)
		}
		want := int64(math.MaxInt64)
		if name == "maxProcesses" {
			want = math.MaxInt32
		}
		maximum, err := rule.Maximum.Int64()
		if err != nil || maximum != want {
			t.Fatalf("maximum %s = %s", name, rule.Maximum)
		}
	}
	if rule := schema.Definitions["RunResult"].Properties["signal"]; rule.Type != "integer" || rule.Minimum != 0 {
		t.Fatal("signal missing from schema")
	}
}
