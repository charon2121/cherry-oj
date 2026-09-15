package node

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"cherry-oj/judge-engine/internal/contract"
)

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
func (n *Node) Run(ctx context.Context) {
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
		if errors.Is(err, errIdentityConflict) {
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
func (n *Node) exchange(ctx context.Context, route string, payload any) (contract.NodeLease, error) {
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
		return lease, errIdentityConflict
	}
	if response.StatusCode != 200 {
		return lease, fmt.Errorf("node control status %d", response.StatusCode)
	}
	if err := decodeJSON(io.LimitReader(response.Body, maxLeaseResponseBytes+1), &lease); err != nil {
		return lease, err
	}
	if lease.NodeID != n.registration.NodeID || !uuidPattern.MatchString(lease.EnvironmentID) || lease.LeaseDurationNs < minLeaseDuration.Nanoseconds() || lease.LeaseDurationNs > maxLeaseDuration.Nanoseconds() {
		return lease, fmt.Errorf("invalid node lease")
	}
	return lease, nil
}
