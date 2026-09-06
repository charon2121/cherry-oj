package contract_test

import (
	"cherry-oj/judge-engine/internal/contract"
	"encoding/json"
	"os"
	"reflect"
	"testing"
)

func TestNodeSchemaExamplesRoundTripWithoutDroppedFields(t *testing.T) {
	b, err := os.ReadFile("../../../../contracts/judge-node.schema.json")
	if err != nil {
		t.Fatal(err)
	}
	var schema struct {
		Defs map[string]struct {
			Examples []json.RawMessage `json:"examples"`
		} `json:"$defs"`
	}
	if err := json.Unmarshal(b, &schema); err != nil {
		t.Fatal(err)
	}
	types := map[string]any{"Registration": &contract.NodeRegistration{}, "Heartbeat": &contract.NodeHeartbeat{}, "Lease": &contract.NodeLease{}, "Install": &contract.NodeInstall{}, "Receipt": &contract.NodeReceipt{}}
	for name, value := range types {
		t.Run(name, func(t *testing.T) {
			if len(schema.Defs[name].Examples) != 1 {
				t.Fatal("missing fixture")
			}
			raw := schema.Defs[name].Examples[0]
			if err := json.Unmarshal(raw, value); err != nil {
				t.Fatal(err)
			}
			got, err := json.Marshal(value)
			if err != nil {
				t.Fatal(err)
			}
			var a, b any
			if err := json.Unmarshal(raw, &a); err != nil {
				t.Fatal(err)
			}
			if err := json.Unmarshal(got, &b); err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(a, b) {
				t.Fatalf("round trip differs: %s", got)
			}
		})
	}
}
