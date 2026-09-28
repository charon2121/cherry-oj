//go:build linux && amd64

// Package cgroup 在 P3 里管理已委派子树下的 cgroup v2 执行组：每次执行新建一组、写限额、
// 采样与最终计量、整组停止并删除。它不理解判题，也不关心组里跑的是什么。
//
// 限额写入后会读回比对；新建的组若已有进程或历史计量直接拒绝。停组用 cgroup.kill，
// 以 populated=0 为准确认清空后才读最终计量，计量因此覆盖整组所有后代。
package cgroup
