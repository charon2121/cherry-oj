package preflight

import (
	"fmt"
	"regexp"

	"cherry-oj/judge-engine/judge/language"
)

var languageIDPattern = regexp.MustCompile(`^[a-z][a-z0-9-]{0,31}$`)

// validLanguages 确认每门声明语言都在注册表里，实际编译运行由启动冒烟验证。
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
