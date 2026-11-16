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

目录保持 `x-contract-version: 2`，新增语言项不修改既有 C/C++ 的目录字段；
各语言共用的执行器策略与失败语义以 [sandbox 执行器协议](./sandbox-executor.md#8-用户程序看到的世界)为准。
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

## 7. Java 的身份、编译与运行

- 语言 id 为 `java`，初始显示名 `Java 21`，同时支持 ACM 与 CORE。
  首个官方工具链使用 Eclipse Temurin **21.0.13+9 / HotSpot / Linux amd64** 的完整 JDK；
  不启用预览特性、不提供在线依赖安装或第三方库。JIT 在运行阶段保持启用。
- 官方固定归档为
  `https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz`，
  大小 `207858352` bytes，SHA-256 为
  `d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c`。
  包锁 v2 的 `stripPrefix` 为 `jdk-21.0.13+9`，`target` 为 `usr/lib/java21`，
  `bin` 把 `java`、`javac`、`jar` 分别链接到该完整 JDK 的 `bin/` 中同名命令。
  保留完整运行库与许可证；既有归档解包上限与路径安全检查不变。
- `files.driverFile = Main.java`，`files.solutionFile = Solution.java`，`requiresCompilation = true`。
  ACM 提交完整的 Main.java；CORE 驱动为 Main.java，用户文本按既有前缀规则写入 Solution.java。
  两者均使用默认包，入口为 `Main.main(String[] args)`；用户自行声明 `class Solution`，不改写 AST 或自动套类。
  初始全局前缀为 `import java.io.*;\nimport java.util.*;\nimport java.math.*;\n`，仅在 CORE 拼接。
- 编译器使用 `javac -encoding UTF-8 --release 21 -proc:none -classpath . -sourcepath . -d classes Main.java`；
  javac 按普通 Java 源码查找规则编译驱动引用的 Solution.java。随后使用
  `jar -J-Djava.io.tmpdir=. --create --file Main.jar -C classes .` 打包全部生成的类，包括内部类、匿名类与用户类。
  jar 的临时归档与输出都在同一个工作挂载点，避开跨挂载移动时的复制和扩展属性处理；
  两者都计入既有工作区容量与 inode 限额，不新增临时目录或资源额度。`.` 指编译进程的当前工作目录，兼容本机开发工作区。
  两条命令由固定的 `sh -c` 脚本顺序执行；不插入用户提供的命令文本。
  唯一移交的编译产物为 Main.jar，运行命令为 `java -cp Main.jar Main`（含下述固定 JVM 选项）。
- Linux 工具链中的所有 JVM 固定 `-XX:ActiveProcessorCount=1 -XX:+UseSerialGC -Xms8m`、
  `-XX:-UseContainerSupport -XX:-UsePerfData -XX:-UseThreadPriorities -Djava.awt.headless=true`，
  避免依赖隔离环境未提供的宿主探测设施；CPU、内存仍由原执行器统一限制。
  固定 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`。
  编译阶段 javac 与 jar 均通过 `-J` 传入同一组 JVM 选项，另固定 `-J-XX:TieredStopAtLevel=1`；
  此编译器启动优化不影响生成的字节码，运行阶段不使用该选项。
- 每次编译或运行，JVM 的 `-Xmx` 为该阶段实际 `memoryBytes` 向下取整到 1024 bytes 的十进制字节数；
  编译阶段使用节点既有编译内存限制，运行阶段使用已冻结的题目内存限制。
  不按宿主内存推测，不额外增加题目限额，不另外扣除固定保留量。
  `-Xmx` 只约束堆；执行器按整组实际内存计量，堆、元空间、线程、JIT、GC 与运行库都计入同一限制。
  小到无法启动 JVM 的限制不自动提高，也不据此移除语言的可用状态。
- 每个测试点重新启动 JVM；类加载、启动、JIT 与 GC 全计入该测试点的 CPU 和内存，不共享预热状态。
  编译失败沿用 CE；非零运行退出或未捕获异常沿用 RE，包含 JVM 自身的 OutOfMemoryError。
  只有执行器的实际资源事实满足现有判定条件时才返回 MLE / TLE / OLE，不根据 stderr 文本猜测资源结论。
  CORE 的 Solution.java 诊断与异常行号按冻结前缀行数换算；正式提交仍不公开隐藏测试的报错内容。
- 自测预算包含已有的编译预算，不新增超时或预算字段。
  初次接入时目录不配置 `limitModel`，只能通过 OVERRIDE 开放；完成真实节点的启动、计算、IO 和内存对照后，
  再按限制协议更新参数并验证 BASE / DERIVED / OVERRIDE。现有 CodeNet 参数只作为拟合起点。
- 目录仍为版本 2，执行器请求与应用接口不增加字段；新增产品语言使用追加迁移，不改写已有迁移与历史提交。
  受影响的消费者为 problem-service、judging-service、submission-service、Web、Go judge、独立工具链构建与部署校验。
  只有具备目录、产品语言记录、对应 judge 注册、通过冒烟的独立工具链和有效限制时，题目才可向用户开放 Java。

CORE 示例驱动：

```java
import java.util.Scanner;
public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        System.out.println(new Solution().add(in.nextInt(), in.nextInt()));
    }
}
```

对应用户编辑区：

```java
class Solution {
    public int add(int a, int b) {
        return a + b;
    }
}
```

版本来源为 [Temurin 官方发布](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.13%2B9)；
选项依据为 [Java 21 启动器规范](https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html)与
[javac 规范](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html)。

2026-11-16，测试服务器 Ubuntu 24.04 / Linux 6.8 / amd64 的正式执行器与独立 Java rootfs
通过启动冒烟和 12 项 Java 运行时验证：标准库、JIT 实际编译、CORE 内部类与匿名类、UTF-8 stderr、
编译诊断、CORE 编译与运行行号、TLE、JVM 内存异常 RE、实际内存限制 MLE、OLE 与网络拒绝。
正常程序无探测警告；jar 仅在当前编译工作目录创建临时归档。
同一节点通过 PyPy 9 项、既有判题 72 项、攻击样本 56 项，以及隔离与资源回收探针。
参数对照及适用范围见[限制协议的实测记录](./limits.md#11-实测记录)；这不代表正式安全验收或应用发布已完成。
