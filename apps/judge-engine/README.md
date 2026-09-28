# judge-engine

三个二进制、两条硬边界。为什么这样切见 [docs/engine.md](../../docs/engine.md)；
每个包的职责和它跑在哪个进程里，写在各自的 `doc.go` 第一句。

## 一次判题经过的进程

```
judging-service ──HTTP /judge──▶ judge ──HTTP /blobs、/run──▶ sandbox
                                (非特权)                      (非特权)
                                                                 │ Unix socket，本机执行协议
                              ────────── 信任边界：以下以 root 运行 ──────────
                                                                 ▼
                                                   P3  isolator daemon（常驻）
                                                                 │ clone，FD 3/4/5
                              ────────── 隔离边界：以下在新 namespace 内 ──────
                                                                 ▼
                                                   P4  init（每次执行新建）
                                                                 │ exec，READY / GO
                                                                 ▼
                                                   P5  exec → execve 用户程序
```

## 目录

| 目录 | 一句话 |
|---|---|
| [cmd/](cmd) | 三个 `main`；[cmd/isolator](cmd/isolator/main.go) 在最前面按参数分流 P3/P4/P5 |
| [judge/](judge) | 编排一次判题：上传源码、编译、逐点运行、比对、汇总 Verdict；入口 [flow](judge/flow/flow.go) |
| [sandbox/](sandbox) | 对 judge 提供沙箱执行：blob、排队、限额归一化、把执行事实归类成 Status |
| [isolator/](isolator/doc.go) | 特权隔离执行，按进程角色分包 ↓ |
| · [daemon](isolator/daemon/doc.go) | P3：socket、认证、槽位、启动自检与恢复 |
| · [execution](isolator/execution/doc.go) | P3 内的一次执行：cgroup、启动 P4、监督、回收、结论 |
| · [startup](isolator/startup/doc.go) | P3↔P4↔P5 的 FD 约定与握手，**完整时序图在这里** |
| · [initproc](isolator/initproc/doc.go) | P4：rootfs、写入输入、启动并放行 P5、上报退出 |
| · [execstage](isolator/execstage/doc.go) | P5：rlimit、降权、seccomp、execve |
| · privilege / seccomp / cgroup | 降权步骤 / 过滤策略 / 资源组与计量 |
| [layout_test.go](layout_test.go) | 三棵子树之间的引用边界：每个服务二进制只能链接自己的子树和 internal/ |
| [internal/](internal) | 模块内共享：[contract](internal/contract)（judge↔sandbox）、[hostexec](internal/hostexec)（sandbox↔isolator） |

## 从哪里开始读

- 一条命令怎么执行：sandbox 的 [api/run.go](sandbox/api/run.go) → [runner](sandbox/runner/runner.go)
  → [backend/isolated.go](sandbox/backend/isolated.go) → [hostexec/client](internal/hostexec/client/client.go)
  → isolator 的 [daemon/server.go](isolator/daemon/server.go) → [execution](isolator/execution/doc.go)。
- 三个进程怎么握手：[startup/doc.go](isolator/startup/doc.go)，对照 [initproc/payload.go](isolator/initproc/payload.go)
  和 [execstage/exec.go](isolator/execstage/exec.go)。
- 配置：[judge.example.yaml](judge.example.yaml)、[sandbox.example.yaml](sandbox.example.yaml)、
  [isolator.example.json](isolator.example.json)；跨层期限的顺序断言在 [sandbox/budget.go](sandbox/budget.go)。
- 在 macOS 上跑 Linux 测试（isolator 只在 linux/amd64 编译）：[scripts/test-linux.sh](../../scripts/test-linux.sh)，
  脚本开头写了容器里覆盖不到的部分。
