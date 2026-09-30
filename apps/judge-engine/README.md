# judge-engine

一个 Go 服务（judge，判题编排加进程内执行层）和一个 C 执行器，两条硬边界。为什么这样切见
[docs/engine.md](../../docs/engine.md)；每个包的职责写在各自的 `doc.go` 第一句。

## 一次判题经过的进程

```
judging-service ──HTTP /judge──▶ judge（非特权）
                                   判题编排 flow ──Upload/Run/Delete──▶ 执行层 execution
                                                                         │ 写 box 目录，exec，读一行 JSON
                              ────────── 信任边界：以下以 root 运行 ──────────
                                                                         ▼
                                                   sandbox 执行器（setuid-root，每次执行一个进程）
                                                                         │ clone3，6 个 namespace
                              ────────── 隔离边界：以下在新 namespace 内 ──────
                                                                         ▼
                                                   init（PID 1）→ fork → 用户程序
```

执行器是独立的 C 程序，源码与 box 约定在 [apps/sandbox](../sandbox/README.md)。本模块里没有特权代码。

## 目录

| 目录 | 一句话 |
|---|---|
| [cmd/](cmd) | 唯一的 `main`，只调用 `judge.Run` |
| [judge/](judge) | 编排一次判题：上传源码、编译、逐点运行、比对、汇总 Verdict；入口 [flow](judge/flow/flow.go)；[judge.go](judge/judge.go) 装配执行层与 HTTP |
| [execution/](execution) | 进程内执行层：blob、排队、限额归一化、把执行事实归类成 Status；入口 [engine.go](execution/engine.go) |
| · [backend](execution/backend/doc.go) | 单次执行的可替换边界：[executor.go](execution/backend/executor.go) 调用 C 执行器，devhost 不隔离、只用于开发 |
| [layout_test.go](layout_test.go) | 判题编排与执行层之间的引用边界 |
| [internal/](internal) | 模块内共享：[contract](internal/contract)（请求、结果与限额的 Go 类型）与平台设施 |

## 从哪里开始读

- 一次判题怎么走：[judge/flow](judge/flow/flow.go) → 执行层的 [Engine.Run](execution/engine.go)
  → [runner](execution/runner/runner.go) → [backend/executor.go](execution/backend/executor.go)
  → 执行器的 [main.c](../sandbox/src/main.c)。
- 配置：[judge.example.yaml](judge.example.yaml)（judge 与 execution 两段）。
- 执行器的真实内核测试：[apps/sandbox/tests](../sandbox/tests/run_tests.py)，需要 root 与一次性的 Linux 机器。
