# 统一改写的样例

[multi-file-languages.md](../multi-file-languages.md) §7 的原始材料。每门语言的用户文件前加了 3 行前缀，并在用户代码的某一行引用未定义的 `missing`：

- `<语言>.raw.txt`：编译器或解释器的原始输出（容器工作目录为 `/w`）；
- `<语言>.rewritten.txt`：`python3 rewrite.py <用户文件名> 3 < <语言>.raw.txt` 的结果；
- `rewrite.py`：不区分语言的改写器原型，只用于验证思路，不是生产实现。

judging-service 的改写模块上线前，用这些样例做固定测试（design.md §5.3、§6.4）。
