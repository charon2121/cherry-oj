---
id: "TASK-108"
type: "task"
title: "补齐首站开机启动与整机重启恢复"
status: "cancelled"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-099", "TASK-100"]
related: []
implements: ["IMPROVEMENT-004#AC-004", "IMPROVEMENT-004#REQ-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "development/works/WORK-048", "docs/engineering", "deploy/sandbox-linux", "apps/judge-engine/internal/judge/node", "apps/server/judging-service/src/main/java", "apps/server/scripts/judge-environment", "apps/server/judging-service/src/main/resources", "apps/server/judging-service/target/judging-service-0.0.1-SNAPSHOT.jar", "apps/web/vite.config.ts", "apps/web/package.json", "apps/web/dist", "apps/server/gateway-service/src/main/resources", "AGETNTS.local.md"]
write_paths: ["development/works/WORK-048", "deploy/sandbox-linux/install", "deploy/sandbox-linux/tests/acceptance", "deploy/sandbox-linux/tunnel", "deploy/sandbox-linux/.local"]
forbidden_paths: ["apps/server", "apps/web", "apps/judge-engine", "contracts", "deploy/backend", "compose.yaml"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-108：补齐首站开机启动与整机重启恢复

## 当前处置：留置后续

2026-09-10 用户明确暂不实施重启恢复。本任务仅形成方案和只读记录，没有实现产出；退出 WORK-048 本轮实施及验收范围，使用工具的 cancelled 状态表示取消本轮执行，不标 done。以下方案、未完成勾选和探测事实完整保留。以后用户要求恢复时，以本方案创建新的承接 TASK，重新核对服务器、环境身份和实施边界；不自动恢复或安排重启。

## 任务目标

在当前专用 Linux 测试服务器完成一次正常整机重启：三个原生服务自动启动，本地 SSH 隧道自动重连；通过现有管理入口重新部署并核验数据后，恢复真实自定义运行和正式提交。用户已选择受控恢复，数据恢复有人工运维步骤，不宣称完全无人值守。

## 依据

IMPROVEMENT-004 的 AC-004 / REQ-004，以及 DESIGN-042「TASK-108 首站整机重启受控恢复」和 PLAN-032 对应执行顺序。TASK-099/100 已完成的服务恢复与业务证据保留，本任务补整机启动事实。

## 可查看范围

以 front matter 的 `read_paths` 为准。

配置、构建 JAR 和前端产物只用于复用既有只读证据脚本及管理页面；不输出凭据，不改变本机 IDEA 启动方式。增加这些只读路径是为了查验当前会话、数据回执和校准，而非扩展业务实现范围。

## 可修改范围

以 front matter 的 `write_paths` 为准。

仅改安装管理脚本及其测试、项目内隧道管理脚本、重启验收驱动和 WORK 证据。远端实施对象限定为已安装的 cherry-sandbox 三个服务、所属 slice 和安装回执所列资源；开机启用只新增 judge 的 multi-user.target.wants 链接，依赖链启动其余服务。更新 operations 脚本须核对旧摘要、保留备份与修订审计；禁止覆盖未知文件。私有连接参数、PID、锁、日志与证据只放 deploy/sandbox-linux/.local。

真实业务数据操作仅限当前 A+B 问题已发布 v4 的既有数据重新部署、有限自定义运行及正式提交；使用正常管理入口，不直接写库。实际重启仅一次正常系统重启，执行前再次核对排空及本任务对象。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

不修改 systemd 单元内容、Go/Java/Web、公开契约、工具链或执行策略；不升级内核、不关闭安全策略、不重启本机 IDEA/原前端，不切换 ACTIVE 环境，不删除原测试 ZIP、已有数据或云厂商代理。需要改变这些范围时先修订方案和任务边界。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

安装器的开机启用/撤销/状态与审计能力；项目内 SSH 隧道监督脚本及运维说明；有期限的重启恢复验收驱动；重启前后身份、会话、服务、数据和真实判题证据。

## 完成标准

- [ ] 开机设置可核对实际状态并按原状态回退；未知链接、文件或 drop-in 拒绝处理。
- [ ] 隧道断线重连、重复启动互斥、停止和错误处理可验证，日志有界，不能误停其他 SSH 连接。
- [ ] 一次真实整机重启后 boot ID 改变，judge 及依赖自动启动；SSH 在规定期限内恢复，资源和权限检查通过。
- [ ] 新节点会话注册成功，核验实际环境指纹及既有校准；旧会话数据回执不被冒用，现有管理入口重新部署后形成当前会话的有效回执。
- [ ] 自定义 A+B、CPU 超限和正式 AC 均符合预期；任务 UID 进程、执行 cgroup、工作区与挂载无残留。
- [ ] VERIFY-049 记录停机时长、每阶段耗时、数据运维步骤和失败/回退事实，支持范围仅限当前机器的正常重启。

## 验证

先本地验证启用管理的所有权/失败回退，以及 SSH 监督的掉线重连、停止和有界日志。远端重用已安装 verify-native.py，以既有 MemoryMax=128M、MemorySwapMax=0、TasksMax=32、CPUQuota=50%、RuntimeMaxSec=90s 的独占临时单元验证原生链。

重启前记录实际 boot ID、服务与隧道状态、回执摘要、环境指纹、会话、数据及校准，检查无排队/在途请求、无执行子组及任务进程，核对下次启动内核和安全代理。确认排空后正常停止本项目服务以阻止新接单，再发出一次正常重启。SSH 恢复探测单次连接不超过 10 秒，退避且总等待最多 5 分钟；服务及注册阶段另设明确期限，超时即报告失败，不无限重启或重试。

重启后区分 SSH 恢复、服务健康、节点注册、数据就绪、实际判题五个阶段。数据未就绪前不能报告业务恢复。使用原已发布版本和校准，不重新发布版本来掩盖会话恢复问题。

## 风险

服务器会短暂停机，期间当前 ACTIVE Linux 节点不可判题。本机 Mac、IDEA 后端及隧道监督进程需持续运行；本任务不覆盖本机重启、睡眠或断网后的无人值守恢复，也不证明强制断电/内核崩溃恢复。

如果实际内核或文件摘要导致环境指纹变化，停止原身份恢复；后续按新节点身份、正常注册与重新校准处理，不伪造旧指纹或复制校准。服务启动失败保持拒绝接单，不能降级到 host。SSH 超过期限仍不可达则保留证据，请用户检查云控制台，不执行强制重置。

重启前可撤销本次启用链接并恢复原隧道管理方式；重启动作本身不可撤销。重启后仅恢复已验证的原配置，保留数据与系统代理；不因回退而切换环境。

## 执行记录

- 2026-09-10：用户要求补齐当前服务器重启恢复，并选择服务/隧道自动恢复、数据通过现有管理入口受控恢复。当前回合仅方案与只读检查；待用户审核本次具体材料后实施，已有意图闸 passed 保留，不代签验收闸。
- 2026-09-10：只读确认三个服务 active / disabled / Restart=no，执行 cgroup 为 0，工作区和 Store 仅 .lock。本轮未修改远端、未重启。WORK 因新增未完成实施任务由控制面回到 todo，不回退 TASK-093～103 的 done 事实。
- 2026-09-10：416 份开发文档校验及 git diff --check 通过，仅既有 WORK-033 提示。refresh 尝试将混有已完成任务的 WORK 推进 doing，被 TASK-108 尚未 ready 的阶段约束拒绝，保持 todo，待本次材料获准后再推进。DESIGN/PLAN 保留原工具生成的 checked 状态；本次补充的实施授权仍待用户审核，不将结构状态视为授权。
- 2026-09-10：状态变更：todo → cancelled。原因：用户明确重启恢复暂不实施，退出本轮验收；仅有方案与只读记录，完整留置材料供后续新任务承接，不标记完成
