package preflight

import (
	"encoding/json"
	"fmt"
	"io"
	"regexp"

	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/language"
	"cherry-oj/judge-engine/judge/node/wire"
)

// maxLockBytes 是包锁的读取上限：几百个软件包的锁也远小于它，超过就当作损坏。
const maxLockBytes = 4 << 20

var languageIDPattern = regexp.MustCompile(`^[a-z][a-z0-9-]{0,31}$`)

// Languages 返回本节点要声明的语言，在 Check 之后调用。
//
// 原生部署取发布里包锁的 languages：包锁是 rootfs 的软件包清单，装了哪些工具链只有它说得清，
// 它的摘要也已由部署清单核对过。开发环境（没有部署清单）取配置 node.languages。
// 每门语言都必须在 judge 的语言注册表里且不重复；这里只决定"声明什么"，能不能真的判由启动冒烟实测。
func Languages(j config.Settings) ([]string, error) {
	declared := j.Node.Languages
	if j.Node.DeploymentManifest != "" {
		var err error
		if declared, err = lockLanguages(j.Node.DeploymentManifest); err != nil {
			return nil, err
		}
	}
	return validLanguages(declared)
}

func lockLanguages(manifestPath string) ([]string, error) {
	file, err := openProtected(manifestPath)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	var manifest deploymentManifest
	if err := wire.Decode(io.LimitReader(file, 65537), &manifest); err != nil {
		return nil, fmt.Errorf("deployment manifest: %w", err)
	}
	entry, ok := manifest.Files["toolchainLock"]
	if !ok {
		return nil, fmt.Errorf("deployment file toolchainLock missing")
	}
	lock, err := openProtected(entry.Path)
	if err != nil {
		return nil, err
	}
	defer lock.Close()
	return decodeLockLanguages(io.LimitReader(lock, maxLockBytes+1))
}

// decodeLockLanguages 只取包锁里的 languages。包锁还有发行版快照与软件包清单，用宽松解码；
// 它的内容已由部署清单按摘要核对，不会是请求方能左右的输入。
func decodeLockLanguages(r io.Reader) ([]string, error) {
	var lock struct {
		Languages []string `json:"languages"`
	}
	body, err := io.ReadAll(r)
	if err != nil {
		return nil, fmt.Errorf("package lock: %w", err)
	}
	if len(body) > maxLockBytes {
		return nil, fmt.Errorf("package lock exceeds %d bytes", maxLockBytes)
	}
	if err := json.Unmarshal(body, &lock); err != nil {
		return nil, fmt.Errorf("package lock: %w", err)
	}
	return lock.Languages, nil
}

func validLanguages(declared []string) ([]string, error) {
	if len(declared) == 0 {
		return nil, fmt.Errorf("node declares no languages")
	}
	seen := make(map[string]bool, len(declared))
	for _, id := range declared {
		if !languageIDPattern.MatchString(id) {
			return nil, fmt.Errorf("invalid language id %q", id)
		}
		if seen[id] {
			return nil, fmt.Errorf("language %q declared twice", id)
		}
		if _, ok := language.Get(id); !ok {
			return nil, fmt.Errorf("language %q is not in the judge's language registry", id)
		}
		seen[id] = true
	}
	return append([]string(nil), declared...), nil
}
