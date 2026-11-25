# problem-build

`problem-build` 在发布前验证 ACM 和 CORE 题。它通过现有 judge 试运行验证 C、C++、PyPy、Java 作答程序。
在安装了 C++ 和作答语言工具链的受信原生 Linux 判题节点上运行。也可以通过 SSH 把该节点的判题入口转发到本机回环地址。
报告记录运行者提供的工具链清单摘要。

在 `apps/judge-engine` 目录构建并运行「区间求和」夹具。将示例中的地址和清单改为本次使用的节点配置。

```sh
work=$(mktemp -d)
go build -o "$work/problem-build" ./cmd/problem-build
sh cmd/problem-build/testdata/range-sum/run.sh \
  "$work/problem-build" /opt/cherry-oj/current/toolchains/cpp/manifest.json \
  "$work/range-sum" http://127.0.0.1:15051
```

## 验证自己的题

- 用 `-statement` 指定题面与约束。用 `-generator`、`-validator` 和 `-oracle` 指定独立的 C++ 程序。生成器输出测试输入，校验器用退出码 0 表示合法，参考解和独立核对解都输出该测试点的答案。
- 用 `-reference` 指定作答语言的参考解，默认用 C++。使用其他语言时通过 `-answer-language` 指定语言编号，并提供对应的 `-answer-toolchain-manifest`。
- CORE 题通过 `-driver` 指定驱动，`-starter` 指定编辑器起始代码。参考解与错误解均提供核心代码。用 `-solution-prefix` 指定题目使用的语言前缀，工具会原样拼接到每份核心代码之前。起始代码需在公开样例上正常退出，可返回占位值。
- 为每个用例传入一个 `-case name=path`。对应的文件原样作为该点的生成器标准输入，固定种子和覆盖目标由出题人安排。命令支持 1 至 64 个测试点，名字以小写字母开头，可包含数字和连字符。
- 每个点至少指定 `-oracle-case name` 或 `-expected name=path`。每次构建至少需要一个独立解核对点和一个公开样例。对公开样例使用 `-sample name=path` 和 `-expected name=path` 核对输入与输出原文，并在审核时检查这两份文件与题面一致。
- 用可重复的 `-wrong name=sourceFile` 添加 1 至 64 份典型错误解，`name` 是该错误解必须得到 WA 的测试点。其余点须正常完成并返回 AC 或 WA。
- 使用 `-cpu-ns`、`-clock-ns` 和 `-memory-bytes` 设定单点上限，整次验题最多运行 10 分钟。试运行须返回完整的 UTF-8 输入和答案。单份文本最多 64 KiB，实际还受 judge 的输出摘录上限约束。checker 试运行要求非空标准输出，独立答案须逐字节一致。

用 `problem-build -help` 查看完整参数。生成器的输入文件和源码应保留，以便复现。

## 审核和应用

通过后，新的私有产物目录包含 `testdata.zip` 和 `report.txt`。ZIP 按上传顺序包含成对的 `.in` 和 `.out`。
管理员查看报告中的用例、数据指纹、判题结论和作答语言的实测资源用量，再将 ZIP 上传到私有题目，核对上传指纹，
使用当前数据完成基础标定，最后通过公开检查并公开。报告和临时输入可能包含隐藏数据，应保存在私有位置。

执行途中失败会返回非零退出码并写出失败报告。报告会指出失败阶段，并在能够定位到测试点时记录复现所需的输入或答案。

## 管理工作台

problem-service 可调用同一二进制运行后台验题。先构建目标平台的 `problem-build`，将下列配置注入 problem-service。

| 配置 | 取值 |
| --- | --- |
| `CHERRY_AUTHORING_BINARY` | 可执行文件绝对路径 |
| `CHERRY_AUTHORING_JUDGE_URL` | 受信原生 Linux 判题节点的回环 HTTP 地址，可由 SSH 转发提供 |
| `CHERRY_AUTHORING_TOOLCHAIN_ROOT` | 原生节点发布的 `toolchains/` 副本，各语言子目录必须有 `manifest.json` |
| `CHERRY_AUTHORING_WORK_ROOT` | problem-service 可写的私有工作目录绝对路径 |
| `CHERRY_AUTHORING_OPENAI_MODEL` 与 `CHERRY_AUTHORING_OPENAI_API_KEY` | 可选的 OpenAI 模型与部署密钥 |

在线构建和 AI 提案默认关闭。管理页使用固定构建输入和现有标定、公开流程。目录、字段和失败约定见 [自动出题协议](../../../../docs/protocols/problem-authoring.md)。
