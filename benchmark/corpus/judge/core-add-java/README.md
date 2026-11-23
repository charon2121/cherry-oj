# core-add-java

驱动 `Main.java` 读取两个整数并调用 `Solution.java` 的 `add`。测试数据和两份参考解复用 `a-plus-b`。

| 提交 | 整体 | 逐点 | 检查内容 |
|---|---|---|---|
| `ac_add` | AC | AC AC AC | 打包并运行用户类、嵌套 record 和匿名类 |
| `wa_minus` | WA | WA WA AC | 相减只在两个输入都为零时正确 |
| `ce_solution` | CE | 空 | 用户文件缺分号，错误指向 `Solution.java` |
| `ce_signature` | CE | 空 | 方法签名不匹配，错误指向 `Main.java` |
| `re_exception` | RE | RE RE RE | 用户方法抛出未捕获异常 |

两条试运行分别检查正确结果、用户异常在 stderr 中的原始 `Solution.java` 行号。
