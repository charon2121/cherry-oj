# a-plus-b：基线链路

读两个整数，输出和。这道题检验的是**最基本的一整条链路**：编译、运行、比对、逐测试点汇总，以及 CE、RE、TLE 和 SE 几条路径。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `3 5` → `8` |
| `2` | `-4 -6` → `-10` |
| `3` | `0 0` → `0` |

## 提交

| 提交 | 整体 | 逐点 | 为什么存在 |
|---|---|---|---|
| `ac_iostream` | AC | AC AC AC | 基线 |
| `ac_scanf` | AC | AC AC AC | C 风格 IO 同样通过 |
| `ac_stderr_note` | AC | AC AC AC | stderr 不影响判定 |
| `wa_minus` | WA | WA WA AC | 输出 `a - b`；第 3 个点 `0 - 0` 恰好等于 `0 + 0` |
| `ce_syntax` | CE | （空） | 缺分号，没有逐点结果 |
| `ce_marker` | CE | （空） | 诊断必须带出预设标记，且不能进入执行阶段 |
| `re_exit1` | RE | RE RE RE | 非零退出 |
| `re_abort` | RE | RE RE RE | 被 SIGABRT 终止 |
| `re_segv` | RE | RE RE RE | 被 SIGSEGV 终止 |
| `tle_loop` | TLE | TLE TLE TLE | 死循环，远超 CPU 限制 |

`requests` 钉住 SE 的路径（地址不存在、相对路径、发布副本被篡改、未知语言、节点没有的语言）；`trials` 覆盖试运行（AC、WA、不给期望的 RAN）。

## 数据

`oracle/gen.py` 生成输入；`solution_a.py`（整数加法）与 `solution_b.py`（十进制字符串竖式加减）各自独立算出期望输出，两者必须逐字节一致。
重新生成：`python3 benchmark/tools/generate.py --problem a-plus-b`。
