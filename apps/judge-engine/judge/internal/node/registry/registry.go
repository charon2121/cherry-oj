// Package registry 维护节点在控制面的注册与心跳。
// 控制面不可用只延迟注册，不影响 Judge 自身的健康入口。
package registry

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"regexp"
	"strings"
	"time"

	"cherry-oj/judge-engine/internal/contract"
	"cherry-oj/judge-engine/judge/internal/config"
	"cherry-oj/judge-engine/judge/internal/node/wire"
)

// ErrIdentityConflict 表示控制面已用同一 nodeId 登记了不同身份。
// 这不是可重试的故障：继续心跳只会反复被拒，必须由人确认是哪一边的身份不对。
var ErrIdentityConflict = errors.New("node identity conflict")

var uuidPattern = regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)

// Registry 持有注册身份与控制面客户端。
type Registry struct {
	cfg          config.Node
	registration contract.NodeRegistration
	client       *http.Client
	logger       *slog.Logger
}

func New(cfg config.Node, registration contract.NodeRegistration, logger *slog.Logger) *Registry {
	return &Registry{cfg: cfg, registration: registration, logger: logger,
		client: &http.Client{Timeout: cfg.RequestTimeout.Std(),
			CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}}
}

const (
	initialRetryDelay     = time.Second
	retryMultiplier       = 2
	maxRetryDelay         = 30 * time.Second
	leaseRenewalDivisor   = 3
	minLeaseDuration      = 3 * time.Millisecond
	maxLeaseDuration      = 300 * time.Second
	maxLeaseResponseBytes = 4096
)

// Run 随进程 context 退出；控制面不可用只延迟注册，不关闭 Judge 健康入口。
func (n *Registry) Run(ctx context.Context) {
	registered := false
	backoff := initialRetryDelay
	for ctx.Err() == nil {
		var payload any = n.registration
		route := "register"
		if registered {
			route = "heartbeat"
			payload = contract.NodeHeartbeat{NodeID: n.registration.NodeID, EnvironmentFingerprint: n.registration.EnvironmentFingerprint, SessionID: n.registration.SessionID}
		}
		lease, err := n.exchange(ctx, route, payload)
		delay := n.cfg.HeartbeatInterval.Std()
		if errors.Is(err, ErrIdentityConflict) {
			n.logger.Error("judge.node.identity.conflict", "nodeId", n.registration.NodeID)
			return
		}
		if err != nil {
			n.logger.Warn("judge.node.control.failed", "nodeId", n.registration.NodeID, "operation", route)
			registered = false
			delay = backoff
			backoff = min(backoff*retryMultiplier, maxRetryDelay)
		} else {
			if !registered {
				n.logger.Info("judge.node.registered", "nodeId", lease.NodeID, "environmentId", lease.EnvironmentID)
			}
			registered = true
			backoff = initialRetryDelay
			delay = min(delay, time.Duration(lease.LeaseDurationNs)/leaseRenewalDivisor)
		}
		timer := time.NewTimer(delay)
		select {
		case <-ctx.Done():
			timer.Stop()
			return
		case <-timer.C:
		}
	}
}
func (n *Registry) exchange(ctx context.Context, route string, payload any) (contract.NodeLease, error) {
	var lease contract.NodeLease
	b, err := json.Marshal(payload)
	if err != nil {
		return lease, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, strings.TrimRight(n.cfg.ControlPlaneURL, "/")+"/internal/judge-nodes/v1/"+route, bytes.NewReader(b))
	if err != nil {
		return lease, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+n.cfg.ControlToken)
	response, err := n.client.Do(req)
	if err != nil {
		return lease, err
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusConflict {
		return lease, ErrIdentityConflict
	}
	if response.StatusCode != 200 {
		return lease, fmt.Errorf("node control status %d", response.StatusCode)
	}
	if err := wire.Decode(io.LimitReader(response.Body, maxLeaseResponseBytes+1), &lease); err != nil {
		return lease, err
	}
	if lease.NodeID != n.registration.NodeID || !uuidPattern.MatchString(lease.EnvironmentID) || lease.LeaseDurationNs < minLeaseDuration.Nanoseconds() || lease.LeaseDurationNs > maxLeaseDuration.Nanoseconds() {
		return lease, fmt.Errorf("invalid node lease")
	}
	return lease, nil
}
