---
id: "TASK-099"
type: "task"
title: "交付 systemd 独立节点部署与环境身份"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-098"]
related: []
implements: ["IMPROVEMENT-004#REQ-004", "IMPROVEMENT-004#REQ-005", "IMPROVEMENT-004#AC-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "development/works/WORK-048", "apps/judge-engine", "contracts/judge-node.schema.json", "contracts/execution-profile.schema.json", "apps/server/scripts/judge-environment", "deploy/sandbox-linux", "apps/judge-engine/internal/judge/node", "apps/judge-engine/internal/config/node.go", "apps/judge-engine/cmd/judge", "AGETNTS.local.md", "apps/server/judging-service/src/main/resources/application.yaml", "apps/server/judging-service/src/main/resources/application-local.yaml", "compose.yaml"]
write_paths: ["development/works/WORK-048", "deploy/sandbox-linux", "apps/judge-engine/internal/judge/node", "apps/judge-engine/internal/config/node.go", "apps/judge-engine/cmd/judge", "compose.yaml"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "deploy/backend", "apps/judge-engine/internal/sandbox"]
created_at: "2026-09-09"
updated_at: "2026-09-10"
---

# TASK-099：交付 systemd 独立节点部署与环境身份

## 任务目标

在隔离测试通过后交付可复现 systemd 安装、停止、恢复与回退材料，并准备独立新节点。

## 依据

front matter 的 implements 条目、DESIGN-042 冻结结果与 PLAN-032。

## 可查看范围

以 read_paths 为准；写入范围也已列入可读范围。

提交前边界修订：用户要求完整提交推送时发现可信开发 Compose 缺显式后端配置，按 PLAN-032 的交付修正增加 compose.yaml 的只读/写入范围，仅补 trusted-host 环境变量及注释。实际运行只允许独立测试实例，不重建或重启用户现有容器；正式 Linux 默认和已验收节点保持不变。

## 可修改范围

以 write_paths 为准。部署固定 helper/sandbox/judge 单元、专用账号、委派树和版本化 rootfs/策略，使用项目独立 /etc/cherry-sandbox/、/var/lib/cherry-sandbox/、/run/cherry-sandbox/ 及 cherry-sandbox* 单元。节点身份纳入后端/内核架构/rootfs/策略/工具链摘要；使用现有配置入口。远端写入仅在本任务获得明确实施授权后进行，机器重启另需当次授权。

## 禁止修改

以 forbidden_paths 为准；不得修改现有可用判题节点、全局安全策略或无关服务。

## 依赖

以 depends_on 为准。当前为 TASK-093 新拆分的实施材料，保持 todo；用户审核并明确允许这些具体实施范围后，依次推进 ready，不从已签的只读探测授权推断编码或部署许可。

## 产出

安装/卸载/健康检查/恢复脚本、systemd 单元与配置示例，新节点身份与安装审计。

## 完成标准

- [x] HTTP 服务非特权，仅可信 Judge 可访问；helper socket 鉴权及节点总资源限制实际生效。
- [x] 配置/策略/rootfs/控制器缺失时启动失败，重启测试实例后无旧任务；机器重启恢复未运行时单独标待验。
- [x] 节点采用新环境身份，不复用旧标定；当前可用判题节点不被替换。
- [x] 安装与卸载仅作用已登记项目资源，不停止代理、不删除用户数据；回退为上一个已硬化版本或停止接单。

## 验证

先 systemd-analyze verify 与安装脚本检查，再独立实例启动/停止/崩溃恢复；查询注册指纹、读回总资源限额。验证命令与具体安装文件清单进入 VERIFY。

## 风险

TASK-093 未授权任何本任务安装动作；构建/安装依赖、账号/路径冲突先形成具体清单。新节点接入管理系统沿用审计入口，不改数据库绕过环境校验。

## 执行记录

- 2026-09-09：TASK-093 根据只读探针和现有源码拆分；尚未编码、测试或部署。

- 2026-09-09：用户已阅读纯 Go 材料并明确表示“没有问题，你可以开始编码”；本地代码和测试实现已授权，按依赖推进。远端安装、机器重启及现有节点切换仍按具体任务边界执行。
- 2026-09-10：状态变更：todo → ready。原因：TASK-098已完成，用户阅读部署与验收步骤后要求继续，本地部署包和节点身份实施范围已明确
- 2026-09-10：状态变更：ready → doing。原因：实现部署清单与systemd生命周期，修正Linux节点身份探针以适配精简rootfs
- 2026-09-10：状态变更：doing → done。原因：首站正式服务故障回收、卸载恢复与七项权限收敛实测通过，新身份注册且旧ACTIVE环境保持不变；机器重启明确未授权待验

## 本轮执行边界（2026-09-10）

用户阅读任务步骤后明确继续。TASK-098已done，开始本地部署包与节点身份实施；先生成可审核安装清单，再确认远端正式安装。配置和ProbeEnvironment修订依据见DESIGN-042，不改sandbox、server或公开契约。远端既有节点切换和机器重启不在本轮执行范围。

## 本地部署包进展（2026-09-10）

用户确认连接本机IDEA后端，通过SSH双向隧道联调。本地只读确认judging-service8084及既有Docker节点5051，新judge使用15051，远端控制面转发18084。本轮未启动隧道、注册或改变现有节点。

已实现Node.deploymentManifest入口、root保护/摘要/活动版本/24项实际cgroup限额核验、隔离g++版本探针；Linux缺清单或误接host拒绝注册。Node.New继续绑定judge自身二进制和执行策略；未改公开契约、sandbox或Java。旧Python容器探针只用于原开发模式。

部署包在deploy/sandbox-linux/install、systemd及build-release.sh，提供review渲染、初次安装/状态/启动/停止/保留数据卸载；候选清单见install/README.md。systemd只开放回环端口，两个服务分账号，helper候选capabilities和节点slice封顶待实机验证；脚本不自动启动/enable、安装包或重启。私有审核输出及二进制在deploy/sandbox-linux/.local，尚缺再次按锁解包的rootfs（先前测试目录已清理），安装前必须补齐并比较固定摘要。

只读远端确认systemd255、Linux6.8、约61980MiB可用磁盘，拟用账号/目录/端口未发现冲突；不是权限行为测试。全模块race/vet、Linux构建/vet、四项部署Python测试、AST和shell语法通过。正式systemd-analyze verify、实际安装/恢复/卸载、能力收敛和注册仍待执行，本任务保持doing。完整可审清单已经准备，下一步请用户确认正式安装此新独立节点；不代签闸。

收尾补齐完整产物：在本地从官方归档下载56个锁定包，哈希全部一致；已有Linux镜像内有界tmpfs重新组装rootfs，manifest与TASK-098完全一致。候选包包含二进制、rootfs tar、清单和脚本；不再缺rootfs。macOS bind mount直接构建会改变文件语义，该错误产物未用于候选安装。

正式安装的临时交付路径明确为/var/tmp/cherry-sandbox-work048-linux-v1（root700），用于完整候选包和单独0600的私有token；属于本项目独占安装准备资源，先校验再解包，不修改无关/tmp条目。取得具体安装确认后才写远端此目录、创建账号和单元、开启SSH隧道与注册。现有节点切换与机器重启仍未授权。

## 正式安装授权（2026-09-10）

用户在阅读完整清单后明确“安装”，授权按该清单安装、启动、建立SSH隧道并注册新独立节点。此次不重复询问相同授权，不重启机器或切换既有节点。为复用现有控制面token，新增精确只读路径：judging-service的application.yaml/application-local.yaml及被忽略的服务器记录；仅提取节点控制认证配置，不改Java配置、不输出凭据。生产修改边界不变。

## 原生安装实测（2026-09-10）

已创建清单内专用身份、版本化文件、三个服务与slice，启动并经SSH隧道注册cherry-linux-1。原judge-local-2保持ACTIVE，新环境REGISTERED、online_nodes=1，无旧标定复制或切换。首次自检揭示systemd255在seccomp准备时丢弃SETUID，按DESIGN补充单一ambient位并保留所有既有约束；首次注册前对单元/清单/回执修订留存摘要审计。未修改sandbox生产实现。

原生C++编译/执行、服务和任务全部线程权限、六种namespace、三处只读挂载、24项资源限额及执行后无任务残留通过。三项缺失测试（helper配置、rootfs清单、helper二进制）均拒绝服务，恢复原文件并重新注册同一环境ID。运维工具保存在/var/lib/cherry-sandbox/operations且摘要纳入回执；未开启开机启动、重启宿主或修改系统安全策略。详细输出见VERIFY-049。

本轮安装请求已完成；本任务仍doing，剩余正式单元的在途崩溃恢复/卸载实操及权限最小集合核查。回环HTTP以专用测试机的可信宿主为前提，不宣称应用层Judge鉴权；机器重启未授权、未验证，单独标待验。TASK-100仍todo，校准、部署题目数据和真实业务切换尚未执行。

## 补齐授权与执行计划（2026-09-10）

用户自行运行原生冒烟通过后明确“继续补齐”，授权本轮对新节点做正式服务在途崩溃、权限核查和卸载/恢复验证。仅新节点短暂离线；不切换ACTIVE环境、不改业务数据或重启机器。故障驱动先核验安装回执和空闲状态，通过/ judge trial编译短sleep任务（实际路径/请求遵循既有Go定义），观测非特权payload出现后，用pidfd核验exe/UID/cgroup再定点SIGKILL judge、sandbox或helper。每例有限墙钟，恢复后核对组/任务/工作区和同一环境身份。驱动位于deploy/sandbox-linux/install，外层128MiB/swap0/32tasks/CPU50%/90s；测试之间串行。

卸载先持久保存四个原单元及摘要；实际uninstall须只移除登记单元并保留账号/配置/发布/业务数据。新增显式restore命令，仅从root保护且摘要匹配的同次备份恢复原单元，拒绝目标冲突或账号改变，恢复本身不启动、不enable，之后显式start。恢复相同完整版本无需伪造新身份，发布/策略/资源清单不变；新版本升级仍必须新环境和重校准。设计先记录后在既有部署边界编码，本轮不改sandbox或judge生产实现。

权限对照允许的精确临时系统资源为/run/systemd/system/cherry-sandbox-helper.service.d/90-work048-capability-test.conf。仅当不存在任何drop-in且新节点已停止时创建，固定候选bounding和原Ambient SETUID；只启动helper/sandbox验证，不让judge在临时环境注册。finally核对本次文件内容后删除并rmdir本次新建空目录、daemon-reload恢复。若确认冗余权限则按DESIGN使用cherry-linux-2新身份，不能更改旧ACTIVE环境或伪造原指纹。

## 完成记录（2026-09-10）

已完成正式judge/sandbox/helper在途SIGKILL、缺文件拒绝及恢复、真实uninstall/restore/start、helper权限删减对照和最终七项配置复验。发现并修复部署health的旧socket就绪竞态；仅改部署工具，无sandbox生产越界。最终新身份cherry-linux-2在线REGISTERED，旧cherry-linux-1仅保留未激活历史，原judge-local-2仍ACTIVE。HTTP访问限定为已冻结的专用测试机可信宿主/回环+SSH，未引入应用层鉴权；helper peer凭据拒绝实测通过。

机器重启未授权、未执行，按完成标准单列待验，不据此声明开机恢复通过。其它平台未运行，不改变支持矩阵。证据及15项本地测试见VERIFY-049；TASK-100仍负责真实业务数据、校准和切换。
