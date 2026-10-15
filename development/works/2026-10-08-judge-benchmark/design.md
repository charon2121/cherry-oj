# 判题 benchmark 设计

> **状态：设计已确认（2026-10-15）。** 开头三项决定都已回答；第一版做到 M4（§8），尚未开始实现。
> **语料格式、预期写法、报告与退出码的约定已写成[判题 benchmark 协议](../../../docs/protocols/benchmark.md)，以那份为准；本文保留方案、取舍和实施步骤。**
> **benchmark 是测试库，有 `judge` 与 `sandbox` 两个入口；凡测「用户提交行为」的现有测试都会搬进来、原位删除（§10）。**
> 术语沿用[测试数据协议](../../../docs/protocols/testdata.md)：testdata 是一堆 testcase，`digest` 叫指纹。

## 0. 已确认的三项

| # | 问题 | 决定 | 影响 |
|---|---|---|---|
| 1 | 攻击样本从哪来？ | 自己设计清单（[attack-cases.md](./attack-cases.md)），源码从现有测试搬来；清单里标「新增」的样本由用户提供或另行商定 | 决定 §5 要不要实现，以及 benchmark 能不能声称「覆盖了沙箱安全」 |
| 2 | 主要在哪里跑？ | 自己的服务器和 GitHub 托管的 CI 机器都可以跑，benchmark 提供一个入口脚本，运行即可；前提是该机器已有 judge 服务和执行器二进制（§4.1）。一次完整的 benchmark 是两次脚本调用（先 `sandbox`、后 `judge`）。本机 devhost 后端没有内核隔离，结果不作隔离证据 | 决定哪些提交的预期有效，见 §3.5 的 `requires` |
| 3 | 第一版范围？ | **做到 M4**：判题部分 8 道题、两个入口、攻击样本首批 18 个、入口脚本、CI 工作流（先不设为必过）。不含：删除旧测试（M5）、9 个新增攻击样本、完整链路入口，见 §8 | 第一版体量与交付时间 |

## 1. 目的与非目标

**目的：** 给任何一个 judge 节点一份可重复的体检：同一批提交，应该得到同一批结果；不一致就说明判题或限制行为变了。
它顶替的是日常 CI 精简后不再自动验证的那部分真实行为。

**非目标：**
- 不是性能压测：不比较耗时，只记录。
- 攻击样本的清单见 [attack-cases.md](./attack-cases.md)；样本源码从现有测试搬来，不新写。
- 不测 Java 服务、Kafka、前端（第二版才接完整链路）。
- 不替代单元测试：它只管「端到端的判题结论」。

## 2. 总体结构

```text
benchmark/
  README.md                 怎么跑、怎么读报告
  corpus/
    corpus.lock             判题部分的指纹锁定
    judge/<题名>/           正常的判题 benchmark（§3）
    attack/<名>/            攻击代码的 benchmark（§5），样本从现有测试搬来
  tools/                    生成、交叉验证、发布、runner、报告（Python，带单测）
  reports/                  运行产物，不入库（.gitignore）
```

三类动作：**生成**（确定性地造出测试数据，并用独立解法算期望输出）→ **发布**（把语料目录放到节点读得到的位置）→
**运行**（发请求、比对预期、出报告）。生成只在开发机做，结果入库；运行可以在任何能连上 judge 的地方做。

## 3. 判题语料

### 3.1 一道题的目录

```text
corpus/judge/<题名>/
  problem.json        题目元数据：名称、检验什么、限制（cpuNs / memoryBytes / clockNs）、requires
  testdata/           协议目录：testdata.json + 成对的 <name>.in / <name>.out（由 scripts/testdata_pack.py 写出）
  submissions/<名>.cpp  或 .c
  expect.json         每个提交的预期（§3.3）
  oracle/             生成与交叉验证用的参考解（Python，在开发机上跑，不进 judge）
  README.md           这道题在检验什么、每个提交为什么存在
```

`testdata/` 就是协议目录，所以 runner 发的请求和线上完全一致：`testDataLocation` 指向它（发布后的路径），judge 自己读。

### 3.2 题目清单

每道题只检验一个容易出错的点；提交名里带预期结论，便于一眼看懂。

| 题 | 检验什么 | testcase 设计 | 提交（预期整体） |
|---|---|---|---|
| `a-plus-b` | 基线链路 | 3 个：普通、含负数、含 0 | `ac_iostream` AC · `ac_scanf` AC · `wa_minus` WA · `ce_syntax` CE · `re_exit1` RE · `re_abort` RE · `tle_loop` TLE |
| `whitespace` | 比对对空白的处理 | 同一答案配多种排版：行尾空格、多余空行、缺末尾换行、`\r\n` | `ac_exact` AC · `ac_trailing_space` AC¹ · `ac_no_final_newline` AC¹ · `wa_token` WA · `pe_extra_blank` PE² |
| `sort-numbers` | 中等规模输入输出与时间 | 5 个：n 从 1 到 10^5，含重复与逆序 | `ac_std_sort` AC · `ac_qsort` AC · `tle_bubble` TLE · `wa_unique` WA（误去重） |
| `sum-large` | 64 位整数 | 4 个：总和超过 2^31 的、接近 2^63 的、负数的 | `ac_int64` AC · `wa_int32` WA（溢出）· `ac_int128` AC |
| `alloc-array` | 内存限制 | 3 个：按输入申请并**写满**对应大小的数组 | `ac_small` AC · `ac_mid` AC · `mle_big` MLE |
| `big-output` | 输出上限 | 2 个：要求输出 8 MiB | `ole_flood` OLE³ · `ac_modest` AC（输出远小于上限） |
| `empty-case` | 空输入与空输出 | 2 个：输入为空、期望输出为空 | `ac_silent` AC · `ac_reads_eof` AC · `wa_prints_text` WA |
| `partial` | 部分通过与汇总 | 6 个 | `fail_first_only` WA（第 1 个错，其余对：前面的点错了也要照跑）· `fail_last_only` WA · `pass_half_tle_rest` TLE · `all_pass` AC |

¹ 在 `strictWhitespace=false`（默认）下。² 只在 `strictWhitespace=true` 的节点上有意义，见 §3.5。
³ 只在有输出上限的 linux 后端有意义（执行器 stdout 硬界 1 MiB），devhost 默认上限 64 MiB，见 §3.5。

题目数量与细节可调；原则是**每个 verdict 至少被一道题的一个提交触发**（AC、WA、PE、CE、RE、TLE、MLE、OLE，SE 用「地址不存在」
这类请求触发，放在 `a-plus-b` 的「请求级用例」里，见 §3.3）。

### 3.3 `expect.json`

```json
{
  "schemaVersion": 1,
  "submissions": {
    "ac_iostream": {
      "verdict": "AC",
      "testcases": ["AC", "AC", "AC"]
    },
    "fail_first_only": {
      "verdict": "WA",
      "testcases": ["WA", "AC", "AC", "AC", "AC", "AC"]
    },
    "ce_syntax": { "verdict": "CE", "testcases": [] }
  },
  "requests": {
    "missing_address":  { "testDataLocation": "/nonexistent/benchmark", "source": "ac_iostream", "verdict": "SE" },
    "relative_address": { "testDataLocation": "relative/path",           "source": "ac_iostream", "verdict": "SE" }
  }
}
```

- `verdict` 是整体结论，`testcases` 是**逐测试点**的结论（顺序 = `testdata.json` 里 `testcases` 的顺序）。
- 整体必须等于逐点结论按 judge 的严重度取最坏者（SE < CE < RE < MLE < OLE < TLE < WA < PE < AC，与 `flow/result.go` 一致）；
  生成脚本校验这一点，**不一致就拒绝写出**，避免预期自相矛盾。
- CE 没有逐点结果（`testcases: []`）；`score` 不单独断言，只在 AC 时应为 100，其余为 0。
- `requests` 是请求级用例：不换提交代码，改的是请求本身（坏地址、相对路径、篡改数据后的地址），用来钉住 SE 的路径。
  篡改类用例需要 runner 在发布副本上改文件（只改副本，不动语料）。
- 判题结果的 `testDataDigest` 必须等于该题 `testdata.json` 的 `digest`（每个 AC/WA 类提交都断言一次）。

### 3.4 生成与交叉验证

目标：预期值不能是「judge 判出来什么就是什么」，否则 benchmark 只会把现有缺陷固化。

1. **确定性生成。** `tools/generate.py` 用固定种子（写在 `problem.json`）造 `.in`；同样的种子、同样的代码，必须得到逐字节相同的数据。
2. **两个独立来源。** 期望输出 `.out` 由 `oracle/` 里的 Python 参考解算出（来源 A）；再由第二份**算法不同**的 Python 解独立算一遍（来源 B，
   例如一个用 `sorted()`，另一个用手写堆）。A 与 B 不一致 → 生成失败。
3. **被测物独立于参考解。** C/C++ 的 `ac_*` 提交由 judge 判，必须判出 AC：它们是第三个来源，错位时能暴露 oracle 或 judge 的问题。
4. **锁定指纹。** 生成后用 `testdata_pack.py` 写出 `testdata.json`，把每道题的 `digest` 和整库指纹写进 `corpus.lock`（入库）。
   CI/本机的 `tools/check.py` 重新生成并比对：不一致说明数据漂移，必须显式更新 lock 并说明原因。
5. **预期的来源要可解释。** `README.md` 里写清楚每个错误提交「为什么会得到这个结论」，评审时可以直接核对。

### 3.5 `requires`：预期依赖节点的配置

有些预期只在特定配置下成立。每道题（或每个提交）可以带 `requires`，runner 在报告里按它分三种状态：**运行**、**跳过（配置不满足）**、**失败**。

| 标签 | 含义 | 影响的例子 |
|---|---|---|
| `backend:linux` | 有真实内核隔离、stdout 硬界 1 MiB | `ole_flood`、`mle_big`（devhost 的内存统计与 linux 不可比） |
| `strictWhitespace:true` | 节点按严格空白判 PE | `pe_extra_blank` |

这些值**由运行者声明**（`--backend`、`--assume strictWhitespace=...`）并写入报告，runner 不去猜；judge 的 `/version` 不暴露配置。
声明错了会表现为「跳过项被当成运行」，所以报告首页明确列出声明值。**devhost 的结果永远不当作隔离证据。**

### 3.6 稳定性：时间与内存类用例

- 绝不把预期放在边界上。TLE 提交用「死循环」或「远超限制」的写法，限制给得宽（例如 1 s 限制，TLE 提交跑数秒仍不结束）；
  MLE 提交申请并写满 ≥ 2 倍限制；合格提交的峰值 ≤ 限制的一半。「刚好卡限制」另设**探测项**（`probe`）：只记录实际结论，不进断言。
- 时间/内存类提交默认**重复 3 次**，要求 verdict 完全一致；不一致报「不稳定」而不是随机通过。
- 耗时与内存只**记录**（每个测试点的 `cpuNs`、`memoryBytes`），不断言；报告可以画趋势，但不做通过/失败判定。

## 4. Runner

### 4.1 入口脚本

benchmark 只提供一个入口脚本 `benchmark/run.py`（只用标准库）。在自己的服务器上和 GitHub 托管的 CI 机器上，运行方式相同：

```text
python3 benchmark/run.py --entries judge   --judge-url http://127.0.0.1:5051 \
        --published-root /srv/cherry-benchmark --declare backend=linux strictWhitespace=false
python3 benchmark/run.py --entries sandbox --executor /opt/cherry-oj/current/libexec/sandbox \
        --executor-config /opt/cherry-oj/etc/executor.conf --declare backend=linux
        [--select a-plus-b] [--report reports/<时间>.json]
```

**脚本不负责安装或启动被测节点**，运行它的机器必须已经具备：

| 前提 | judge 入口 | sandbox 入口 |
|---|---|---|
| judge 服务在运行，`--judge-url` 可达 | 需要 | **不能运行**：judge 持有 boxes 根的 `.lock`，脚本取不到锁会按环境错误退出（协议 §7.2） |
| 执行器二进制（setuid-root）与受信配置 | 由 judge 使用，脚本不碰 | 需要，脚本以 root 或服务身份直接调用 |
| 测试数据发布位置 | 需要，且 judge 的运行身份能一路进得去（0755/0644） | 不需要 |
| 脚本与被测节点同机 | 取决于发布位置能否共享 | 需要 |

因为两个入口对「judge 是否在运行」的要求相反，**两个入口分两次运行**：先在 judge 停止时运行 `--entries sandbox`，再在 judge 运行时运行 `--entries judge`。
两次的报告各自独立，汇总时按 `environment.declared.entries` 区分（协议 §10）。

- **自己的服务器：** judge 与执行器已由安装器装好（`/opt/cherry-oj`）。`sandbox` 入口前先 `systemctl stop cherry-sandbox-judge.service`，跑完再 `start`，然后运行 `judge` 入口。
- **GitHub 托管的 CI：** 工作流里先构建 judge 与执行器、起好节点（可复用 `deploy/sandbox-linux/tests/` 里的夹具与 `judge_service.py`），再运行同一个脚本；
  这一步的具体编排属于 CI 工作流的设计，不属于脚本。
- **本机开发：** 可以对 devhost 后端的 judge 运行 `judge` 入口调试逻辑，`sandbox` 入口和所有 `backend:linux` 的项会被跳过。

对每道题的每个提交：读源码 → 构造 `JudgeRequest`（`mode=submit`、`testDataLocation=<published-root>/<题名>/testdata`、
`limits` 来自 `problem.json`、`languageId` 由扩展名决定）→ 同步等待 → 与 `expect.json` 逐项比对 → 记结果。串行执行。

- **发布（`tools/publish.py`）：** 校验 `corpus.lock` 后，把 `testdata/` 复制到 `--published-root`（目录 0755、文件 0644，
  与 problem-service 写出的权限一致），并写入一个发布清单。**测试数据根目录必须让 judge 用户能一路进得去**——这是已知的部署坑（0700 的父目录会让读取失败而得到 SE）。
- **只读语料：** 篡改类请求用例在发布副本上操作并在结束后还原，语料本身不被修改。
- **退出码：** 0 全部符合预期；1 有失败；2 有不稳定项；3 环境/配置错误（连不上、校验失败、取不到 box 锁）。
- **声明：** `--declare` 给出的配置（`backend`、`strictWhitespace`、`revealExpected`、`languages` 等）写进报告；脚本不去猜节点配置。

### 4.2 第二版：完整链路入口

复用 `scripts/work-002-e2e.py` 的栈搭建与管理 API 调用：对每道题创建题目、上传 ZIP（由 `testdata/` 打包）、标定、公开，
再通过提交 API 提交每个提交，轮询结果，用同一份 `expect.json` 比对。它额外验证 Java 服务与 Kafka 链路上的结论投影（例如 CE 的诊断、
`passedTestcaseCount`）。第一版稳定后再做，不阻塞第一版。

## 5. 攻击样本与两个入口

benchmark 是一个**测试库**：测试代码和测试所需的「用户提交的代码」都在里面。它有两个入口（协议见 [benchmark 协议](../../../docs/protocols/benchmark.md)）：
`judge` 入口经 `POST /judge`，看 verdict；`sandbox` 入口直接调用执行器，看事实 JSON。同一份提交可以从任一入口测试。

攻击样本的清单、每个样本在两个入口的预期、以及从哪里迁来源码，见 [attack-cases.md](./attack-cases.md)。样本源码**从现有测试里搬来**，不新写。

## 6. 报告

**机器可读（`reports/<时间>-<节点>.json`）：**

```json
{
  "schemaVersion": 1,
  "startedAt": "...", "finishedAt": "...",
  "environment": {
    "endpoint": "...", "judgeVersion": "0.1.0-mvp",
    "declared": { "backend": "linux", "strictWhitespace": false },
    "kernel": "6.8.0-124-generic", "gitSha": "...", "corpusDigest": "...",
    "rootfsManifestSha256": "..."
  },
  "summary": {
    "judge":  { "ran": 52, "passed": 50, "failed": 1, "unstable": 1, "skipped": 4 },
    "attack": { "samples": 0, "ran": 0, "passed": 0, "failed": 0, "skipped": 0 } },
  "judge": [ { "name": "a-plus-b", "digest": "...", "submissions": [
    { "name": "ac_iostream", "status": "pass", "expected": "AC", "actual": "AC",
      "testcases": [ {"idx":1,"expected":"AC","actual":"AC","cpuNs":1200000,"memoryBytes":1048576} ], "repeats": 1 } ] } ]
}
```

- `rootfsManifestSha256`、`kernel` 等节点侧信息由运行者通过可选参数提供（runner 不 SSH 到节点）；缺失就写 `null`，不猜。
- 失败项带**差异**：预期/实际并排，逐点高亮不同的位置；不输出隐藏数据正文（只输出名字、verdict、长度）。

**人读汇总：** 终端表格（题 × 提交 × 状态）加一屏结论：环境声明、跳过项与原因、失败项差异、攻击样本数。

## 7. 怎么验证 benchmark 自己

- **runner 单测（mock judge）：** 全部符合 → 退出码 0；故意改坏一条预期 → 退出码 1 且报告里有差异；不稳定项 → 2；连不上 → 3；
  `requires` 不满足 → 跳过而不是失败。
- **生成脚本单测：** 同种子两次生成逐字节相同；来源 A 与 B 故意不一致 → 拒绝写出；整体与逐点预期自相矛盾 → 拒绝写出；
  `corpus.lock` 与实际不符 → `check.py` 失败。
- **对真实 judge 的首次运行就是验收：** 本机 Docker 全部通过（跳过 `backend:linux` 项），测试服务器原生节点全部通过（含 `backend:linux` 项），报告入档。
- **回归保护：** 提交前可以只跑 `check.py`（不需要 judge），保证语料没有漂移。

## 8. 实施步骤

第一版 = M1 到 M4。每个里程碑独立可验收，前一个通过才开始下一个。

| 里程碑 | 内容 | 验收 |
|---|---|---|
| **M1** | 入口脚本骨架；`tools/` 里的协议目录生成（复用 `testdata_pack.py`）、`generate.py`、`check.py`、`corpus.lock`；报告与退出码；`a-plus-b` 通过 `judge` 入口跑通；runner 自己的单测（mock judge，见 §7） | 本机与测试服务器上 `a-plus-b` 的全部提交符合预期；单测覆盖退出码 0/1/2/3 与跳过 |
| **M2** | 其余 7 道题（含两份算法不同的参考解交叉验证）、全部请求级用例（含 `unknown_language`、`language_not_installed`、`tampered_input`）、`trial` 用例、`publish.py` | 判题部分 42 个提交、5 个请求级用例、3 个试运行用例在测试服务器原生节点上全部通过；报告入档 |
| **M3** | `sandbox` 入口（运行说明、并发组、独占 box 锁）；`attack-common/`（共用测试数据与 `probe.cpp`）；攻击样本首批 18 个（A1–A4、A6–A8、A14–A17、B1–B7），源码从现有测试**复制**过来（此时不删旧测试） | 测试服务器上 `sandbox` 入口与 `judge` 入口各跑一遍全部通过；每个样本的回收检查与干净运行通过 |
| **M4** | CI 工作流：只在判题相关路径有改动时触发；GitHub 托管机器上构建并起好 judge 与执行器，按顺序运行两次脚本；上传报告；**先不设为必过检查** | 在托管机器上完整跑通一次；稳定运行约一周没有误报后，再另行决定是否设为必过 |
| M5（第一版之外） | 按 §10 迁移并删除旧测试：每搬一类先在 benchmark 里通过，再删原位置 | 旧位置不再有「用户提交行为」的案例 |
| 之后 | 9 个新增攻击样本（A5、A9–A13、B8–B10，其中 B8–B10 由用户提供）；完整链路入口（§4.2）；CI 设为必过 | |

**第一版不含 M5 的原因：** 删除旧测试不可逆，应当在 benchmark 在 CI 上稳定运行之后再做，避免中间出现没人测的空档。

## 10. 迁移与删除

规则：**凡是在测「用户提交的行为」的测试，整体搬进 benchmark，原位置删除；不留引用，也不留内嵌副本。**
测平台自己（协议拒绝路径、安装、崩溃恢复、单元测试）的测试留在原位，它们需要的简单程序由各自的夹具自带。

| 现位置 | 去向 | 处理 |
|---|---|---|
| `deploy/sandbox-linux/tests/judge_program.py` | `attack/` 的 A1–A8、A14、B1–B7、B11 | 一个行为一个样本，源码拆开，整个文件删除 |
| `deploy/sandbox-linux/tests/acceptance/*.cpp` 与 `README.md` | `judge/` 与 `attack/` | 正常的并入 `judge-cases.md` 对应提交，资源类并入攻击样本；`observe_run.py` 视情况搬入 |
| `deploy/sandbox-linux/tests/{cpp_limits,cpp,extended,smoke}.py` | `attack/` | 内嵌源码与断言搬走，文件删除 |
| `deploy/sandbox-linux/tests/judge_chain.py` 的 smoke 与重复 | `judge/` 与 `attack/` | smoke 里的各模式与 1000 次重复搬走（对应 A17）；**调用方断开 HTTP 连接的取消（C5）与「两个请求并发、峰值 2 个执行组」（C7）是平台行为，留在原位**，否则整个文件删除时没人测 |
| `apps/sandbox/tests/probe.c` | `attack/` 的各样本 | 模式拆成独立样本；执行器测试里仍需要的简单程序，由 `run_tests.py` 的夹具自带 |
| `apps/sandbox/tests/run_tests.py` 里的这些用例 | benchmark 的 `sandbox` 入口 | `test_echo_streams_and_exit`、`test_exit_code_and_signal`、`test_identity_and_filesystem`、`test_cpu_limit`、`test_wall_limit`、`test_memory_limit_is_task_oom`、`test_output_limit`、`test_network_is_killed_by_seccomp`、`test_x32_abi_is_killed`、`test_process_and_thread_limits`、`test_background_processes_are_reclaimed`、`test_privilege_and_peer_isolation`、`test_links_cannot_be_created` 搬走并删除 |
| `deploy/sandbox-linux/install/verify-*.py` 与 `fault_batch.py` 里的内嵌源码 | 不搬 | 这些是平台恢复测试的夹具，保留一行最简程序即可（如 `sleep`） |
| `apps/judge-engine` 与 `apps/server` 的单元测试里的内嵌源码 | 不搬 | 它们验证的是假后端或服务层，不是判题行为 |
| `fault_batch.py` 的 `aggregate-memory`、`judge_program` 的 `hold` | 不搬，需重新评估 | 它们依赖已删除的 cgroup 总量上限，前提不再成立 |
| `scripts/work-002-e2e.py` 回显隐藏输入的提交 | 不搬 | 测的是 Java 服务对隐藏数据的展示投影，不是 judge 或 sandbox |

**留在 `run_tests.py` 的：** 非法请求被拒、同一 box 并发被拒、遗留执行组被回收、取消、命令不存在、产物规则、多次运行不泄漏。
**搬迁顺序：** 每搬一类，先在 benchmark 里让它通过，再删除原位置；任何时候都不会出现既没有原测试、也还没有新测试的空档。

## 9. 风险与已知限制

- **devhost 无隔离：** 本机 Docker 通过只说明判题逻辑正确，不能说明沙箱安全。报告首页声明后端，防止误读。
- **时间类噪声：** 机器负载会影响 TLE/MLE 的边界，所以预期远离边界并重复运行；仍不稳定的项会被如实标出，而不是静默通过。
- **rootfs 只有 C/C++ 工具链：** 语料不含其他语言；日后加语言要先扩 rootfs。
- **语料是公开的：** 它验证判题行为，不是防作弊用的隐藏数据。
- **读取路径的权限：** 发布目录对 judge 用户不可达会得到 SE，容易被误当作判题缺陷；`publish.py` 发布后做一次可读性自检（以 judge 用户能读到的方式，或至少检查每级目录的 `o+x`）。
- **未覆盖：** 并发判题、长时间稳定性、网络隔离之外的攻击面（见 §5）、Java/Kafka 链路（第二版）。
