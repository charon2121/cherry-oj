---
id: "TASK-121"
type: "task"
title: "固定沙箱软件包来源与长期留存"
status: "done"
work: "WORK-056"
owners: ["team/judge-engine"]
depends_on: ["IMPROVEMENT-006", "DESIGN-050", "DECISION-034", "PLAN-040"]
related: []
implements: ["IMPROVEMENT-006#REQ-001"]
verifies: []
tags: []
read_paths: ["deploy/sandbox-linux", "development/works/WORK-050", "development/works/WORK-055", "development/works/WORK-056", ".github/workflows"]
write_paths: ["deploy/sandbox-linux/ci", "development/works/WORK-056"]
forbidden_paths: ["apps", "contracts", "deploy/sandbox-linux/rootfs", ".github/workflows"]
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# TASK-121：固定沙箱软件包来源与长期留存

## 任务目标

恢复 CI 的固定官方快照获取，并证明原锁 56 个包与 rootfs 内容不变。

## 依据

IMPROVEMENT-006#REQ-001、DESIGN-050、PLAN-040。

## 可查看范围

以 front matter 为准；包括上游失败证据及原下载器接口。

## 可修改范围

仅 CI 获取描述、packages.py、相关测试/说明/报告与本工作记录。

## 禁止修改

禁止应用代码、契约、包锁、rootfs 默认下载/构建逻辑、工作流发布动作及用户服务器。

## 依赖

意图闸和后续实施授权；上游依赖以 front matter 为准。

## 产出

固定快照描述与显式获取接口、相关负向测试、Linux 全量恢复证据。

## 完成标准

- [x] 56/56 包通过原锁校验，原锁摘要不变，rootfs 清单匹配基线。
- [x] 不可用来源、篡改、取消和超限不产生成功目录，现有文件安全测试通过。
- [x] 记录 Linux 获取耗时与身份，未改变默认部署路径或既有预算。

## 验证

执行 rootfs 与 CI 测试套件，空目录 Linux 快照恢复并构建 rootfs；验证日志与失败清理。远端 CI 运行需相应提交推送授权。

## 风险

三包成功不保证其余包可用；缺失时停止，不改版本或静默回退。

## 执行记录

- 2026-09-12：仅完成方案拆分，尚未实施。
- 2026-09-12：状态变更：todo → ready。原因：意图闸已通过且用户授权实施，上游与边界就绪
- 2026-09-12：状态变更：ready → doing。原因：开始固定快照来源实现和验证

- 2026-09-12：意图闸实为 passed，依用户本轮授权实施。新增 acquisition.json，冻结快照时间、索引和原锁摘要；packages.py 仅在 CI 获取路径映射到快照，缓存键升为 v2，记录获取描述摘要。
- 2026-09-12：本地 basic.py 全部通过：install 15 + rootfs 27 + ci 94 = 136 项；Python AST 71 文件、shell 4 文件通过。原包锁与默认部署代码未改。
- 2026-09-12：本地空目录实取 56/56 包，77,897,048 bytes，64.417629 秒，58 个请求均首次 HTTP 200；额外逐包重新计算摘要通过，临时获取目录已回收。证据 `/private/tmp/cherry-work056-snapshot/{download.log,evidence.json}`。
- 2026-09-12：Linux 获取与 rootfs 清单验证尚未执行，TASK 保持 doing；本机缺 dpkg-deb，不把本地下载结果当 Linux 运行通过。提交推送及独立子智能体复核需取得本批授权后继续。

- 2026-09-12：用户明确授权本批只读独立子智能体复核、修正后提交推送 origin/main 并运行 Linux CI；已启动 work056_review。

- 2026-09-12：f791b85 推送后 Linux 独立冷检查通过，56包恢复且rootfs基线一致；独立证据复核通过，完成本任务，完整主CI结果另行跟踪。详见VERIFY-057。
- 2026-09-12：状态变更：doing → done。原因：独立复核、本地136测试、Linux全56包与原rootfs基线验证通过；主CI业务结果另记
