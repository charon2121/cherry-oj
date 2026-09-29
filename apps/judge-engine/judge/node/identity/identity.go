package identity

import (
	"crypto/rand"
	"fmt"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
	"cherry-oj/judge-engine/judge/language"
)

const (
	uuidBytes       = 16
	uuidVersionMask = 0x0f
	uuidVersion4    = 0x40
	uuidVariantMask = 0x3f
	uuidVariant     = 0x80
)

// Identity 是本节点这次进程的身份，构造后不再变化。
type Identity struct{ registration contract.NodeRegistration }

// Registration 返回身份的副本。切片头是值、底层数组共享，因此语言清单要另行复制，
// 否则调用方改一下就改了本节点的身份。
func (i Identity) Registration() contract.NodeRegistration {
	r := i.registration
	r.Languages = append([]string(nil), r.Languages...)
	return r
}

// New 计算本次进程的身份：每次调用都生成新的 sessionId。
func New(s config.Settings) (Identity, error) {
	session, err := sessionID()
	if err != nil {
		return Identity{}, err
	}
	languages, err := declaredLanguages()
	if err != nil {
		return Identity{}, err
	}
	return Identity{registration: contract.NodeRegistration{
		NodeID:    s.Node.ID,
		SessionID: session,
		Endpoint:  strings.TrimRight(s.Node.AdvertiseURL, "/"),
		Languages: languages,
	}}, nil
}

// declaredLanguages 只声明 cpp，**这不是遗漏**。
//
// 语言注册表里还有 python 和 java，但整条业务链路目前只支持 cpp：judging-service 的
// TrialController、SubmissionExecutionProfileController 与 FormalWorker 都把 languageId
// 限死为 cpp，也只有 cpp 的标定流程。控制面按声明把判题路由给节点，多声明一种走不通的语言
// 只会让节点看起来能判它。要真正支持 python/java，必须先在 judging-service 放开语言约束
// 并提供对应的标定，那是一次跨服务的产品变更，不能从判题机这一侧单方面声明。
func declaredLanguages() ([]string, error) {
	cpp, ok := language.Get("cpp")
	if !ok {
		return nil, fmt.Errorf("language registry is missing cpp")
	}
	return []string{cpp.Name}, nil
}

func sessionID() (string, error) {
	b := make([]byte, uuidBytes)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	b[6] = (b[6] & uuidVersionMask) | uuidVersion4
	b[8] = (b[8] & uuidVariantMask) | uuidVariant
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:16]), nil
}
