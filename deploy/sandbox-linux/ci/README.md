# 沙箱回归 CI

本目录把 WORK-048 的已验收断言组织为 WORK-050 的重构回归。`cases.json` 是必需用例清单，
记录稳定 ID、原断言来源与执行组；人工复核、重启和其他平台在 exclusions 中单列，不能计为通过。

普通开发机可运行（输出目录必须不存在）：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /tmp/cherry-ci-basic
```

基础套件覆盖安装器、rootfs 构建器及 CI 自测、Python AST 和 shell 语法。真实 Linux 套件只面向
一次性、独占的 GitHub Ubuntu VM；普通 Go 测试通过或交叉编译都不能替代它。当前套件实施与
真实 Actions 验证进度以 WORK-050/VERIFY-051 为准。

`report.py` 定义内部报告 schemaVersion=1：顶层严格包含 suite、sourceSha、harnessSha、runId、
startedAt、finishedAt、environment、cases 和 cleanup。每个 case 只含 id/status/evidence/details；
status 为 PASS、FAIL、ENVIRONMENT_ERROR、CANCELLED 或 NOT_RUN，默认 NOT_RUN。
sourceSha 必须匹配本次 checkout，harnessSha 覆盖非忽略的部署/测试脚本内容，case ID 必须与清单
完全相符。首次结果不可覆盖，必需用例全部 PASS 且 cleanup PASS 才能成功。

报告 JSON 不超过 1 MiB，单套件证据总计不超过 20 MiB；日志写入器另在运行时限制输出。
证据必须是非空普通文件，禁止链接、路径逃逸和缺失文件。汇总必须同时检查 job 状态及报告，
取消、前置失败、没有报告均不能成为绿灯。报告只保存测试事实，不保存凭据、Cookie 或私钥。

新增场景需要同时增加清单、执行断言及报告映射。更改测试含义须先更新 WORK 的设计依据；
不能通过删 case、放宽阈值、host 回退或重试整个场景掩盖第一次失败。

## Linux 内核套件

工作流中的 `sandbox-kernel` 先由普通 runner 用户运行 `prepare.py`，构建本提交的二进制、
Go 边界测试和锁定的 56 包 rootfs。它执行 Linux 专属包的 race 测试并拒绝任何跳过，下载器逐包
校验 SHA256；准备日志独立保存。当前使用冷包目录，尚未启用 rootfs 缓存。

随后 `kernel.py` 在一次性 VM 内通过 sudo 编排已有测试，检查实际 systemd/内核/LSM、控制器与
资源冲突。运行入口要求 GitHub-hosted 的环境与运行 ID、Linux/amd64 和 root；不可指向 SSH
服务器。缺能力直接失败。所有脚本的 CPU、内存、线程和时间预算沿用既有测试。

每批单元包含 GitHub run ID 与 attempt。`.ci-owner.json` 以 root 私有权限登记单元后才启动；
`finally` 和工作流 `always()` 都调用同一所有权清理逻辑。只停止已登记单元，检查全部存活进程的
身份、挂载及执行 cgroup，确认无残留后才删除本轮夹具。遇到未知占用或残留时保存事实并失败。
运行时长及 VM 销毁提供最后托底，但不能补造清理通过记录。

`results.py` 要求 Linux 专属 Go 测试、四组实际边界测试及 Python 逐模式标记出现；边界夹具的
`TestExecFailureChild` 在非子进程模式正常返回，不设置跳过例外。旧手动脚本默认参数保持有效，
CI 仅为 `chain_batch.py` 增加可选独立单元名，为零限额场景增加结果记录。
