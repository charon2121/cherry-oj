# cherry-oj

学习型 Online Judge。浏览器端和五个 Java 服务已经建立基础工程，当前可工作的判题引擎是一个 Go 服务
`judge` 加一个 C 执行器：

- `judge`：判题编排、测试数据读取、答案比对；进程内的执行层负责执行编译与运行命令、返回资源用量与输出。
- `sandbox` 执行器（`apps/sandbox`）：setuid-root 的一次性 C 程序，每次执行在隔离环境里跑一条命令。
  只在原生 Linux 部署中使用，见 `deploy/sandbox-linux`。

## 项目文档

项目只维护两套文档：

- [`docs/`](./docs/README.md)：已经确认、跨工作项长期有效的全局产品与技术文档；
- [`development/`](./development/README.md)：明确启用文档流程的工作说明、必要技术材料和历史记录。

开发文档流程默认关闭。需要它时，明确调用仓库 skill：

```text
$dev-work 开发题目收藏功能
$dev-work 修复登录后立即掉线
```

启用后，每个工作先写一份短说明，讲清背景、思路、范围与代价、验收结果；用户确认后实施，
再补实际结果和验证。功能、修复、基建、维护和改进有各自流程，详细材料按需补充。
普通开发不要求建立 WORK、编号任务或签闸，项目工程规范和必要测试继续生效。
已有 WORK 已迁成短入口，原方案和证据保留，见[工作目录](./development/WORKS.md)。

## 应用开发入口

前后端的启动方式和工具解释分别维护在应用目录中：

- [Web 开发说明](./apps/web/README.md)：怎样运行、开发和验收一个页面。
- [Web 工具链说明](./apps/web/TOOLCHAIN.md)：`package.json` 中每个直接依赖和开发依赖的职责。
- [Java 服务开发说明](./apps/server/README.md)：五个服务的端口、构建、启动与健康检查。
- [Java 服务工具链说明](./apps/server/TOOLCHAIN.md)：Maven、Spring Boot Parent、BOM、Plugin 和 Starter 的区别。

这些应用文档解释“怎样工作、工具为何存在”；全局产品边界见 [`docs/product.md`](./docs/product.md)，
具体功能的历史验收口径可从对应工作短说明进入原定义与验证资料。

## Docker Compose 启动

前置条件：Docker Engine / Docker Desktop，并启用 Compose v2。

```bash
docker compose build
docker compose up -d --wait
```

默认部署行为：

- judge 暴露在宿主机 `127.0.0.1:5051`。
- 测试数据只读挂载到 judge；默认使用仓库中的 A+B 测试 fixture。
- 执行层的 blob store 和执行工作区使用 tmpfs，容器停止后自动清空。
- judge 的 JSON 文件日志写入 `engine-logs` volume，并按 UTC 日期拆分；stdout 日志仍然保留。
- 容器使用非 root 用户、只读根文件系统、移除 Linux capabilities。

发送一个 A+B 判题请求：

```bash
curl -sS -X POST http://127.0.0.1:5051/judge \
  -H 'Content-Type: application/json' \
  -d '{
    "submissionId":"docker-smoke",
    "problemId":"problem-a-plus-b",
    "problemVersionId":"problem-a-plus-b-v1",
    "testDataVersionId":"a-plus-b",
    "languageId":"cpp",
    "source":"#include <iostream>\nint main(){long long a,b;std::cin>>a>>b;std::cout<<a+b<<\"\\n\";}",
    "limits":{"cpuNs":1000000000,"memoryBytes":268435456}
  }'
```

期望返回 `"verdict":"AC"`，并且 `caseResults` 中三个测试点全部为 AC。

查看状态和日志：

```bash
docker compose ps
docker compose logs -f judge
```

停止服务：

```bash
docker compose down
```

## 部署参数

Compose 支持通过环境变量或项目根目录的 `.env` 文件覆盖：

| 变量 | 默认值 | 用途 |
|---|---:|---|
| `TESTDATA_PATH` | 仓库测试 fixture | 宿主机测试数据目录，只读挂载给 judge |
| `JUDGE_BIND_ADDRESS` | `127.0.0.1` | judge 的宿主机监听地址 |
| `JUDGE_PORT` | `5051` | judge 的宿主机端口 |
| `JUDGE_CPUS` | `3.0` | judge 容器 CPU 配额（含执行层运行的用户程序） |
| `JUDGE_MEMORY_LIMIT` | `2560m` | judge 容器总内存上限 |
| `JUDGE_PIDS_LIMIT` | `640` | judge 容器进程数上限 |
| `SANDBOX_PARALLELISM` | `2` | 执行层同时执行的命令数 |
| `SANDBOX_MAX_BLOB_BYTES` | `67108864` | 执行层 store 单个 blob 上限 |
| `SANDBOX_STORE_SIZE` | `256m` | blob store tmpfs 大小 |
| `SANDBOX_WORKSPACE_SIZE` | `1g` | 编译和运行工作区 tmpfs 大小 |
| `ENGINE_LOG_LEVEL` | `INFO` | judge 的 JSON 日志级别 |

生产环境中应移除 judge 的宿主机端口映射，让业务 server 与 judge 通过后端私网通信。
标准答案只由判题编排读取，执行层与用户程序接触不到它。

> 安全说明：Compose 使用零隔离的 devhost 后端，Docker 只是外围隔离，只适合可信代码的开发与联调，
> 不能执行不可信提交。逐任务的 namespace、pivot_root、cgroup 与 seccomp 隔离由原生 Linux 部署中的
> sandbox 执行器完成，见 `deploy/sandbox-linux`。
