# core-add-c：C 语言的核心代码模式

与 `core-add` 相同的题与测试点，换成 C：judge 把 `driver.c` 写成 `Main.c`，把提交写成 `solution.c`（文件名见
`contracts/languages.json` 的 `files`），驱动文件用 `#include "solution.c"` 引用它，只编译 `Main.c`。每个提交开头的四行
是平台为 C 配的前缀（`stdio.h`、`stdlib.h`、`string.h`、`math.h`），judge 收到的用户文件就是这样。

## 提交

| 提交 | 整体 | 逐点 | 为什么存在 |
|---|---|---|---|
| `ac_func` | AC | AC AC AC | 基线 |
| `wa_minus` | WA | WA WA AC | 返回 `a - b` |
| `ce_solution` | CE | （空） | 用户文件第 5 行引用未定义的名字：报错必须落在 `solution.c:5:`，judging-service 据此换算成编辑器行号 |
| `ce_signature` | CE | （空） | 签名少一个参数：报错落在驱动文件调用处 `Main.c:8:` |
| `re_abort` | RE | RE RE RE | 用户函数里 `abort()` |
