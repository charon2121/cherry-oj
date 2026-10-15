# benchmark 协议

规定 benchmark 的**语料格式、预期的写法、报告的格式和运行结果的含义**。benchmark 检验「用户提交的代码」在 judge 与 sandbox 上的行为：
同一批提交应得到同一批结论，不一致就说明判题或限制行为变了。语料、运行者和报告只靠本协议对齐。

benchmark 有两个**入口**，同一份提交可以从任一入口测试：

| 入口 | 从哪里开始 | 看到什么 |
|---|---|---|
| `judge` | `POST /judge`（[judge 调用协议](./judge-api.md)） | 判题结论 verdict、逐测试点结论 |
| `sandbox` | 直接调用执行器（[执行器协议](./sandbox-executor.md)） | 执行器的原始事实：退出码、信号、终止原因、OOM 次数…… |

## 快速使用

语料分两部分：`judge/` 是正常的判题 benchmark，`attack/` 是攻击代码的 benchmark。判题部分的一道题是一个目录，最小的一道：

```text
corpus/judge/a-plus-b/
├── problem.json
├── testdata/                 测试数据协议目录：testdata.json + 成对的 1.in / 1.out …
├── submissions/
│   ├── ac_iostream.cpp
│   └── wa_minus.cpp
└── expect.json
```

`problem.json`：

```json
{ "schemaVersion": 1, "name": "a-plus-b", "title": "基线链路", "seed": 1,
  "limits": { "cpuNs": 1000000000, "memoryBytes": 268435456, "clockNs": 3000000000 }, "requires": [] }
```

`expect.json`：

```json
{ "schemaVersion": 1,
  "submissions": {
    "ac_iostream": { "verdict": "AC", "testcases": ["AC", "AC", "AC"] },
    "wa_minus":    { "verdict": "WA", "testcases": ["WA", "WA", "AC"] } },
  "requests": {} }
```

运行者对每个提交构造一次判题请求，与 `expect.json` 逐项比对，输出报告（§10）；退出码表示整体结论（§9）。
一个攻击样本是 `attack/` 下的一个目录，最小的一个见 §5。

## 1. 术语

沿用[测试数据协议](./testdata.md)：testdata 是一堆 testcase，digest 叫「指纹」。

| 名称 | 含义 |
|---|---|
| 语料（corpus） | 判题部分和攻击部分的目录集合 |
| 判题部分 | 语料下的 `judge/`，检验判题结论是否正确 |
| 攻击部分 | 语料下的 `attack/`，检验沙箱是否守住限制 |
| 入口（entry） | 测试从哪里开始：`judge` 或 `sandbox`，见上表 |
| 题目 | `judge/` 下的一个子目录，带测试数据、提交和预期 |
| 提交 | 题目下 `submissions/` 里的一份源码，每份带预期 |
| 攻击样本 | `attack/` 下的一个子目录，装一份试图越过沙箱限制的程序，见 §5 |
| 请求级用例 | 不换源码、只改请求本身（坏地址等）的用例，只属于 `judge` 入口 |
| 运行说明（run spec） | 从 `sandbox` 入口运行一次程序的输入与预期，见 §6 |
| 运行者 | 读语料、从入口发起调用、比对预期并出报告的程序 |

## 2. 语料布局

```text
<语料根>/
├── corpus.lock                     锁定文件（§2.4），只覆盖判题部分
├── judge/                          判题部分：正常的判题 benchmark
│   └── <题名>/                     题名匹配 ^[a-z0-9][a-z0-9-]{0,62}$，judge/ 内不重复
│       ├── problem.json            题目元数据（§2.1）
│       ├── testdata/               测试数据协议目录，由生成步骤写出，入库
│       ├── submissions/<名>.cpp    提交源码；名字匹配 ^[a-z0-9][a-z0-9_]{0,62}$
│       ├── expect.json             预期（§3）
│       ├── oracle/                 生成与交叉验证用的参考解，不进 judge
│       └── README.md               这道题检验什么、每个提交为什么得到该结论
├── attack-common/                  攻击样本共用的内容（§5）
│   ├── testdata/                   测试数据协议目录：一个测试点，输入为空，期望输出是一行 OK
│   └── probe.cpp                   干净探针：只输出 OK（§8）
└── attack/                         攻击部分：攻击代码的 benchmark，可以为空
    └── <名>/                       攻击样本（§5），名字规则同题名
```

两部分各自独立：判题部分比对结论的正确性，攻击部分比对限制是否被守住并检查回收；两部分共用 §7 的运行流程、§9 的退出码和 §10 的报告，报告中分开汇总。

提交与样本的语言由扩展名决定，对应 judge 的语言标识；扩展名对应的语言必须是被测 judge 支持的。目前只有 `.cpp`。

### 2.1 problem.json

| 字段 | 规则 |
|---|---|
| `schemaVersion` | 固定 `1` |
| `name` | 与目录名相同 |
| `title` | 这道题检验什么，一句话 |
| `seed` | 生成 `.in` 用的整数种子；同一种子与同一生成程序必须得到逐字节相同的数据 |
| `limits` | `cpuNs`、`memoryBytes`、`clockNs`，原样放进判题请求的 `limits`，均为正整数 |
| `requires` | 本题成立所需的节点配置标签（§4），可以为空 |

不允许出现其他字段。

### 2.2 一道题只检验一个容易出错的点

题目设计的约束（不是字段规则）：
- 语料整体必须使每一种 verdict（AC、WA、PE、CE、RE、TLE、MLE、OLE）至少被一个提交触发；SE 由请求级用例触发（§3.3）。
- 时间、内存类提交的预期不能放在边界上：TLE 提交要远超限制，MLE 提交要申请并写满不少于 2 倍限制，合格提交的峰值不超过限制的一半。
  「刚好卡限制」的情形只能作为**探测项**（§3.4），不进断言。

### 2.3 数据的两个独立来源

期望输出 `.out` 不能由被测 judge 产生，否则 benchmark 只会固化现有缺陷：
1. 同一道题必须有两份**算法不同**的参考解各自独立算出期望输出，两者必须逐字节一致，否则这道题不合格；
2. 被测物（`submissions/`）是第三个来源：标为 AC 的提交必须由 judge 判出 AC，对不上说明参考解或 judge 有问题。

### 2.4 corpus.lock

```json
{ "schemaVersion": 1,
  "problems": { "a-plus-b": "<该题 testdata.json 的 digest>", "whitespace": "<…>" },
  "corpusDigest": "<64 位小写十六进制>" }
```

`corpusDigest`：只对判题部分计算，按题名升序，对每题生成一行 `"<digest>  <题名>\n"`，拼接后取 SHA-256。
重新生成语料后得到的指纹与锁定值不一致，就是数据漂移；必须显式更新锁定文件并说明原因，不能悄悄通过。

## 3. expect.json

| 字段 | 规则 |
|---|---|
| `schemaVersion` | 固定 `1` |
| `submissions` | 键是提交名（去掉扩展名），必须与 `submissions/` 下的文件一一对应 |
| `submissions.<名>.verdict` | `judge` 入口的整体结论 |
| `submissions.<名>.testcases` | 逐测试点结论，顺序与 `testdata.json` 的 `testcases` 一致；CE 时为空数组 |
| `submissions.<名>.message` | 可选；对整体结果 `message` 的断言：`equals`、`startsWith` 或 `contains` 之一。用于 CE 的编译诊断 |
| `submissions.<名>.sandbox` | 可选；`sandbox` 入口的运行说明数组（§6） |
| `submissions.<名>.requires` | 可选；仅对这个提交成立所需的标签（§4） |
| `submissions.<名>.probe` | 可选，`true` 表示探测项（§3.4） |
| `requests` | 请求级用例（§3.3），可以为空对象 |
| `trials` | 试运行用例（§3.5），可以为空对象 |

结论取值：`AC`、`WA`、`PE`、`CE`、`RE`、`TLE`、`MLE`、`OLE`、`SE`；试运行（§3.5）另有 `RAN`。

### 3.1 整体与逐点必须一致

整体结论必须等于逐点结论按下列严重度取最坏者：`SE` 最严重，依次为 `CE`、`RE`、`MLE`、`OLE`、`TLE`、`WA`、`PE`、`RAN`，`AC` 最轻。
不一致的 `expect.json` 不合格，生成步骤必须拒绝写出，避免预期自相矛盾。

### 3.2 分数与指纹

- 不单独断言 `score`：只要求 AC 时为 100，其余为 0。
- 判题结果的 `testDataDigest` 必须等于该题 `testdata.json` 的 `digest`；每个产生逐点结果的提交都要核对。

### 3.3 请求级用例

不换提交代码，改的是请求本身，用来钉住 SE 的路径：

```json
"requests": {
  "missing_address":  { "testDataLocation": "/nonexistent/benchmark", "source": "ac_iostream", "verdict": "SE" },
  "relative_address": { "testDataLocation": "relative/path",           "source": "ac_iostream", "verdict": "SE" },
  "unknown_language": { "languageId": "brainfuck", "source": "ac_iostream", "verdict": "SE",
                        "message": { "contains": "unknown language" } },
  "language_not_installed": { "languageId": "python", "sourceText": "print(1)\n", "verdict": "SE",
                              "body": { "contains": "errno=2" }, "requires": ["noLanguage:python"] }
}
```

| 字段 | 规则 |
|---|---|
| `source` | 本题 `submissions/` 里的提交名；与 `sourceText` 互斥，二选一 |
| `sourceText` | 内联的源码文本，用于该语言在本题里没有对应提交的情形 |
| `testDataLocation` | 替换请求里的地址 |
| `languageId` | 替换请求里的语言标识 |
| `limits` | 覆盖请求里的同名限制 |
| `verdict` | 预期的整体结论 |
| `message` | 可选，对响应顶层 `message` 的断言 |
| `body` | 可选，对整个响应正文（JSON 文本）的断言；用于原因出现在逐测试点结果里的情形 |
| `requires` | 可选，标签（§4） |

需要「篡改已发布数据」的用例，运行者只能改**发布副本**，并在结束后还原，不得修改语料本身。

### 3.4 探测项

`probe: true` 的提交只记录实际结论，不进断言，不影响退出码。用于观察边界行为，例如恰好卡在限制上的程序。

### 3.5 试运行

`mode` 为 `trial` 的请求：测试点在请求里，不需要 `testDataLocation`，也没有 `testDataDigest`。没有 `expected` 的测试点只运行，结论是 `RAN`，并带出输出与 stderr 摘要：

```json
"trials": {
  "trial_ac":  { "source": "ac_iostream", "verdict": "AC",
                 "testcases": [ { "name": "sample", "input": "1 2", "expected": "3", "verdict": "AC" } ] },
  "trial_wa":  { "source": "ac_iostream", "verdict": "WA",
                 "testcases": [ { "name": "sample", "input": "1 2", "expected": "4", "verdict": "WA" } ] },
  "trial_ran": { "source": "ac_iostream", "verdict": "RAN",
                 "testcases": [ { "name": "sample", "input": "1 2", "verdict": "RAN", "output": { "contains": "3" } } ] }
}
```

| 字段 | 规则 |
|---|---|
| `source` / `sourceText` | 同 §3.3 |
| `limits` | 可选，缺省取自 `problem.json` |
| `verdict` | 整体结论，规则同 §3.1，`RAN` 排在 `PE` 与 `AC` 之间 |
| `testcases[]` | `name`、`input` 必填，`expected` 可选；`verdict` 是该点的预期结论 |
| `testcases[].output`、`testcases[].stderr` | 可选，对输出摘要、stderr 摘要的断言（`equals`、`startsWith`、`contains` 之一） |

## 4. requires：预期依赖节点的配置

有些预期只在特定配置下成立。题目、提交或样本可以带 `requires`，运行者按它把每一项归为**运行**、**跳过**或**失败**：

| 标签 | 含义 | 举例 |
|---|---|---|
| `backend:linux` | 有真实内核隔离，输出有硬上限 | OLE、MLE 类提交 |
| `strictWhitespace:true` | 节点按严格空白判 PE | 多余空行应判 PE 的提交 |
| `entry:sandbox` | 运行者能直接调用执行器，且独占 box（§7.2） | 所有 `sandbox` 入口的运行说明 |
| `noLanguage:<语言>` | 节点没有这种语言的工具链（运行者在 `languages` 里声明了有哪些） | 向只有 C/C++ 的节点提交 python 应得到 SE |

- 标签的值**由运行者声明**并写入报告，运行者不去猜测节点的配置。
- 声明满足 `requires` 才运行；不满足则**跳过**，报告里写明原因；跳过不算失败。
- 没有真实内核隔离的后端（开发用的宿主后端）的结果**永远不作为隔离证据**。
- 一个提交或样本如果有 `sandbox` 入口的运行说明，该入口自动要求 `entry:sandbox` 与 `backend:linux`，不必再写。

## 5. 攻击样本

`attack/<名>/` 放试图越过限制的样本，每个样本一个目录，格式如下：

```text
attack/<名>/
├── sample.cpp               样本源码
└── manifest.json
```

```json
{ "schemaVersion": 1, "name": "<名>", "language": "cpp",
  "limits": { "cpuNs": 1000000000, "memoryBytes": 268435456, "clockNs": 3000000000 },
  "judge":   { "verdict": "TLE" },
  "sandbox": [ { "facts": { "reason": "cpu" } } ],
  "repeat":  { "judge": 1, "sandbox": 1 },
  "requires": ["backend:linux"], "notes": "…" }
```

- `judge` 是 `judge` 入口的预期：一个具体 verdict，或 `{ "verdictNot": "AC" }` 表示「只要不是 AC」。可以省略，表示该样本不从 `judge` 入口测试。
- `sandbox` 是 `sandbox` 入口的运行说明数组（§6）。可以省略，表示该样本不从 `sandbox` 入口测试。两个入口至少要有一个。
- `repeat` 可选，每个入口把同一次调用连续执行的遍数，缺省 1。用来发现泄漏（§7.3）。
- 样本没有测试数据：从 `judge` 入口提交时，使用语料根下共用的 `attack-common/testdata/`（一个测试点，输入为空，期望输出是固定的一行 `OK`）；样本绝不会输出它，所以「不是 AC」是有意义的断言。
- 每个样本跑完后做**回收检查**和**干净运行**（§8，两个入口都做）。
- 报告必须写出攻击样本数量。数量为 0 时写明「攻击样本：0，未覆盖沙箱安全」，**不得省略这一行**。
- 攻击样本不属于 `corpus.lock` 的范围；样本的完整性由使用者自己管理。

## 6. sandbox 入口的运行说明

`sandbox` 入口不经过 judge：运行者自己准备 box，调用执行器，读执行器的事实 JSON 与 `out/` 里的文件，与运行说明比对。
一个运行说明描述「用什么输入运行一次，应当看到什么」：

```json
{ "testcase": "1",
  "limits": { "cpuNs": 500000000 },
  "facts": { "reason": "cpu", "cpuNs": { "ge": 500000000 }, "clockNs": { "lt": 2000000000 } },
  "stdout": { "startsWith": "ok" },
  "elapsedMsLt": 2000 }
```

两次运行必须同时进行时（例如检验两个并发执行互相看不到对方），用 `id` 与 `concurrent` 把它们配成一组：

```json
"sandbox": [
  { "id": "left",  "stdin": "isolation left ${runnerPid}",  "concurrent": ["right"],
    "facts": { "exitCode": 0 }, "stdout": { "equals": "private left peers=0 host=hidden\n" } },
  { "id": "right", "stdin": "isolation right ${runnerPid}", "concurrent": ["left"],
    "facts": { "exitCode": 0 }, "stdout": { "equals": "private right peers=0 host=hidden\n" } } ]
```

| 字段 | 规则 |
|---|---|
| `id` | 可选，同一个提交或样本内唯一，匹配 `^[a-z0-9][a-z0-9_]{0,31}$`；被 `concurrent` 引用的运行说明必须有 |
| `testcase` | 可选，该提交所在题目的测试点名字，其 `.in` 作为 stdin。攻击样本没有测试点，不能用 |
| `stdin` | 可选，字符串，直接作为 stdin；与 `testcase` 互斥。两者都省略则 stdin 为空。其中的 `${runnerPid}` 由运行者替换为运行者自己的进程号 |
| `limits` | 可选，覆盖 `limits` 中的同名字段；未给的字段取自题目或样本的 `limits`。`maxProcesses`、`stdoutMaxBytes`、`stderrMaxBytes` 可以在这里给，缺省为 64、1 MiB、1 MiB |
| `facts` | 对执行器**事实 JSON**（[执行器协议 §7.2](./sandbox-executor.md)）字段的断言，只能用那里列出的字段名 |
| `stdout` | 可选，对 `out/stdout` 的断言：`equals`、`startsWith` 或 `contains` 之一 |
| `stderr` | 可选，对 `out/stderr` 的断言，写法同 `stdout`。用于证明 stdout 与 stderr 是两条独立的流 |
| `elapsedMsLt` | 可选，运行者观测到的这一次调用的总耗时（毫秒）必须小于它。用于证明「整组被及时回收，没有等后台进程自己退出」 |
| `concurrent` | 可选，`id` 数组，列出必须与本运行说明**同时运行**的其他运行说明（§7.2），关系必须对称 |
| `anyOf` | 可选，与 `facts` / `stdout` / `stderr` 互斥：由多个上述断言组成的数组，满足其中任意一个即可。用于「被 seccomp 杀死，或程序自己报告被拒」这类两种结果都合格的情形 |

**`facts` 的写法：** 每个字段的值是字面值（整数、字符串、布尔，要求相等），或下列比较之一：
`{ "gt": n }`、`{ "ge": n }`、`{ "lt": n }`、`{ "le": n }`、`{ "in": [ … ] }`。

**运行流程：**
1. **编译。** 与 judge 使用相同的编译命令（`cpp`：`g++ Main.cpp -o Main -O2 -std=c++17`），编译限制 CPU 10 s、墙钟 20 s、内存 256 MiB、进程 64 个（与官方安装的 judge 编译限制一致）；取回产物 `Main`。
   编译失败（`exitCode` 不为 0）且运行说明没有预期编译错误时，这一项记为 `fail`。
2. **运行。** 另用一个 box，把 `Main` 作为输入（可执行），命令为 `Main`，stdin 与限制取自运行说明。
3. **比对** `facts`、`stdout`、`elapsedMsLt`；**回收检查**。

编译与运行是执行器协议里的两次独立调用，运行者必须保持每次的 stdin 管道到执行器退出（取消协议）。同一个提交或样本的多个运行说明共用一次编译的产物。

## 7. 运行与比对

### 7.1 judge 入口

对每个提交：读源码，构造一次[判题请求](./judge-api.md)，同步等待结果，与预期逐项比对。

| 请求字段 | 取值 |
|---|---|
| `mode` | `submit` |
| `testDataLocation` | 该题 `testdata/` 发布到被测节点可读位置后的地址（遵守[测试数据协议](./testdata.md)） |
| `languageId` | 由提交扩展名决定 |
| `source` | 提交源码 |
| `limits` | 取自 `problem.json`；攻击样本取自 `manifest.json` |
| `submissionId`、`problemId` | 运行者生成，同一次运行内唯一 |

- **串行执行**，不要对同一节点并发提交。
- **发布位置必须让 judge 的运行身份能一路进得去**：任一级父目录缺少对其他用户的执行权限，读取就会失败并得到 SE，被误当作判题缺陷。
- **比对对象：** 整体 verdict、逐点 verdict、`testDataDigest`，以及 `expect.json` 写了的 `message`。耗时和内存只记录，不断言。
- **标准答案不外泄：** 声明的 `revealExpected` 为 `false`（默认）时，每个 `WA` 测试点的 `diff` 不得含 `want`；含则该提交记为 `fail`。声明为 `true` 时不检查。

### 7.2 sandbox 入口

- 运行者以 root 或执行器的服务身份运行，在被测节点本机直接调用执行器。
- **独占 box：** 运行者对 boxes 根下的 `.lock` 加独占锁，取不到就是 judge 正在用，记为环境错误（退出码 3），不得与运行中的 judge 争用同一批 box。
- 每个运行说明占用一个 box；调用前复位 box。没有 `concurrent` 的运行说明串行执行。
- **并发组：** 通过 `concurrent` 互相引用的运行说明组成一个组（关系必须对称，且组内每个 `id` 都存在）。组内各运行说明各占一个不同的 box，
  由运行者同时启动，等全部结束后再逐个比对。组的大小不得超过节点的 `box_count`，否则记为环境错误。
- **必须真的重叠：** 运行者记录组内每次调用（从执行器启动到退出）的起止时间；任意两次调用的时间区间没有交集，该组所有成员记为 `fail`，原因写「未重叠」。
  这是因为「看不到对方」只有在对方正在运行时才有意义。
- 组内任一成员的回收检查未通过，该成员记为 `fail`；成员之间互不影响各自的状态判定。
- 事实 JSON 解析时遵守执行器协议的版本规则：未知字段、未知 `version`、未知 `reason` 都是环境错误。
- 执行器退出码为 1（拒绝）或 2（回收未确认）都记为 `fail`；退出码 2 还要在报告里标出该 box 作废。

### 7.3 重复与状态

- **重复：** 时间、内存类提交（TLE、MLE、OLE 及其对照）默认重复 3 次，要求结论完全一致；不一致记为**不稳定**，不得随机通过。
  `judge` 入口比对 verdict，`sandbox` 入口比对 `facts` 的断言结果。
- **重复序列：** 样本的 `repeat` 要求把同一次调用连续执行多遍，用来发现泄漏（组、挂载、fd、内存）。所有遍的结论必须相同，序列结束后做一次回收检查和一次干净运行（§8）。
  它与上面的「重复 3 次」不同：后者测稳定性，前者测泄漏。

每个提交或样本在每个入口各有一个状态：

| 状态 | 含义 |
|---|---|
| `pass` | 与预期完全一致 |
| `fail` | 整体或某个测试点的结论与预期不符 |
| `unstable` | 重复运行的结论不一致 |
| `skipped` | `requires` 不满足 |
| `probe` | 探测项，只记录 |

## 8. 回收检查

两个入口都要做，每个攻击样本跑完后检查被测节点：

| 检查项 | 通过条件 |
|---|---|
| 任务进程 | 没有属于 payload 与 init 身份的进程 |
| 执行 cgroup | judge 服务的 `jobs/` 下没有遗留的 `box-*` 组 |
| 工作区 | 各 box 的 `in/`、`out/` 为空 |
| 干净运行 | 回收检查之后立即运行 `attack-common/probe.cpp`（只输出 `OK`）：`judge` 入口 verdict 为 `AC` 且内存峰值小于 16 MiB；`sandbox` 入口 `exitCode` 为 0、`reason` 与 `error` 为空、`memoryBytes` 小于 16 MiB |

「干净运行」证明前一个样本没有污染后一次执行：不继承它的内存峰值，不留下状态。四项都通过才算通过；未通过则记为 `fail`，即使结论与预期一致。

## 9. 退出码

| 码 | 含义 |
|---|---|
| 0 | 所有运行的项均为 `pass`（`skipped`、`probe` 不影响） |
| 1 | 至少一项 `fail` |
| 2 | 没有 `fail`，但至少一项 `unstable` |
| 3 | 环境或配置错误：连不上节点、语料校验失败、`corpus.lock` 不符、`sandbox` 入口取不到 box 锁 |

## 10. 报告

机器可读，一次运行一个 JSON：

```json
{
  "schemaVersion": 1,
  "startedAt": "2026-10-13T10:00:00Z", "finishedAt": "2026-10-13T10:05:00Z",
  "environment": {
    "endpoint": "http://127.0.0.1:15051",
    "judgeVersion": "…",
    "declared": { "backend": "linux", "strictWhitespace": false, "revealExpected": false,
                  "languages": ["cpp"], "entries": ["judge", "sandbox"] },
    "kernel": null, "gitSha": "…", "corpusDigest": "…", "rootfsManifestSha256": null
  },
  "summary": {
    "judge":  { "ran": 52, "passed": 50, "failed": 1, "unstable": 1, "skipped": 4 },
    "attack": { "samples": 25, "ran": 25, "passed": 25, "failed": 0, "unstable": 0, "skipped": 0 } },
  "judge": [
    { "name": "a-plus-b", "digest": "…", "submissions": [
      { "name": "ac_iostream", "entry": "judge", "status": "pass", "expected": "AC", "actual": "AC", "repeats": 1,
        "testcases": [ { "idx": 1, "expected": "AC", "actual": "AC", "cpuNs": 1200000, "memoryBytes": 1048576 } ] } ] } ],
  "attack": [
    { "name": "<名>", "results": [
      { "entry": "judge",   "status": "pass", "expected": "TLE", "actual": "TLE",
        "residue": { "processes": 0, "cgroups": 0, "workFiles": 0, "cleanProbe": "pass" } },
      { "entry": "sandbox", "status": "pass", "expected": { "reason": "cpu" }, "actual": { "reason": "cpu", "cpuNs": 504000000 },
        "residue": { "processes": 0, "cgroups": 0, "workFiles": 0, "cleanProbe": "pass" } } ] } ]
}
```

- `judge` 列出判题部分的题目，`attack` 列出攻击样本，每个样本在每个运行过的入口有一条结果；`residue` 是回收检查的结果，三项都必须为 0、`cleanProbe` 必须为 `pass` 才算 `pass`。
- `environment.declared` 是运行者声明的配置（§4），必须写入报告，包括能运行的入口 `entries`。
- `kernel`、`rootfsManifestSha256` 等节点侧信息由运行者提供，**没有就写 `null`，不猜**。
- `fail` 的项带差异：预期与实际并排，指出哪个测试点或哪个事实字段不同。报告**不输出隐藏数据的正文**，只输出名字、verdict、事实字段和长度。
- 人读汇总必须包含：环境声明、跳过项及原因、失败项的差异、攻击样本数。

## 11. 版本与兼容

- 本协议的各个 JSON 文件都带 `schemaVersion`，目前均为 `1`。读取方遇到不认识的版本、未知字段，一律拒绝。
- 改动字段、取值或比对规则，需要同时修改语料生成步骤、运行者、报告消费方和相关测试，并升级对应的 `schemaVersion`。
- 不在本协议范围内：压力与性能比较、攻击样本的内容、判题服务之外的链路（Java 服务、Kafka、前端），包括这些服务对隐藏数据的展示投影。
