package node

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"cherry-oj/judge-engine/internal/config"
	"cherry-oj/judge-engine/internal/contract"
)

// ProbeEnvironment reads the execution environment through sandbox's existing,
// bounded /run interface. No user input is interpolated into the probe program.
func ProbeEnvironment(ctx context.Context, j config.JudgeConfig) (config.JudgeConfig, error) {
	c := &http.Client{Timeout: 15 * time.Second, CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	get := func(path string, payload any, result any) error {
		var body io.Reader
		method := http.MethodGet
		if payload != nil {
			b, err := json.Marshal(payload)
			if err != nil {
				return err
			}
			body = bytes.NewReader(b)
			method = http.MethodPost
		}
		r, err := http.NewRequestWithContext(ctx, method, strings.TrimRight(j.SandboxURL, "/")+path, body)
		if err != nil {
			return err
		}
		r.Header.Set("Content-Type", "application/json")
		response, err := c.Do(r)
		if err != nil {
			return fmt.Errorf("sandbox environment probe unavailable")
		}
		defer response.Body.Close()
		if response.StatusCode != 200 {
			return fmt.Errorf("sandbox environment probe rejected")
		}
		b, err := io.ReadAll(io.LimitReader(response.Body, 16385))
		if err != nil || len(b) > 16384 {
			return fmt.Errorf("sandbox environment probe response invalid")
		}
		return json.Unmarshal(b, result)
	}
	var version struct {
		Name    string `json:"name"`
		Version string `json:"version"`
	}
	if err := get("/version", nil, &version); err != nil {
		return j, err
	}
	if version.Name != "cherry-oj-sandbox" || version.Version == "" || len(version.Version) > 128 {
		return j, fmt.Errorf("sandbox version invalid")
	}
	var result contract.RunResult
	spec := contract.RunSpec{Command: []string{"/usr/bin/python3", "-c", environmentProbe}, Limits: contract.Limits{CPUNs: 2_000_000_000, ClockNs: 5_000_000_000, MemoryBytes: 134217728, MaxProcesses: 8, StdoutMaxBytes: 8192, StderrMaxBytes: 1024}}
	if err := get("/run", spec, &result); err != nil {
		return j, err
	}
	if result.Status != contract.StatusOK || result.ExitCode != 0 {
		return j, fmt.Errorf("sandbox environment probe failed")
	}
	var m struct{ Architecture, CPUModel, OSVersion, KernelVersion, ToolchainVersion, RuntimeDigest string }
	if err := decodeJSON(strings.NewReader(result.Stdout), &m); err != nil {
		return j, fmt.Errorf("sandbox environment metadata invalid")
	}
	for _, field := range []struct {
		value string
		limit int
	}{{m.Architecture, 32}, {m.CPUModel, 256}, {m.OSVersion, 128}, {m.KernelVersion, 128}, {m.ToolchainVersion, 128}, {m.RuntimeDigest, 128}} {
		if field.value == "" || len(field.value) > field.limit {
			return j, fmt.Errorf("sandbox environment metadata missing or too long")
		}
	}
	j.Node.Architecture = m.Architecture
	j.Node.CPUModel = m.CPUModel
	j.Node.OSVersion = m.OSVersion
	j.Node.KernelVersion = m.KernelVersion
	j.Node.ToolchainVersion = m.ToolchainVersion
	j.Node.SandboxVersion = version.Version
	j.Node.RuntimeDigest = m.RuntimeDigest
	return j, nil
}

// CPU features omit volatile per-core MHz/counters. Runtime digest covers actual
// sandbox binary and container quotas, which also affect calibration reuse.
const environmentProbe = `import hashlib,json,pathlib,platform,subprocess
cpu=pathlib.Path('/proc/cpuinfo').read_text()
keys={'model name','CPU implementer','CPU architecture','CPU variant','CPU part','CPU revision','Hardware','Features','flags'}
facts=sorted({line.strip() for line in cpu.splitlines() if ':' in line and line.split(':',1)[0].strip() in keys})
if not facts: raise RuntimeError('CPU identity unavailable')
digest=lambda b:hashlib.sha256(b).hexdigest()
cpu_model=next((s.split(':',1)[1].strip() for s in facts if s.startswith('model name')),platform.machine())
osinfo=platform.freedesktop_os_release()
runtime={}
for name in ('cpu.max','memory.max','cpuset.cpus.effective'):
 p=pathlib.Path('/sys/fs/cgroup')/name
 runtime[name]=p.read_text().strip() if p.exists() else 'unavailable'
runtime['binary']=digest(pathlib.Path('/usr/local/bin/sandbox').read_bytes())
print(json.dumps(dict(Architecture=platform.machine(),CPUModel=cpu_model[:160]+' sha256:'+digest('\n'.join(facts).encode()),OSVersion=osinfo['PRETTY_NAME'],KernelVersion=platform.release(),ToolchainVersion=subprocess.check_output(['/usr/bin/g++','--version'],timeout=2).decode().splitlines()[0],RuntimeDigest=digest(json.dumps(runtime,sort_keys=True).encode()))))
`
