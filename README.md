# cherry-oj

学习型 Online Judge。当前判题引擎由两个 Go 服务组成：

- `judge`：判题编排、测试数据读取、答案比对。
- `sandbox`：执行一条编译或运行命令，返回资源用量与输出。

二者来自同一个 Go module，但部署为**两个独立容器**，通过私有 HTTP 网络通信。

## 开发任务中心

进入 Git 的开发任务统一放在 [`tasks/`](./tasks/README.md)。开始开发前先查看可认领任务：

```bash
scripts/task list --ready
scripts/task show TASK-0001
```

Agent 或开发者必须先认领并同步认领提交，再修改代码：

```bash
scripts/task claim TASK-0001 \
  --agent codex/example \
  --branch codex/task-0001
```

任务的状态、硬依赖、租约、验收标准和完成证据都记录在对应 Markdown 文件中。提交前可运行
`scripts/task check` 校验整个依赖图和状态不变量。

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

生产环境中应移除 judge 的宿主机端口映射，让业务 server 与 judge 通过后端私网通信。
测试数据仍只挂载给 judge，sandbox 不应接触题库答案。

> 安全说明：当前 sandbox 使用 host Container 实现，Docker 只是外围隔离。它适合开发和
> MVP 联调，但还不应执行公网不可信提交；上线前仍需完成 namespace、chroot 和 cgroup
> 的逐任务隔离。
