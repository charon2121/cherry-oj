# cherry-oj

学习型 Online Judge。浏览器端和五个 Java 服务已经建立基础工程，当前可工作的判题引擎由两个 Go 服务组成：

- `judge`：判题编排、测试数据读取、答案比对。
- `sandbox`：执行一条编译或运行命令，返回资源用量与输出。

二者来自同一个 Go module，但部署为**两个独立容器**，通过私有 HTTP 网络通信。

## 项目文档

项目只维护两套文档：

- [`docs/`](./docs/README.md)：已经确认、跨工作项长期有效的全局产品与技术文档；
- [`development/`](./development/README.md)：具体功能或工程工作产生的定义、体验、设计、计划、任务、
  验证与项目记忆。

所有开发工作先建立统一 WORK，不再分别维护产品需求中心和研发任务中心：

```bash
scripts/work list --type work
scripts/work overview
scripts/work flow WORK-002
scripts/work show FEATURE-001
scripts/work check
```

新工作使用 `scripts/work new`：先按 WORK Type 选择产品、基建、修复、重构或改进流程，再按风险、
影响面和额外关注插入阶段与检查，并通过 artifacts 把阶段关联到零份或多份所需文档。
TASK 进入开发前必须声明可查看、可修改和禁止修改的路径；`scripts/work context TASK-001` 可以组装
受这些边界约束的智能体上下文。代码完成只会进入 `implemented`，有实际通过的 VERIFY 后才能进入
`verified`。

## 应用开发入口

前后端的启动方式和工具解释分别维护在应用目录中：

- [Web 开发说明](./apps/web/README.md)：怎样运行、开发和验收一个页面。
- [Web 工具链说明](./apps/web/TOOLCHAIN.md)：`package.json` 中每个直接依赖和开发依赖的职责。
- [Java 服务开发说明](./apps/server/README.md)：五个服务的端口、构建、启动与健康检查。
- [Java 服务工具链说明](./apps/server/TOOLCHAIN.md)：Maven、Spring Boot Parent、BOM、Plugin 和 Starter 的区别。

这些应用文档解释“怎样工作、工具为何存在”；全局产品边界见 [`docs/product.md`](./docs/product.md)，
具体功能验收口径见对应 WORK 下的 FEATURE 与 VERIFY。

## Docker Compose 启动

前置条件：Docker Engine / Docker Desktop，并启用 Compose v2。

```bash
docker compose build
docker compose up -d --wait
```

默认部署行为：

- judge 暴露在宿主机 `127.0.0.1:5051`。
- sandbox 不映射宿主机端口，只允许 judge 通过内部网络访问。
- 测试数据只读挂载到 judge；默认使用仓库中的 A+B 测试 fixture。
- sandbox 的 blob store 和执行工作区使用 tmpfs，容器停止后自动清空。
- judge/sandbox 的 JSON 文件日志写入 `engine-logs` volume，并按 UTC 日期拆分；stdout 日志仍然保留。
- 两个容器都使用非 root 用户、只读根文件系统、移除 Linux capabilities。

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

期望返回 `"verdict":"AC"`、`"environmentFingerprint":"local-compose"`，并且
`caseResults` 中三个测试点全部为 AC。

查看状态和日志：

```bash
docker compose ps
docker compose logs -f judge sandbox
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
| `JUDGE_ENVIRONMENT_FINGERPRINT` | `local-compose` | 实际判题环境指纹；生产必须设为已注册环境值 |
| `SANDBOX_PARALLELISM` | `2` | sandbox 同时执行的任务数 |
| `SANDBOX_CPUS` | `2.0` | sandbox 容器 CPU 配额 |
| `SANDBOX_MEMORY_LIMIT` | `2g` | sandbox 容器总内存上限 |
| `SANDBOX_STORE_SIZE` | `256m` | blob store tmpfs 大小 |
| `SANDBOX_WORKSPACE_SIZE` | `1g` | 编译和运行工作区 tmpfs 大小 |
| `ENGINE_LOG_LEVEL` | `INFO` | judge/sandbox 的 JSON 日志级别 |

生产环境中应移除 judge 的宿主机端口映射，让业务 server 与 judge 通过后端私网通信。
测试数据仍只挂载给 judge，sandbox 不应接触题库答案。

> 安全说明：当前 sandbox 使用 host Container 实现，Docker 只是外围隔离。它适合开发和
> MVP 联调，但还不应执行公网不可信提交；上线前仍需完成 namespace、chroot 和 cgroup
> 的逐任务隔离。
