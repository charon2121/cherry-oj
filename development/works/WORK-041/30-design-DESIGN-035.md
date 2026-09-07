---
id: "DESIGN-035"
type: "design"
title: "建立题目阅读与 Monaco 编码工作台"
status: "checked"
work: "WORK-041"
owners: ["codex/root"]
depends_on: ["FEATURE-010", "EXPERIENCE-018"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# DESIGN-035：建立题目阅读与 Monaco 编码工作台

## 背景

FEATURE-010 定义范围，EXPERIENCE-018 定义可见行为。只读核查发现 ProblemDetailPage 右侧无条件渲染
`/login` 链接，没有消费会话查询；不是该页面检测出认证失败。submission-service 目前只有启动与身份
校验骨架，Gateway/OpenAPI 没有用户自测、提交、历史或草稿 API。用户已明确选择前端切片。

## 目标与限制

仅修改 Web 和对应说明；复用 GET /api/problems/{slug}、GET /api/auth/session 以及现有登录/改密链路。
保持 C++ ACM、管理员发布与校准模型、后端鉴权和现有接口；不新增跨服务契约、迁移或 Docker 配置。

## 整体方案

扩展现有 WorkbenchPageTemplate，增加 opt-in 的答题布局变体，让题面与代码区获得平衡宽度、有限视口
高度和独立滚动；默认变体保持后台原布局。SiteAppShell 仅在该详情路由采用适合工作区的高度/页脚策略，
不能扩大影响到题库列表、登录页或后台。模板独占页面语义标题和首内容间距；页面只组合已有 UI。

Monaco 通过 npm 的 ESM 入口本地打包，在本地 UI 薄适配中配置 editor worker 与 C++ 语言贡献，异步加载。
使用稳定版并锁入 package-lock；不恢复后台 Monaco，也不引入完整 VS Code、LSP 或语言服务器。
组件持有 editor/model 的生命周期、ResizeObserver 与事件清理，业务层持有文本与草稿键；切主题和布局
不重建文本模型，不让受控值回写破坏撤销栈。Monaco 需要的数值样式/颜色由本地适配读取语义 token，
业务 JSX 不写 inline style 或裸颜色；编辑器内核生成的 DOM 样式不视作业务手写样式。

手机触控端回退到已有 TextEditor；窄屏桌面可以继续 Monaco。使用单一文本状态协调两种视图，避免响应式
切换造成双写或游标以外的文本丢失。第一版不增加新的拖拽基础组件。

## 模块与数据

- features/problems：详情工作台、会话分支、题面与样例、草稿 hook/存储适配。
- components/ui：Monaco 薄适配与 Workbench 模板变体；不直接发业务请求。
- features/auth/api/session-query.ts：复用现有 query options，禁止根据 localStorage 中的用户名判断登录。
- 现有 problemDetailSchema 已包含 problemVersionId、allowedLanguages[].starterCode，无需改 API。公开详情
  没有时间/内存限制字段，本轮不编造限制值，也不为显示它们扩大后端范围。

草稿存储键采用版本化命名空间加 userId/problemId/problemVersionId/languageId；记录 schemaVersion、
source、updatedAt、revision。初次读取在首个保存之前完成，区分无记录、有效空文本、损坏格式与超限。
单份源码以 UTF-8 256 KiB 为本轮本地保护上限；不截断用户输入，超过上限保留内存并提示无法自动保存。
正常输入约 500ms 防抖保存；身份、题目变化前和 pagehide 同步 flush 到原键，成功后才标记已保存。
存储失败、损坏记录、加载失败都不能用空文本覆盖已有记录；提供复制和显式重置恢复。

账号变化时先停止旧订阅与旧键写入，再隐藏/释放旧模型；访客不创建用户草稿。登出保留对应账号存储，
页面在重新认证前不读取展示旧源码；本地存储无法抵御同设备开发者工具访问，界面不宣称加密。
跨标签页不用单值 localStorage 的“读 revision 再写”冒充原子比较更新。每个页面实例以唯一 writerId 保存
独立恢复副本，并记录它基于哪个 revision；各页面只写自己的副本，包含 pagehide flush。最新记录索引
只作为读取提示，不承担唯一副本职责。发现同一基线出现多个写入分支时展示恢复选择，保留每份代码，
显式选择/覆盖后才允许清理对应冲突副本。storage 事件仅用于通知，不能成为保证不丢数据的唯一手段。

## 接口与状态

Query 负责题目与会话；独立管理 pending/error/authenticated/anonymous/password-change-required，
网络错误不是 anonymous。共用导航上的会话缓存，登录/登出后正确失效。会话未确认时不载入用户草稿。
已编辑后网络断开保留缓冲区并暂停需要确认身份的动作，重新获得明确身份后按原键恢复。

本机草稿状态为 loading/clean/dirty/saving/saved/error/conflict；运行与提交为 capability-unavailable，
与登录状态相互独立。禁用按钮仍提供可读理由，不安装执行快捷键，不向内部 Judge 或 sandbox 请求。

## 安全与失败

题面继续 SafeMarkdown；源码不进入 URL、请求日志、分析事件或第三方服务。样例复制处理权限拒绝。
本地持久化仅作用于当前用户命名空间，明确未保存状态和离开保护；跨题、换账号的异步加载必须核对键，
避免旧回调写入新题。reset 操作确认后只更新当前草稿，不清空全站 localStorage。

## 监控与部署

只交付 Web 静态资源，不重启 Java 或 Docker。记录工作台 chunk 与 worker 的 raw/gzip 大小、加载请求域、
切换题目后 model 是否释放；题库和后台不应新增 Monaco 请求。沿用公开 requestId 诊断接口失败。

## 迁移与兼容

本轮无服务端数据迁移。新增本机命名空间，不触碰既有主题或后台工作台存储。模板改动 opt-in；后台仍
使用 CodeMirror。全局说明只在实施验证后同步本轮已成立的事实，不把 WORK-002 的规划改写为已完成。

## 备选方案

继续固定登录链接无法提供编辑；只用 CodeMirror 与用户明确指定 Monaco 不符。选择桌面 Monaco、手机
已有编辑器回退，在体验与已知支持范围之间明确边界。完整提交后端需要另行确认 WORK-002 的问题，
不作为本次页面交付的隐藏依赖。

## 风险与重审条件

主要风险是大包/worker 配置、模型泄漏、草稿跨身份竞态、触屏和键盘体验；通过真实浏览器与故障注入验证。
若后续加入 LSP、自定义运行、正式提交、云端草稿或 CORE，先升级相应定义和任务，不能沿本任务越界。

## 参考依据

- [LeetCode Two Sum](https://leetcode.com/problems/two-sum/)：2026-09-07 核对题面、代码、测试用例和结果区域组织；本轮只采用题面与编码组织。
- [Monaco 官方 README](https://github.com/microsoft/monaco-editor)：模型拥有 URI，需释放 editor/model；官方不支持移动浏览器。
- [Monaco ESM 集成说明](https://github.com/microsoft/monaco-editor/blob/main/docs/integrate-esm.md)：采用本地 ESM 与 Vite worker 接入；实施时依锁定版本复核入口。
- 仓库依据：docs/design-system/PROMPT.md、docs/design-system.md §6.1/§7、docs/engineering/typescript.md。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：已核实公开题目和会话接口，完成本地编辑器、草稿、兼容与回退方案
- 2026-09-07：结构与内容校验通过，由工具置为 checked。

## 实施补充

- Monaco 锁定 0.56.0，采用该版实际 ESM exports；官方 C++ grammar 静态注册在异步 runtime 内，
  避免二次 lazy loader 的失败缓存。原生模块网络失败可能需要完整刷新，错误态提供“刷新页面重试”，
  由草稿 beforeunload flush/离开保护保住未落盘内容，仍可复制/下载备份。
- 短视口（高度不超过36rem）固定最小代码阅读空间，并让代码面板滚动；200%等效视口验证结果见VERIFY。
- 窄屏 Tabs 保留 shadcn base-nova 官方 Base UI 骨架（registry返回200），用本仓库语义类替换样式。
