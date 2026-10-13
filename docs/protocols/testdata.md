# 测试数据协议

规定一道题的测试数据放在哪里、长什么样、谁写谁读。problem-service 写，judge 读，两端围绕**同一个地址**协作。

## 快速使用

写入方：把成对的 `<名字>.in` / `<名字>.out` 放进一个目录，用仓库脚本生成元数据，再把目录发布到某个地址。

```sh
python3 scripts/testdata_pack.py /srv/cherry-oj/testdata/p-a-plus-b   # 在目录里写出 testdata.json
```

读取方：拿到地址后，先读 `<地址>/testdata.json`，再按它列出的文件取回并核对。judge 的请求里只带这一个地址（`testDataLocation`）。

## 术语

同一个东西只有一个名字。大小写、下划线、连字符随各层惯例变，词本身不换。

| 概念 | 名称 | 中文 | 出现处 |
|---|---|---|---|
| 一道题的整套数据（一个目录） | testdata | 测试数据 | `testDataLocation`、`testdata.json` |
| 其中的一对 `<name>.in` / `<name>.out` | testcase | 测试点 | `testcases[]`、`testcaseResults` |
| 测试点个数 | testcaseCount | 测试点数 | `testdata.json`、判题结果 |
| 整份数据的内容指纹 | digest | 指纹 | `testdata.json` 的 `digest`、`testDataDigest` |
| 数据目录的地址 | location | 地址 | `testDataLocation` |

testdata 是一堆 testcase。「摘要」不指 digest。

## 1. 参与方

| 角色 | 做什么 |
|---|---|
| problem-service（写入方） | 按本协议写出目录；数据库里只保存地址 |
| judging-service | 判题时把地址原样放进 `POST /judge` 的请求 |
| judge（读取方） | 读 `<地址>/testdata.json`，取回并核对数据，把每个测试点的 `.in` 作为标准输入交给执行器 |

- 一道题只有一份测试数据，没有版本号。地址是它唯一的标识。
- judge 不访问数据库，只认请求里的地址。

## 2. 地址

地址指向**一个目录**：

| 形式 | 例子 | 规则 |
|---|---|---|
| 本地路径 | `/srv/cherry-oj/testdata/p-a-plus-b` | 以 `/` 开头的绝对路径。读写双方必须共享同一个文件系统 |
| HTTP(S) | `https://data.example/testdata/p-a-plus-b/` | 以 `http://` 或 `https://` 开头，作为基地址，末尾没有 `/` 时补上。只需支持对静态文件的 `GET` |

其他形式（相对路径、其他协议）一律拒绝。目录里的内容用相对名字访问：
`<地址>/testdata.json`、`<地址>/<名字>.in`、`<地址>/<名字>.out`。

## 3. 目录布局

平铺，没有子目录：

```text
<地址>/
├── testdata.json    元数据，最后写入
├── 1.in
├── 1.out
├── 2.in
└── 2.out
```

## 4. testdata.json

```json
{
  "schemaVersion": 1,
  "testcaseCount": 2,
  "totalBytes": 15,
  "digest": "<64 位小写十六进制>",
  "testcases": [
    { "name": "1",
      "input":  { "sizeBytes": 4, "sha256": "<64 位小写十六进制>" },
      "output": { "sizeBytes": 2, "sha256": "<64 位小写十六进制>" } },
    { "name": "2",
      "input":  { "sizeBytes": 6, "sha256": "<64 位小写十六进制>" },
      "output": { "sizeBytes": 3, "sha256": "<64 位小写十六进制>" } }
  ]
}
```

| 字段 | 规则 |
|---|---|
| `schemaVersion` | 本格式的版本，固定为 `1` |
| `testcaseCount` | 测试点个数，1–1000，等于 `testcases` 的长度 |
| `totalBytes` | 所有 `.in` 与 `.out` 的字节数之和 |
| `testcases` | **顺序就是判题顺序**，也是结果里测试点序号（从 1 起）的来源 |
| `testcases[].name` | 匹配 `^[A-Za-z0-9][A-Za-z0-9._-]{0,123}$`，同一份数据内不重复。文件固定为 `<name>.in` 与 `<name>.out` |
| `sizeBytes` | 该文件的字节数，不为负 |
| `sha256` | 该文件内容的 SHA-256，64 位小写十六进制 |
| `digest` | 整份数据的指纹，见下 |

`testdata.json` 不超过 1 MiB。读取方（judge）拒绝上表以外的字段和 JSON 之后的多余内容。

### 指纹 `digest`

对每个测试点按 `testcases` 的顺序，依次生成两行：

```text
<input sha256>  <name>.in
<output sha256>  <name>.out
```

（两个空格，行尾换行。）把所有行拼接后取 SHA-256。可用 `sha256sum` 的输出格式复现。
顺序是指纹的一部分，内容或顺序一变指纹就变。指纹只用于完整性与缓存判断，不是版本号。

## 5. 读取方的责任

每次判题按以下顺序处理：

1. 读 `<地址>/testdata.json`，检查结构、字段规则、`testcaseCount`、名字唯一、`totalBytes` 与各文件大小之和一致、`digest` 与重新计算的值一致。
2. 把每个测试点的 `.in`、`.out` 复制到读取方自己的本地目录，核对大小与 SHA-256。
   本地已有且 `digest` 相同的副本可以复用；缓存是优化，**每次判题仍必须先读一次 `testdata.json`**。
3. 全部复制并核对完成之后，才开始运行第一个测试点。此后写入方再改动这个地址，不影响本次判题。
4. 运行每个测试点：`<name>.in` 作为标准输入；`<name>.out` 只用于比对，**不进入沙箱**。

**任何一步失败，整次判题给出 SE（系统内部错误）并记录原因，不判其中一部分。**

| 情况 | 结果 |
|---|---|
| 地址格式不支持 | SE |
| `testdata.json` 读不到、解析失败或不满足字段规则 | SE |
| 文件缺失，或大小、SHA-256 与 `testdata.json` 不符 | 重新读取 `testdata.json` 并重试一次；仍失败则 SE |
| 数据超过读取方配置的单文件或总量上限 | SE（不重试） |
| HTTP 返回非 200 | SE（`404` 视为文件缺失，按上一行处理；其他状态不重试） |

请求缺少地址字段属于请求本身不合法，按请求校验规则返回 400；其余都是 HTTP 200 加 SE。

## 6. 写入方的责任

- **先写数据文件，最后写 `testdata.json`。** 它是「这份数据已完整」的标记，读不到它就是未就绪。
- **不原地修改已经可读的目录。** 更新时先在同级写出一个完整的新目录，再整体替换旧目录（本地用重命名，HTTP 由服务端保证）。地址保持不变。
- 测试数据的上传、校验与存储方式由写入方自行决定，协议只规定最终落成的目录。
- 测试点顺序由写入方决定并写进 `testcases`。`scripts/testdata_pack.py` 的默认顺序：名字能解析为整数的按数值升序，其余按字符串序排在后面。

## 7. 版本与兼容

- 格式版本是 `schemaVersion`，目前只有 `1`。读取方遇到不认识的版本、未知字段或多余内容，一律拒绝。
- 改动字段、校验规则或指纹算法，必须同时修改 problem-service、judge、`scripts/testdata_pack.py` 和相关测试，并升级 `schemaVersion`。
- 不在本协议范围内：请求字段名、数据库列、HTTP 接口、前端展示、题目的发布状态；地址的来源限制（允许哪些根目录或 HTTP 源）。
