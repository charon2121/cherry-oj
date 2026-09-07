---
id: "VERIFY-042"
type: "verify"
title: "建立题目阅读与 Monaco 编码工作台"
status: "approved"
work: "WORK-041"
owners: ["codex/root"]
depends_on: ["TASK-075"]
related: []
implements: []
verifies: ["TASK-075", "FEATURE-010#REQ-001", "FEATURE-010#REQ-002", "FEATURE-010#REQ-003", "FEATURE-010#REQ-004", "FEATURE-010#REQ-005", "FEATURE-010#REQ-006", "FEATURE-010#REQ-007", "FEATURE-010#REQ-008", "FEATURE-010#REQ-009", "FEATURE-010#REQ-010", "FEATURE-010#AC-001", "FEATURE-010#AC-002", "FEATURE-010#AC-003", "FEATURE-010#AC-004", "FEATURE-010#AC-005", "FEATURE-010#AC-006", "FEATURE-010#AC-007", "FEATURE-010#AC-008"]
tags: []
result: "pass"
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# VERIFY-042：建立题目阅读与 Monaco 编码工作台

## 验证对象

FEATURE-010 前端范围与 TASK-075。实现基于已签署的意图闸；这里记录技术证据，不代替人工验收。

## 对应要求

| 验收 | 对应要求 | 实际证据 |
|---|---|---|
| AC-001 | REQ-001、REQ-008 | 真实题目左右等宽、独立滚动；深浅主题截图；工作台切换与原题库回归 |
| AC-002 | REQ-003、REQ-006 | USER/ADMIN、访客、查询延迟/故障、强制改密、会话失效与账号切换 E2E；真实 admin 会话无固定登录提示 |
| AC-003 | REQ-002、REQ-004 | 真实 Monaco 高亮、查找、缩进命令、撤销重做、空代码刷新；主题变化不重建模型 |
| AC-004 | REQ-004、REQ-005 | 编辑保存刷新、不同账号/题目版本隔离、版本主动切换、重置取消/确认 E2E；草稿身份单测 |
| AC-005 | REQ-005、REQ-006 | 20 项草稿测试覆盖交错写入、第三分支竞态、损坏、超限、quota、pagehide与内存恢复；浏览器拒写离开保护、资源加载失败刷新恢复 |
| AC-006 | REQ-007 | disabled 运行/提交 + 可读原因；记录网络请求，点击和快捷键不产生执行或提交请求 |
| AC-007 | REQ-008、REQ-009 | 320px、长中文、Tab退出、手机CodeMirror、forced-colors/reduced-motion E2E；200%等效视口专项验证 |
| AC-008 | REQ-010 | 本站 worker 实际启动；题库与后台无 Monaco 请求；原题库/后台、登录、安全与主题回归 |

## 检查与结果

使用 Node 24.19.0（仓库要求 >=24 <25），没有用宿主默认 Node 26 代替交付检查。

- `npm run check`：通过，包含设计 token/296 对比组合、源码门禁与负向自测、生成 API 无漂移、格式、
  ESLint、TypeScript、39 个 Vitest 文件共 165 项测试。
- `npm run build`：通过，生成静态站与独立 Monaco/worker 资源。
- `npm run storybook:build`：通过，包括 CodeEditor 正常/只读状态；既有模板和后台编辑组件继续构建。
- Playwright：新增工作台 14 项、原 problems 3 项、smoke/design-system 27 项均有通过证据，共 44 项。
  初轮两项失败来自测试把 Mac 宿主快捷键用于 Windows UA，以及错误假定输入只产生一个 undo 分组；
  修正为按编辑器使用的平台判据发键、检查 undo 改变与 redo 恢复后，定向 2/2 通过。
- 200% 等效视口专项：CSS 640×360、DPR 2 模拟 1280×720 屏幕放大；初检发现编辑区仅 10px，
  已改为短视口最小编辑高度 + 可滚动代码面板；专项及全部 14 项工作台用例通过。
  实际滚轮可访问保存状态与底部按钮，截图同时检查编辑区和底部区域。
- `scripts/work check`：333 份开发文档通过；保留既有 WORK-033 状态推导提示，与本工作无关。
- `python3 scripts/docs_test.py`：402 份 Markdown 入口/链接通过；`git diff --check` 通过。

### 真实会话与外观

用户在本机 `http://localhost:5173` 登录后，用真实公开题目 `a-plus-b-ii` 验证：账号 admin 直接看到
可编辑 Monaco；添加验收注释、等到“已保存到本机”、刷新、通过页面复制核对源码完全恢复。
随后用“恢复起始代码”确认弹窗移除本次验收注释。没有调用判题 API，没有改变服务端题目或会话凭据。

Browser 实测包含黑/白、320px 题目与代码切换；窄屏文档宽度 320px，scrollWidth 320px，未横向溢出。
截图保存在本机 `/tmp/cherry-work041-visual/`：`desktop-auth-dark.png`、`desktop-auth-white.png`、
`narrow-white.png`，缩放等效截图由专项 E2E 保存。截图是本机验证附件，未上传外部服务。
200% 项采用等效 CSS 视口和像素密度模拟，不把它描述成操作系统原生浏览器缩放测试。

### 设计系统八问

1. **框数**：工作区没有外层卡片框。左右 pane 与工具条使用 hairline 区分独立滚动范围；样例用浅底
   区分可复制的输入/输出，按钮保留组件本身的描边，弹窗保留浮层边界。
2. **对齐**：这是双栏工作台，没有跨行数据列；题面小标题/正文/样例左边缘一致，代码工具条按同一
   16px 边距排列。长标题折行，长 slug 可断行，未套用短标题 spread 列表。
3. **文字档位**：标题 foreground、正文 fg-2、说明 fg-muted、版本/模式/快捷键 fg-meta；
   fg-disabled 只用于不可用的运行/提交，其原因另用可读说明。
4. **模板**：`WorkbenchPageTemplate variant="coding"`，模板独占语义 h1；原 `form` 默认行为保持。
5. **设计值**：业务 JSX 没有 `var(--ds-*)`、inline style、raw color 或新增 alias；Monaco 数值接口
   在 UI 适配里解析语义 token，CodeMirror 继续消费统一 token。
6. **有序量与颜色**：难度复用三条形状与中文文字，不靠彩色 Badge；品牌色用于登录入口/焦点，代码
   语法颜色承担词法区分，不把判题状态伪装成高亮颜色。
7. **适配验证**：双主题、320px、键盘、长中文、辅助显示与触控回退已测；窄屏切换保持编辑器挂载，
   隐藏 pane 为 inert。短高度允许代码面板滚动，200% 等效专项结果单独记录。
8. **尺寸对照**：真实 DOM 测得顶栏 56px、sm 按钮 24px、按钮文字 12px、图标 16px、描边 1px；
   与 measurements.md 对应档位一致。编辑器行距为适合源码阅读的 token 派生值，不套用列表 44px 行高。

### 独立复核

独立子智能体复核发现并处理三项 P2：存储失败时新版本切换没有继续入口；手机 IME 组合过程提前通知
保存；C++ 二次懒加载失败被缓存。分别补上备份后明确切换、组合完成后通知、官方 grammar 静态注册。
此外真实浏览器故障测试确认原生动态 import 的失败可能保留缓存，因此增加“刷新页面重试”恢复入口，
刷新前由草稿 beforeunload flush 与离开保护处理；已有源码仍可通过页面复制或下载。

## 未通过项

当前无未处理的产品实现阻断项。用户已签署验收闸；原生手机设备覆盖限制保留，200% 项已明确为等效模拟。

## 范围检查

只改 Web、事实说明、设计组件清单及 WORK-041 文档/工作工具生成索引；没有修改 Java、Go、公开契约、
API 生成文件、数据库、Compose、后台题目工作台业务或题库列表。未构建/重启 Docker 或部署后端。
既有 `apps/server/data/problem-testdata/assets/01a079c1-c530-7962-a5ad-f09adb0a0260.zip` 保持原状。

## 遗留问题

运行、提交、判题历史和云端草稿没有纳入本次实施，不作为已开放能力。

## 剩余风险

- 首次 Monaco 资源较大：Vite 报 runtime 2,983.17 kB raw / 761.30 kB gzip，CSS 116.51 / 17.36 kB；
  worker 300.37 kB raw。本轮使用官方子入口/C++/必要贡献，未加载全语言、LSP或CDN；不提高告警阈值
  隐藏大 chunk 提示。题库和后台不加载这部分。
- 构建仍提示既有扫描文本中的 `var(…)` 无效示例与大 chunk；未将警告当成功能故障，也未抑制警告。
- 草稿仅当前浏览器，256 KiB UTF-8 上限，不自动删除其他页面恢复副本；配额失败时保留内存并要求备份。
  浏览器崩溃/清数据无法承诺恢复未落盘修改，页面已提供状态与离开保护。
- Monaco 原生模块网络失败可能需要刷新；错误态明确提供实际可用的完整刷新恢复路径。
- 操作系统真实手机输入法仍建议人工体验；已自动覆盖 DOM composition 与 Android EditContext 协议，
  不能把桌面模拟称为已覆盖所有手机设备。
- 运行、提交、自测、判题历史仍属后续范围，本次页面完成不代表整条答题链路上线。

## 结论

技术检查 result=pass。用户已签署验收闸，VERIFY 为 approved；刷新后 WORK-041 为 verified。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：技术验证通过并补齐证据，提交人工验收闸审阅，未代签验收
- 2026-09-07：验收闸通过：review → approved。原因：已人工确认题目页面符合预期，接受运行与提交暂未开放
