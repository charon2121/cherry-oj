# judge-engine

两个 Go 服务加一个 C 执行器，两条硬边界。为什么这样切见 [docs/engine.md](../../docs/engine.md)；
每个包的职责写在各自的 `doc.go` 第一句。

## 一次判题经过的进程

```
judging-service ──HTTP /judge──▶ judge ──HTTP /blobs、/run──▶ sandbox
                                (非特权)                      (非特权)
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
| [cmd/](cmd) | 两个 `main`，只调用各自服务包的 `Run` |
| [judge/](judge) | 编排一次判题：上传源码、编译、逐点运行、比对、汇总 Verdict；入口 [flow](judge/flow/flow.go) |
| [sandbox/](sandbox) | 对 judge 提供沙箱执行：blob、排队、限额归一化、把执行事实归类成 Status |
| · [backend](sandbox/backend/doc.go) | 单次执行的可替换边界：[executor.go](sandbox/backend/executor.go) 调用 C 执行器，devhost 不隔离、只用于开发 |
| [layout_test.go](layout_test.go) | 两棵子树之间的引用边界：每个服务二进制只能链接自己的子树和 internal/ |
| [internal/](internal) | 模块内共享：[contract](internal/contract)（judge↔sandbox 的 DTO）与平台设施 |

## 从哪里开始读

- 一条命令怎么执行：sandbox 的 [api/run.go](sandbox/api/run.go) → [runner](sandbox/runner/runner.go)
  → [backend/executor.go](sandbox/backend/executor.go) → 执行器的 [main.c](../sandbox/src/main.c)。
- 配置：[judge.example.yaml](judge.example.yaml)、[sandbox.example.yaml](sandbox.example.yaml)；
  跨层期限的顺序断言在 [sandbox/budget.go](sandbox/budget.go)。
- 执行器的真实内核测试：[apps/sandbox/tests](../sandbox/tests/run_tests.py)，需要 root 与一次性的 Linux 机器。
