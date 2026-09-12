---
id: "DESIGN-050"
type: "design"
title: "固定沙箱软件包来源与长期留存"
status: "checked"
work: "WORK-056"
owners: ["team/judge-engine"]
depends_on: ["IMPROVEMENT-006"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# DESIGN-050：固定沙箱软件包来源与长期留存

## 背景

落实 IMPROVEMENT-006。WORK-055 已完成传输与缓存分层，但原锁依赖的三个历史包在当前归档 URL 返回 404。保留其 VERIFY-056 失败事实，不把新来源说成原路径恢复。

## 目标与限制

冻结 `deploy/sandbox-linux/rootfs/ubuntu24-amd64-smoke.lock.json` 原始字节，SHA256 为 `4dbcf4dd7025146ff44782024354b1868eacaaa313d53a57ded5fab247fb129c`。不升级包、不改默认部署下载器、不放大既有 600 秒准备、120 秒 rootfs 构建及工作流期限；不缓存测试结果、标定或节点身份。

## 整体方案

1. CI 专用获取描述文件固定官方地址 `https://snapshot.ubuntu.com/ubuntu/20260910T000000Z/`，noble/noble-updates 与 amd64。索引也使用同一快照；从索引解析文件路径，按现有锁逐包校验。先验证 56 个包与 rootfs，再进入资产阶段。
2. 将同一包集打成确定性归档，附准确的文件清单、摘要、来源和许可材料。包资产与对应源码资产分开，日常只下载包资产。目标为本仓库 GitHub Immutable Release；发布前只读确认仓库支持和配置，准备草稿清单、固定 tag 命名及权限操作说明。人审核确切内容后才发布/启用不可变设置，不使用 latest 或可覆盖的同名文件。
3. 日常准备作业：缓存命中后重新验证，未命中时下载描述文件固定的 Release 资产，校验外层 SHA256 再校验 56 个包。按本次 run/attempt 产物交给三条消费者。冷工作流始终空目录直取官方快照，不读 Release 或缓存；该路线由工作流显式选择，无静默回退。

## 模块与数据

新增 CI 专用 acquisition 描述文件置于 `deploy/sandbox-linux/ci/`，至少包含 schema 版本、原锁摘要、快照时间、索引位置、包资产精确 tag/asset 标识、归档字节数及摘要。实施第一阶段仅快照字段有效；发布完成后另行填入实际资产身份，消费端不接受占位资产。描述文件进入缓存键和 harness 身份计算。

复用 packages.py 的文件安全、限额、取消与逐包校验；仅扩展来源选择与固定资产解包，避免再造通用下载框架。生成产物放本项目拥有的临时目录，不把 .deb 或源码归档直接提交 Git。rootfs 继续由当前 build.py 构建，基线 manifest 摘要为 `ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0`。

## 接口与状态

获取方式显式为 snapshot / release / cache，输出保持经过校验的平铺 .deb 目录。日志分别记录连接/传输错误、404、摘要失败与解包拒绝；失败不发布目录、不写缓存、不上传成功包产物。未发布固定资产前，不让消费者依赖不存在的资产。

## 安全与失败

快照仅 HTTPS 固定官方域名；Release 下载只接受固定 GitHub API/资产入口及经过审阅的 GitHub 资产重定向域名，不允许任意重定向或向跨域转发凭证。发布阶段使用最小 contents:write，普通 CI contents:read；PR 不发布资产。

保留包下载 100 MiB 单文件、256 包上限、有限重试及总期限；传输内容不执行。归档下载与解包按发布清单声明总字节数、文件数和逐文件大小设上限，拒绝多余文件、绝对路径、路径穿越、symlink、hardlink、特殊文件与重复条目。所有读取仍防 TOCTOU。损坏缓存明确失败，不偷偷替换。

发布前核对每个二进制包的版权文件和源码对应关系。需要对应源码的包提供准确版本的源包、上游源码、补丁和必要构建说明；记录摘要并同批保留，不能仅用易失效上游链接代替交付。若精确源码不可得、许可证义务无法满足或资产不可变能力不可用，停止发布并回报；快照阶段可以独立交付，不能宣称长期留存完成。

## 监控与部署

沿用独立冷工作流的路径触发、定期运行与手动入口，以及日志上传和所属临时文件回收。每次记录 source/harness/lock、来源和资产摘要。源码归档准备是一次性发布操作，先取得索引大小并形成有界字节/时间清单再执行；不把大源码下载塞进日常测试。用户服务器、systemd 和 IDEA 环境无操作。

## 迁移与兼容

先快照实现并实测，再准备和发布资产，最后启用资产消费。原锁 source 字段保留为历史来源，新获取事实单独记录。新缓存键避免复用旧输入身份。仍要求全新 workflow run；消费者单独 rerun 可能缺少同 attempt 产物，沿用 WORK-055 限制。

## 备选方案

只用快照改动最小，但依赖其留存期限和每次外部下载；只用缓存会被淘汰，不能长期留存；升级到当前包会改变执行环境身份。采用“官方快照恢复 + 固定资产保存 + 可丢弃缓存”分开解决来源、留存与速度。

## 风险与重审条件

Ubuntu 说明快照预期至少保留两年，不承诺永久；项目资产不得自动删除。安全更新、Ubuntu 停止提供对应版本、GitHub 资产能力或许可限制变化需重审。快照成功不证明历史慢连接根因已消失。

依据：[Ubuntu snapshot](https://snapshot.ubuntu.com/)、[Ubuntu 使用说明](https://ubuntu.com/server/docs/how-to/software/snapshot-service/)、[GitHub 缓存淘汰](https://docs.github.com/en/actions/reference/workflows-and-actions/dependency-caching)、[Release 资产](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases)、[不可变 Release](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases)。

本地只读证据 `/private/tmp/cherry-snapshot-research-x9lo4oto/results.json`：libc-dev-bin 20418 bytes / 1.125181s，libc6-dev 2125374 bytes / 2.821678s，libc6 3262434 bytes / 3.025974s，全部 HTTP 200 且匹配原锁。只代表这三个包，不代表完整 Linux CI 已通过。

## 变更记录

- 2026-09-12：状态变更：draft → review。原因：已明确来源、身份、安全、分发及失败边界
- 2026-09-12：结构与内容校验通过，由工具置为 checked。
