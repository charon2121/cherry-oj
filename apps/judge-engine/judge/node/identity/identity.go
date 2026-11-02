package identity

import (
	"crypto/rand"
	"fmt"
	"strings"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/config"
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
//
// languages 是启动时确定并逐门实测通过的语言（见 preflight.Languages 与 judge 的启动冒烟），
// 这里只负责把它们写进身份；控制面按声明把判题路由给节点，所以不能声明没有实测过的语言。
func New(s config.Settings, languages []string) (Identity, error) {
	if len(languages) == 0 {
		return Identity{}, fmt.Errorf("node identity needs at least one language")
	}
	session, err := sessionID()
	if err != nil {
		return Identity{}, err
	}
	return Identity{registration: contract.NodeRegistration{
		NodeID:    s.Node.ID,
		SessionID: session,
		Endpoint:  strings.TrimRight(s.Node.AdvertiseURL, "/"),
		Languages: append([]string(nil), languages...),
	}}, nil
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
