# partial：逐测试点独立判定与汇总

读两个整数，输出和；6 个测试点，后三个的 `|a| ≥ 10^6`（供 `pass_half_tle_rest` 使用）。**一个测试点的结论不影响其余点**：前面错了、崩了，后面照常运行。

| 测试点 | 输入 |
|---|---|
| `1` | `1 1` |
| `2` | `2 3` |
| `3` | `10 20` |
| `4` | `1000000 2000000` |
| `5` | `-5000000 7000000` |
| `6` | `123456789 987654321` |

| 提交 | 整体 | 逐点 | 为什么存在 |
|---|---|---|---|
| `all_pass` | AC | AC×6 | 基线 |
| `fail_first_only` | WA | WA AC AC AC AC AC | 第 1 个点错了，其余照跑 |
| `fail_last_only` | WA | AC AC AC AC AC WA | 错误点在末尾 |
| `re_middle` | RE | AC AC RE AC AC AC | 中间崩溃不影响其余点 |
| `pass_half_tle_rest` | TLE | AC AC AC TLE TLE TLE | 部分通过的汇总 |
