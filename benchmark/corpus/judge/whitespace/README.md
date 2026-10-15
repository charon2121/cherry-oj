# whitespace：比对对空白的处理

读两个整数，输出和；标准答案是 `N\n`。judge 的比对只看 token：**空白不同永远不会判成 WA**，节点配置 `strictWhitespace` 为 `false`（默认）时判 AC，为 `true` 时判 PE。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `1 2` → `3` |
| `2` | `10 20` → `30` |

| 提交 | 排版 | 需要的配置 | 整体 | 为什么存在 |
|---|---|---|---|---|
| `ac_exact` | `N\n` | 无 | AC | 基线 |
| `ws_trailing_space_*` | `N \n` | `_loose` 需要 `strictWhitespace:false`，`_strict` 需要 `true` | AC / PE | 行尾空格 |
| `ws_no_final_newline_*` | `N` | 同上 | AC / PE | 缺末尾换行 |
| `ws_crlf_*` | `N\r\n` | 同上 | AC / PE | Windows 换行是排版差异，不是内容差异 |
| `ws_extra_blank_*` | `N\n\n` | 同上 | AC / PE | 多一个空行 |
| `wa_token` | `N+1` | 无 | WA | token 不同才是 WA |

同一排版的 `_loose` 与 `_strict` 两个文件**源码逐字节相同**（有单测守着）：一个提交只有一个预期，所以靠 `requires` 区分。运行者声明的 `strictWhitespace` 决定哪一半运行、哪一半跳过。
