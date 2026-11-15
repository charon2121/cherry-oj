# 语言运行约定

规定各语言在题目配置、提交冻结、judge 与工具链之间如何对齐。字段真源为
[`languages.json`](../../contracts/languages.json)；本文规定运行与失败语义。显示名与前缀代码仍由
problem-service 的语言表拥有，不在契约里复制产品字段。

## 快速使用

在已安装 `pypy` 工具链且通过启动冒烟的节点上：

```sh
curl -sS -X POST http://127.0.0.1:15051/judge -H 'Content-Type: application/json' -d '{
  "submissionId":"pypy-trial", "problemId":"a-plus-b", "mode":"trial", "languageId":"pypy",
  "source":"a,b=map(int,input().split())\nprint(a+b)\n",
  "testcases":[{"input":"1 2\n","expected":"3\n"}],
  "limits":{"cpuNs":1000000000,"memoryBytes":268435456,"clockNs":10000000000}
}'
```

成功为 HTTP 200、`verdict: AC`；试运行不提供 expected 时为 `RAN`。这不绕过平台提交入口的就绪检查。

## 1. 参与方与传输

problem-service 管理语言显示名、前缀与题目驱动/模板；submission-service 按现有规则冻结源码、
用户文件、前缀行数与绝对限制；judging-service 用目录计算预算并处理允许公开的报错；
Go judge 根据目录对应的语言注册选择命令和文件名，经执行器在独立工具链中执行。
HTTP、冻结字段与执行器请求继续使用既有协议，不新增请求字段。

## 2. PyPy 的身份与运行时

- 语言 id 为 `pypy`，建议初始显示名 `Python 3 (PyPy)`；不与开发用 `python` 注册混同，也不以版本号作为语言 id。
- 首个官方工具链锁定 PyPy **8.0.1 / Python 3.11.17 / Linux amd64**，保留 JIT 默认启用。
  官方完整归档、摘要、大小与安装方式见[包锁 v2](../../deploy/sandbox-linux/rootfs/README.md#包锁-v2-与运行时归档)。
  不使用 Python 3.12 beta，不在节点上在线安装 pip 包；仅提供该锁定分发自带的标准库与模块。
- `requiresCompilation: false`，没有平台编译阶段，不生成或缓存字节码；命令为
  `pypy3 -E -s -B Main.py`，解释器从该工具链的 PATH 查找。默认 JIT、GC 与语言语义不按题目调整。
  `-E` 忽略 Python 环境变量，`-s` 关闭用户 site 目录，`-B` 禁止写入 pyc；保留脚本目录供 CORE 导入用户文件。
  运行环境固定 `PYPY_GC_NURSERY=4MB`（4MiB）：与受限 `/proc` 下的默认回退值相同，避免运行时为探测缓存写出警告；
  不按题目或题目内存限制改变它，其他语言不继承此变量，也不放宽 `/proc` 或系统调用权限。
- 每个测试点启动全新进程，运行时加载、语法解析、JIT 和 GC 都计入该测试点的 CPU 与内存，测试点之间不共享预热状态。

## 3. ACM 与 CORE

`files.driverFile = Main.py`，`files.solutionFile = solution.py`。
ACM 的 source 是完整 Main.py，管理员与用户自行写 import；CORE 的 source 是题目驱动 Main.py，
solutionSource 是按既有规则拼接的全局前缀与用户文本，写成 solution.py。初始全局前缀为 `import sys\n`。
驱动通过 `from solution import Solution` 等普通 Python 导入引用它；不解析函数签名，不做缩进或 AST 改写。
用户文件必须自行满足 Python 语法，前缀行数沿用冻结和报错换算规则。

例如驱动读取两个整数，调用用户类的 `add`：

```python
from solution import Solution

a, b = map(int, input().split())
print(Solution().add(a, b))
```

用户编辑区的起始代码：

```python
class Solution:
    def add(self, a: int, b: int) -> int:
        return a + b
```

正式判题和自测使用相同文件组织，不把 Java 或 Go 宿主目录加入 Python 搜索路径。

## 4. 结论、报错与预算

- 因无独立编译阶段，语法错误、导入失败、未捕获异常和其他非零退出都按既有运行事实得到 `RE`，不新增 CE 预检阶段。
  TLE、MLE、OLE 继续按现有执行事实判定；运行时缺失、启动冒烟失败或执行器故障属于平台问题，不能伪装成用户 RE。
- 自测的 RE 对应 `RUNTIME_ERROR`，stderr 中 solution.py 的位置按冻结前缀行数换算成编辑器行号，
  路径和控制字符沿用现有清理。标准输出保持原文。正式提交只返回已有安全结果，不公开隐藏测试的 traceback、输入或输出。
- 预算不包含 compileBudget，只包含逐测试点的墙钟与开销；自测仍遵守现有预算上限和全链路期限。
- 初次接入时目录不配置 `limitModel`，只允许覆盖标定开放。真实节点完成启动、计算、IO 与内存样本拟合后，
  再按[限制推导协议](./limits.md)补充参数并验证 BASE / DERIVED / OVERRIDE。CodeNet 候选值只作实验起点，
  不直接作为上线参数；PyPy 补偿上限仍为不设上限。参数取值不改变既有公式或冻结语义。

## 5. 版本、兼容与失败

目录保持 `x-contract-version: 2`，这里只新增语言项，既有 C/C++ 字段与行为不变。
新语言必须同时具备目录、产品语言记录、judge 注册和通过冒烟的独立工具链；节点仅按已安装并验证的工具链声明语言。
未完成这些条件或没有有效限制时，该语言不能进入题目的就绪作答列表；未知语言不回退到其他解释器。
包锁 v2 的兼容约定以 rootfs README 为准；新增语言不重写历史 JudgeInput、旧锁或旧发布。
本协议的参与方都需对齐后才能开放该语言。字段、命令语义或失败分类改变须重新审核。

## 6. 依据与实测记录

2026-11-15，官方 [8.0.1 发布说明](https://doc.pypy.org/release-v8.0.1.html)与
[校验和](https://pypy.org/checksums.html)核对通过；下载归档 SHA-256 为
`706965bcf372752ba69dafd59d860e51bc2fadbb0f8a47cec3426125d7058ceb`，大小 37254555 bytes。
本机 Docker 的 Ubuntu 24.04 amd64 临时容器执行 --version 得到 Python 3.11.17 / PyPy 8.0.1，
`-E -s -B` 对应选项生效；该检查只证明分发和启动参数，不等于原生执行器安全验证或性能标定。
[官方安装要求](https://pypy.org/download_advanced.html#installing)是保留整个运行时目录，命令通过符号链接接入。

同日，测试服务器的正式执行器验证标准库导入、JIT 实际编译、CORE 导入、语法错误、TLE、MLE、OLE 与
网络系统调用拒绝共 9 项通过。正常程序 stderr 为空，Go 返回原始用户文件行号；
节点的既有 namespace、只读挂载、能力清空、no-new-privileges 与资源回收探针通过。
受限 `/proc` 下默认 nursery 与显式 `4MB` 均实测为 4MiB，后者消除了探测 CPU 缓存产生的警告。
限制参数的对照范围见[限制协议的实测记录](./limits.md#11-实测记录)。
