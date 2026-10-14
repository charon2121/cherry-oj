# 判题样本清单

判题 benchmark 的题目与提交清单。每个提交的整体结论和逐测试点结论都在这里定下，之后生成 `expect.json` 以此为准。
格式见[判题 benchmark 协议](../../../docs/protocols/benchmark.md)，方案与取舍见 [design.md](./design.md)。只用 C/C++。

## 总览

| 题 | 检验什么 | 测试点数 | 提交数 | 需要的配置 |
|---|---|---|---|---|
| `a-plus-b` | 基线链路，及 CE/RE/TLE/SE | 3 | 7 | 无 |
| `whitespace` | 比对对空白的处理 | 2 | 10 | 部分需 `strictWhitespace` |
| `sort-numbers` | 中等规模输入输出与时间 | 5 | 4 | 无 |
| `sum-large` | 64 位整数 | 4 | 3 | 无 |
| `alloc-array` | 内存限制 | 3 | 4 | `backend:linux` |
| `big-output` | 输出上限 | 2 | 3 | `backend:linux` |
| `empty-case` | 空输入与空输出 | 2 | 3 | 无 |
| `partial` | 逐测试点独立判定与汇总 | 6 | 5 | 无 |

**verdict 覆盖：** AC、WA、PE、CE、RE、TLE、MLE、OLE 各至少一个提交触发；SE 由 `a-plus-b` 的请求级用例触发。

**统一限制：** 除特别说明，`cpuNs` = 1 s，`memoryBytes` = 256 MiB，`clockNs` = 3 s。
**统一约束：** TLE 提交远超限制；MLE 提交申请并写满不少于 2 倍限制；合格提交峰值不超过限制的一半。

下文表中「逐点」按测试点顺序列出结论；整体结论是逐点按严重度取最坏者（SE > CE > RE > MLE > OLE > TLE > WA > PE > AC）。

---

## 1. a-plus-b：基线链路

读两个整数，输出和。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `3 5` → `8` |
| `2` | `-4 -6` → `-10` |
| `3` | `0 0` → `0` |

| 提交 | 做什么 | 整体 | 逐点 | 为什么 |
|---|---|---|---|---|
| `ac_iostream` | `cin`/`cout` 求和 | AC | AC AC AC | 基线 |
| `ac_scanf` | `scanf`/`printf` 求和 | AC | AC AC AC | C 风格 IO 同样通过 |
| `wa_minus` | 输出 `a - b` | WA | WA WA AC | 第 3 个点 `0-0` 恰好等于 `0+0` |
| `ce_syntax` | 缺分号 | CE | （空） | 编译失败没有逐点结果 |
| `re_exit1` | `return 1` | RE | RE RE RE | 非零退出 |
| `re_abort` | `abort()` | RE | RE RE RE | 被信号终止 |
| `tle_loop` | `while(1);` | TLE | TLE TLE TLE | 远超 CPU 限制 |

**请求级用例**（用 `ac_iostream` 做源码，只改请求）：

| 名字 | 改什么 | 结论 |
|---|---|---|
| `missing_address` | `testDataLocation` 指向不存在的目录 | SE |
| `relative_address` | `testDataLocation` 用相对路径 | SE |
| `tampered_input` | 在**发布副本**里改一个 `.in` 的内容，使 sha256 不符 | SE |

---

## 2. whitespace：比对对空白的处理

读两个整数，输出和。标准答案是 `N\n`。空白差异永远不会变成 WA：宽松判 AC，严格判 PE。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `1 2` → `3` |
| `2` | `10 20` → `30` |

| 提交 | 输出的排版 | 需要的配置 | 整体 | 逐点 |
|---|---|---|---|---|
| `ac_exact` | `N\n` | 无 | AC | AC AC |
| `ws_trailing_space_loose` | `N \n`（行尾空格） | `strictWhitespace:false` | AC | AC AC |
| `ws_trailing_space_strict` | 同上，同一份源码 | `strictWhitespace:true` | PE | PE PE |
| `ws_no_final_newline_loose` | `N`（缺末尾换行） | `strictWhitespace:false` | AC | AC AC |
| `ws_no_final_newline_strict` | 同上 | `strictWhitespace:true` | PE | PE PE |
| `ws_crlf_loose` | `N\r\n` | `strictWhitespace:false` | AC | AC AC |
| `ws_crlf_strict` | 同上 | `strictWhitespace:true` | PE | PE PE |
| `ws_extra_blank_loose` | `N\n\n`（多一个空行） | `strictWhitespace:false` | AC | AC AC |
| `ws_extra_blank_strict` | 同上 | `strictWhitespace:true` | PE | PE PE |
| `wa_token` | 输出 `N+1` | 无 | WA | WA WA |

- 一个提交只有一个预期，所以同一份排版要写两个提交文件（`_loose`、`_strict`），源码相同，靠 `requires` 区分。
- 运行者声明的 `strictWhitespace` 决定哪一半运行、哪一半跳过。

---

## 3. sort-numbers：中等规模输入输出与时间

读 `n` 和 `n` 个整数，按升序输出，空格分隔。

| 测试点 | 输入 |
|---|---|
| `1` | `n = 1` |
| `2` | `n = 10`，含重复 |
| `3` | `n = 1000`，互不相同、完全逆序 |
| `4` | `n = 10^5`，随机，取值范围小所以大量重复 |
| `5` | `n = 10^5`，全部相等 |

| 提交 | 做什么 | 整体 | 逐点 | 为什么 |
|---|---|---|---|---|
| `ac_std_sort` | `std::sort` | AC | AC×5 | 基线 |
| `ac_qsort` | C 的 `qsort` | AC | AC×5 | 另一种实现同样通过 |
| `tle_bubble` | 冒泡排序 | TLE | AC AC AC TLE TLE | `n = 10^3` 够快，`n = 10^5` 远超限制 |
| `wa_unique` | 排序后去重 | WA | AC WA AC WA WA | 只有含重复的点才错 |

---

## 4. sum-large：64 位整数

读 `n` 和 `n` 个 `long long`，输出总和（总和不超过 `long long`）。

| 测试点 | 输入特点 |
|---|---|
| `1` | 小数 |
| `2` | 总和超过 `2^31` |
| `3` | 总和接近 `2^63` |
| `4` | 含负数，总和在 `int` 范围内 |

| 提交 | 做什么 | 整体 | 逐点 | 为什么 |
|---|---|---|---|---|
| `ac_int64` | `long long` 累加 | AC | AC×4 | 基线 |
| `ac_int128` | `__int128` 累加，输出时转回 | AC | AC×4 | 更宽的类型同样通过 |
| `wa_int32` | 读入 `long long`，用 `int` 累加 | WA | AC WA WA AC | 只有总和越界的点才错 |

---

## 5. alloc-array：内存限制（需要 `backend:linux`）

读 `k`，分配 `k` MiB 并**写满每一页**，输出 `k`。内存限制 256 MiB。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `1` → `1` |
| `2` | `64` → `64` |
| `3` | `100` → `100` |

| 提交 | 做什么 | 整体 | 逐点 | 为什么 |
|---|---|---|---|---|
| `ac_malloc` | `malloc` 后写满 | AC | AC AC AC | 峰值不超过限制的一半 |
| `ac_vector` | `std::vector<char>` 写满 | AC | AC AC AC | C++ 容器同样通过 |
| `mle_big` | 忽略 `k`，写满 512 MiB | MLE | MLE MLE MLE | 远超限制 |
| `mle_last_only` | 只有 `k ≥ 100` 时把申请量放大到 6 倍 | MLE | AC AC MLE | 同一份提交逐点结论不同 |

内存只在有真实隔离的后端上可比，所以整题要求 `backend:linux`。

---

## 6. big-output：输出上限（需要 `backend:linux`）

读 `n`，输出 `1` 到 `n`，每个数一行。输出上限由执行器的 stdout 硬界（1 MiB）决定。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `n = 100`（约 0.3 KB） |
| `2` | `n = 50000`（约 0.3 MB，低于上限） |

| 提交 | 做什么 | 整体 | 逐点 | 为什么 |
|---|---|---|---|---|
| `ac_modest` | 按题输出 | AC | AC AC | 输出远小于上限 |
| `ole_flood` | 忽略 `n`，循环输出直到被终止 | OLE | OLE OLE | 超过上限 |
| `probe_near_limit` | 输出约 1 MiB 减一点 | 探测项 | 只记录 | 不进断言，观察边界 |

---

## 7. empty-case：空输入与空输出

读若干整数，输出它们的和；**没有整数时什么都不输出**。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | 空文件 → 空文件 |
| `2` | 只有一个换行 → 空文件 |

| 提交 | 做什么 | 整体 | 逐点 | 为什么 |
|---|---|---|---|---|
| `ac_silent` | 什么都不输出 | AC | AC AC | 空对空 |
| `ac_reads_eof` | 读到 EOF，没有整数就不输出 | AC | AC AC | 正确处理空输入 |
| `wa_prints_text` | 输出 `0` | WA | WA WA | 期望是空，多输出就是错 |

这道题同时检验测试数据协议允许零字节文件。

---

## 8. partial：逐测试点独立判定与汇总

读两个整数，输出和；6 个测试点，难度递增（后三个输入较大，供 `pass_half_tle_rest` 使用）。

| 提交 | 做什么 | 整体 | 逐点 | 为什么 |
|---|---|---|---|---|
| `all_pass` | 全对 | AC | AC×6 | 基线 |
| `fail_first_only` | 只有第 1 个点算错 | WA | WA AC AC AC AC AC | 前面的点错了，后面的点也要照跑 |
| `fail_last_only` | 只有第 6 个点算错 | WA | AC AC AC AC AC WA | 错误点在末尾 |
| `re_middle` | 只在第 3 个点崩溃 | RE | AC AC RE AC AC AC | 中间崩溃不影响其余点 |
| `pass_half_tle_rest` | 前 3 个点正确，后 3 个点超时 | TLE | AC AC AC TLE TLE TLE | 部分通过的汇总 |

---

## 汇总

- 提交共 **39** 个：`a-plus-b` 7、`whitespace` 10、`sort-numbers` 4、`sum-large` 3、`alloc-array` 4、`big-output` 3（其中 1 个探测项）、`empty-case` 3、`partial` 5；另有 3 个请求级用例。
- 按配置分：无要求 24 个，`strictWhitespace` 相关 8 个，`backend:linux` 相关 7 个（含 1 个探测项）。
- 参考解：每题两份算法不同的参考解各自算出期望输出，逐字节一致才算合格（协议 §2.3）。

## 待确认

- `whitespace` 的 `_loose`/`_strict` 成对写法是否接受，还是改协议让一个提交支持按配置给两个预期。
- `big-output` 的 `probe_near_limit` 和 `ws_crlf_*` 是否保留：它们依赖节点对边界的具体处理，保留的话只作探测项更稳。
