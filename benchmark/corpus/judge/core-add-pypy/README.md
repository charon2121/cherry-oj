# core-add-pypy

驱动从 `solution.py` 导入 `Solution`，读取两个整数并调用 `add`。测试数据和两份参考解复用 `a-plus-b`。

| 提交 | 整体 | 逐点 | 检查内容 |
|---|---|---|---|
| `ac_add` | AC | AC AC AC | 导入用户文件并调用方法 |
| `wa_minus` | WA | WA WA AC | 相减只在两个输入都为零时正确 |
| `re_solution` | RE | RE RE RE | 导入时的语法错误 |
| `re_signature` | RE | RE RE RE | 命中驱动传参和用户方法签名不匹配 |
| `re_exception` | RE | RE RE RE | 用户方法抛出未捕获异常 |

试运行检查正确结果，以及语法错误、签名错误和用户异常在 stderr 中的原始文件名与行号。
