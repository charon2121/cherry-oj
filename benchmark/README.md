# benchmark

针对 judge 与 sandbox 的正确性和安全性测试库：测试代码，加上测试所需的「用户提交的代码」。
格式与运行结果的含义见 [benchmark 协议](../docs/protocols/benchmark.md)，设计与里程碑见
[设计文档](../development/works/2026-10-08-judge-benchmark/design.md)。

**目前是 M2：`judge` 入口，8 道题、42 个提交。** `sandbox` 入口和攻击样本在后面的里程碑。

## 运行

被测机器上必须已经有一个在运行的 judge 服务；脚本不负责安装或启动它。judge 启动时要先核对 rootfs，需要几秒钟，请等 `GET /version` 能答了再运行。

```sh
python3 benchmark/run.py --judge-url http://127.0.0.1:5051 --published-root /srv/cherry-benchmark \
        --declare backend=linux languages=cpp
```

- `--published-root`：测试数据的发布位置，必须是绝对路径，**judge 的运行身份要能一路进得去**（目录 0755）。脚本发布后会检查并给出警告。
- `--declare`：运行者声明的节点配置：`backend`、`strictWhitespace`、`revealExpected`、`languages`。**没有声明的项就是未知**，依赖它的项会被跳过，脚本不去猜。
  需要真实内核隔离的项要求声明 `backend=linux`；在开发用的无隔离后端上，结果不作为隔离证据。
- `--select a-plus-b`：只跑指定的题。`--repeat N`：时间、内存类提交的重复遍数（默认 3）。
- 报告写到 `benchmark/reports/<时间>-<主机>.json`（不入库）；屏幕上打印人读的汇总。

`whitespace` 的结论取决于节点的 `strictWhitespace` 配置：每个排版有 `_loose` 与 `_strict` 两个源码相同的提交，声明的配置决定哪一半运行、哪一半跳过。
要把两半都跑到，需要对两种配置的 judge 各运行一次（`--select whitespace` 只跑这一题）。
`alloc-array` 与 `big-output` 要求声明 `backend=linux`，否则整题被跳过。

退出码：`0` 全部符合预期；`1` 有失败；`2` 没有失败但有不稳定项；`3` 环境或配置错误（连不上 judge、语料与 `corpus.lock` 不一致、参数不对）。

## 语料

```text
benchmark/corpus/
├── corpus.lock                  每道题测试数据的指纹，防止数据悄悄漂移
└── judge/<题名>/                problem.json、testdata/、submissions/、expect.json、oracle/、README.md
```

每道题的 `README.md` 写明它检验什么、每个提交为什么得到该结论。

| 命令 | 作用 |
|---|---|
| `python3 benchmark/tools/check.py` | 不需要 judge：语料格式合格、与 `corpus.lock` 一致、重新生成的数据逐字节相同。提交前跑 |
| `python3 benchmark/tools/generate.py [--problem 题名]` | 用 `oracle/` 重新生成测试数据，两份参考解输出不一致就拒绝 |
| `python3 benchmark/tools/generate.py --update-lock --reason "原因"` | 数据确实要变时，显式更新 `corpus.lock`；必须说明原因 |

## 测试

```sh
python3 -m unittest discover -s benchmark/tools -p '*_test.py'
```

用假 judge 检查运行者本身：全部符合预期得到 0；故意改坏一条预期得到 1；时间类提交结论不一致得到 2；连不上得到 3；`requires` 不满足得到跳过而不是失败。
