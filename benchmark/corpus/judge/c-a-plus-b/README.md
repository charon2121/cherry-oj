# c-a-plus-b：C 语言的基线链路与编译选项

读两个整数，输出和；测试点与 `a-plus-b` 相同（`3 5`、`-4 -6`、`0 0`）。这道题检验 C 的整条链路，并钉住两条编译选项：
judge 用 `gcc Main.c -o Main -O2 -std=gnu17 -lm` 编译（见 `apps/judge-engine/judge/language`）。

## 提交

| 提交 | 整体 | 逐点 | 为什么存在 |
|---|---|---|---|
| `ac_scanf` | AC | AC AC AC | 基线；sandbox 入口直接核对第 1 点的输出 |
| `ac_math` | AC | AC AC AC | 用到 `sqrt`：少了 `-lm` 会链接失败 |
| `ac_gnu_typeof` | AC | AC AC AC | 用到 `typeof`：`-std=gnu17` 能编译，严格的 `-std=c11` 不能 |
| `wa_minus` | WA | WA WA AC | 输出 `a - b`；第 3 个点 `0 - 0` 恰好等于 `0 + 0` |
| `ce_syntax` | CE | （空） | 缺分号，没有逐点结果 |
| `re_segv` | RE | RE RE RE | 写空指针 |
| `tle_loop` | TLE | TLE TLE TLE | 死循环 |
