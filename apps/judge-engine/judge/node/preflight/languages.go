package preflight

import (
	"fmt"
	"io"
	"regexp"

	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/language"
	"cherry-oj/judge-engine/judge/node/wire"
)

var languageIDPattern = regexp.MustCompile(`^[a-z][a-z0-9-]{0,31}$`)

// Languages 返回本节点要声明的语言，在 Check 之后调用。
//
// 原生部署取部署清单登记了工具链的语言：每门语言一棵独立的 rootfs（releases/<发布>/toolchains/<语言>/），
// 装了哪些工具链只有发布说得清，部署清单按摘要绑定了它们。开发环境（没有部署清单）取配置 node.languages。
// 每门语言都必须在 judge 的语言注册表里且不重复；这里只决定"声明什么"，能不能真的判由启动冒烟实测。
func Languages(j config.Settings) ([]string, error) {
	declared := j.Node.Languages
	if j.Node.DeploymentManifest != "" {
		var err error
		if declared, err = manifestLanguages(j.Node.DeploymentManifest); err != nil {
			return nil, err
		}
	}
	return validLanguages(declared)
}

func manifestLanguages(manifestPath string) ([]string, error) {
	file, err := openProtected(manifestPath)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	var manifest deploymentManifest
	if err := wire.Decode(io.LimitReader(file, 65537), &manifest); err != nil {
		return nil, fmt.Errorf("deployment manifest: %w", err)
	}
	return toolchainLanguages(manifest), nil
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
