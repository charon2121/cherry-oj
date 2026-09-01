# 工程规范与模块状态

根目录 [`CLAUDE.md`](../../CLAUDE.md) 只保留每次会话都必须遵守的部分：项目边界、仓库结构、
跨语言铁律、提交约定和协作协议。展开的规范放在这里，动手之前按需读。

| 你要做什么 | 先读 |
|---|---|
| 写 Go（judge / sandbox） | [`go.md`](./go.md) |
| 写 Java（`apps/server`） | [`java.md`](./java.md) + [`TOOLCHAIN.md`](../../apps/server/TOOLCHAIN.md) |
| 写 TypeScript / Web UI | [`typescript.md`](./typescript.md) + [`TOOLCHAIN.md`](../../apps/web/TOOLCHAIN.md) + [`design-system.md`](../design-system.md) |
| 命名、错误、资源、测试的通用约定 | [`conventions.md`](./conventions.md) |
| 提交、hooks、CI | [`git-workflow.md`](./git-workflow.md) |
| 开发流程、工作项、闸 | [`development/README.md`](../../development/README.md) |

## 当前进度

| 部分 | 状态 |
|---|---|
| `contracts/` | ✅ v2：judge / submission / judge-input / snapshot / profile / events；run / verdict 保持稳定 |
| sandbox（store, container, runner, pool, api, cmd） | ✅ 可独立 `curl` |
| `internal/config` | ✅ YAML + 环境变量 |
| judge：`contract`、`testcase`、`language`、`checker`、`client`、`flow` | ✅ |
| judge：`api`、`cmd/judge` | ✅ `POST /judge`，可与 sandbox 双进程联调 |
| Docker 部署 | ✅ judge / sandbox 双容器 Compose（开发与 MVP） |
| `apps/server`（五个 Java 服务） | ✅ Maven 聚合、独立端口、健康检查与基础测试；业务 API 待实现 |
| `apps/web` | ✅ React、Router、Query、样式、组件与测试工具骨架；业务页面待实现 |

---
