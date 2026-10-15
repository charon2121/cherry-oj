# alloc-array：内存限制（需要 `backend:linux`）

读 `k`，分配 `k` MiB 并**写满每一页**，输出 `k`。内存限制 256 MiB。内存只在有真实隔离的后端上可比，所以整题要求 `backend:linux`。

| 测试点 | 输入 → 期望输出 |
|---|---|
| `1` | `1` → `1` |
| `2` | `64` → `64` |
| `3` | `100` → `100` |

| 提交 | 整体 | 逐点 | 为什么存在 |
|---|---|---|---|
| `ac_malloc` | AC | AC AC AC | `malloc` 后每页写一字节；峰值不超过限制的一半 |
| `ac_vector` | AC | AC AC AC | C++ 容器同样通过 |
| `mle_big` | MLE | MLE MLE MLE | 忽略输入，写满 512 MiB（两倍于限制） |
| `mle_last_only` | MLE | AC AC MLE | `k ≥ 100` 时申请放大到 600 MiB：同一份提交逐点结论不同 |
