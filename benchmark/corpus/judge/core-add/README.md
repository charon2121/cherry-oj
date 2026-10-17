# core-add：核心代码模式

读两个整数，输出和；但提交的不是完整程序，而是**用户文件**。题目目录里的 `driver.cpp` 是驱动文件：judge 把它写成 `Main.cpp`、
把提交写成 `solution.cpp`，一起编译（文件名见 `contracts/judge.schema.json` 的 `x-language-files`）。每个提交开头的两行
`#include <bits/stdc++.h>` 与 `using namespace std;` 是平台给 C++ 加的前缀——正式链路里由 Java 服务合并，这里直接写进提交，
因为 benchmark 检验的是 judge 收到什么、怎么判。

这道题检验：驱动文件与用户文件按约定的文件名一起编译；用户文件与驱动文件的报错位置都**保持编译器的原始输出**
（行号换算是 judging-service 的事，judge 不改写）；不支持 CORE 的语言收到用户文件时判 SE。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `3 5` → `8` |
| `2` | `-4 -6` → `-10` |
| `3` | `0 0` → `0` |

## 提交

| 提交 | 整体 | 逐点 | 为什么存在 |
|---|---|---|---|
| `ac_class` | AC | AC AC AC | 基线：用户文件只写 `class Solution`，不写 `main` |
| `wa_minus` | WA | WA WA AC | 输出 `a - b` |
| `ce_solution` | CE | （空） | 用户文件第 5 行引用未定义的名字：诊断必须是原始位置 `solution.cpp:5:`（含前缀的两行） |
| `ce_signature` | CE | （空） | `add` 少一个参数，错误出在驱动文件的调用处：诊断必须指向 `Main.cpp:8:` |
| `re_abort` | RE | RE RE RE | 用户函数里 `abort()` |

`requests` 的 `solution_unsupported_language` 把用户文件发给不支持 CORE 的 `python`：judge 必须判 SE，而不是忽略用户文件。
`trials` 覆盖 CORE 的试运行。

## 数据

与 a-plus-b 相同：`oracle/gen.py` 生成输入，`solution_a.py` 与 `solution_b.py` 各自独立算出期望输出。
重新生成：`python3 benchmark/tools/generate.py --problem core-add`。
