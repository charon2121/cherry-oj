# Python 编码规范（`scripts/`、`deploy/`、各服务 `scripts/`）

> 本项目跨语言约定见 [`project-conventions.md`](../project-conventions.md)，通用编码指令见
> [`general.md`](../general.md)。

本仓库的 Python 不是业务代码，而是**工具代码**：`scripts/work`（工作项与文档管理）、
`scripts/*_test.py`（文档与契约校验）、`deploy/sandbox-linux/ci/*.py`（沙箱镜像与 CI 流程）、
各服务的 `scripts/*.py`。它们在 CI 里被直接调用，坏了会挡住所有人，所以约束比一般脚本严。

## 1. 零第三方依赖

**只用标准库。** 仓库里没有 `requirements.txt`、没有 `pyproject.toml`、没有虚拟环境，CI 直接
`python3 xxx.py` 就跑。

这是刻意的：这些脚本的价值在于「clone 下来就能跑」。一旦引入 pip 依赖，就要同时解决版本锁定、
安装步骤、CI 缓存和跨平台差异，而它们解决的问题（读 Markdown、拼 JSON、跑子进程）标准库全都够用。

确实需要第三方库时，先问能不能换个做法；不能就单独提出来讨论，不要顺手 `pip install`。

## 2. 文件骨架

每个可执行脚本都长这样：

```python
#!/usr/bin/env python3
"""一句话说明这个脚本干什么。"""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
```

- **shebang + 中文模块 docstring**，一句话说清用途。
- **`from __future__ import annotations` 必写**，放在所有 import 之前。
- 标准库 import 按 `import x` / `from x import y` 分组，各自字母序。
- 模块级常量用 `UPPER_CASE`，**优先 tuple 而不是 list**——这些是配置不是数据，不该被改。

## 3. 类型注解

- **所有函数签名写完整注解**，包括返回值（`scripts/work` 里 86 个 `def` 全部有 `->`）。
  没有返回值写 `-> None`。
- 用 `dataclass` 表达记录类型，不要传一路 dict 再靠 key 拼写正确来维持不变量。
- `Any` 只用于确实无法表达的边界（解析完的 JSON），解析后尽快收敛到具体类型。

## 4. 路径与子进程

- **路径一律 `pathlib.Path`**，不用 `os.path`。
- 相对定位从 `Path(__file__).resolve().parents[n]` 出发，不依赖调用时的工作目录。
- **外部字符串拼进路径前先用正则关死**，理由见 [`project-conventions.md`](../project-conventions.md) §1.5——
  这条在 Go 侧已经踩过三次，Python 侧同样成立。
- 子进程用 `subprocess` 的列表形式传参，**不要 `shell=True` 拼字符串**。

## 5. 测试

- **测试文件命名 `*_test.py`，不是 `test_*.py`。** 这是本仓库的约定（和 Go 一致），
  `work_test.py`、`docs_test.py`、`contracts_test.py` 都如此。
- **用标准库 `unittest`，不用 pytest**（见 §1）。测试类继承 `unittest.TestCase`。
- 需要文件系统的测试用 `tempfile.TemporaryDirectory()` 建临时根，从
  `development/templates` 复制所需骨架，**不要在真实仓库目录上跑写操作**。
- 校验类脚本（`docs_test.py`、`contracts_test.py`）本身也要有测试
  （`docs_test_test.py`），否则校验器静默失效时没人知道。

## 6. 输出与退出码

- 给人看的输出用中文，**明确说清哪里错了、错在第几行、期望是什么**——这些脚本的输出经常是
  CI 失败时唯一的线索。
- `main()` 返回 `int` 作为退出码，`sys.exit(main())` 收尾。成功 0，有错非 0。
- 成功路径也要有一行确认输出（例如 `✓ 554 份 Markdown 文档入口和本地链接有效`），
  否则分不清「检查通过」和「检查根本没跑」。
