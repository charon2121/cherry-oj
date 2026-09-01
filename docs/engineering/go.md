# Go 编码规范（`apps/judge-engine`）

适用于本仓库所有 Go 代码。**写 Go 之前先读完本文。**
跨语言的通用约定（单位、契约、零值陷阱、
什么进配置什么进请求、测试观念）见 [`conventions.md`](./conventions.md)，
本文只写 Go 特有的部分。

下面多数条款来自这个项目实际踩过的坑，括号里给了对应位置。

---

## 0. 铁律

提交前这三条必须干净，CI 和 `pre-commit` hook 都会强制：

```bash
gofmt -l .              # 无输出
go vet ./...            # 无输出
go test -race ./...     # 全绿
```

`-race` 不是可选项：`pool` 的并发用例在普通模式下可能几百次都不复现竞态。

---

## 1. 包与文件

- 包名**小写、单数、不用下划线**：`pool`、`store`、`checker`。
  复数只用于「注册表」语义（`languages` 装着多门语言）。
- **文件名 = 里面装什么**，按「会一起改的放一起」分，而不是按代码类型分。
  `blobs.go` 装三个 blob handler，因为它们会一起变。
- 和包同名的文件放核心类型（`pool.go` 放 `Pool`）。
- **别把包目录命名成 `testdata`** —— go 工具链会整个无视它，`go build` 报的错
  完全不着边际。数据目录才叫 `testdata`（工具链忽略它正是我们想要的）。
- 测试 fixture 放包内的 `testdata/`。

---

## 2. 命名

通用的四条——「主语 = 接收者」「动词成对且全仓统一」「限定词只在存在对立面时
才有信息量」「给意图起个名字」——见 [`conventions.md`](./conventions.md) §1.1，那里的
例子本来就取自本模块。这里只补 Go 特有的：

- **别让名字结巴。** Go 的调用处自带包名，`pool.NewPool()` 读起来是「池池」。
  用 `pool.New()`。同理 `container` 包里的文件叫 `host.go`，不是 `host_container.go`。
- **缩写全大写或全小写，不要驼峰**：`URL`、`ID`、`HTTP`——`Url`、`Id` 不是 Go 风格。
  非导出时全小写：`url`、`id`。
- **`Get` 前缀只在真的有「取」的语义时用。** 单纯的字段访问器叫 `Name()` 而不是
  `GetName()`；但 `store.Get(ref)` 是真的去取一袋字节，该带。
- **不导出的东西也要好好起名**——`registry`、`clockRatio` 这些将来会被读很多遍。

---

## 3. 可见性与接口

- **类型不导出、构造函数导出**：`diskStore` + `NewDiskStore()`。
  对外的契约是接口，具体实现随时能换。
- **接口由消费方定义。** `api.Executor`、`flow.Sandbox`、`api.Judger` 都声明在
  使用方，实现方完全不知道它们存在。好处：依赖单向不成环，且测试能塞一个
  十几行的假替身。
- **接受接口，返回结构体。** `api.New(exec Executor, ...)` 收接口，
  `pool.New(...) *Pool` 返回具体类型。
- **接口要小。** `flow.Sandbox` 只有 Upload/Run/Delete 三个方法，所以假替身
  三十行就能写完。接口越大，替身越难写，测试就越容易退化成「起个真环境」。
- 一段逻辑该挂在数据类型上还是放别处，**看它依不依赖配置**：
  `JudgeLimits.Validate()` 只看数据本身，挂类型上正好；算墙钟要读 `clockRatio`
  （配置），就不能挂在 `contract` 上，否则纯数据包要去 import `config`。

---

## 4. 可选参数

**用 `Options` 结构体，别堆裸参数。**

```go
api.New(p, st, api.Options{MaxBlobBytes: cfg.Sandbox.Store.MaxBlobBytes})  // ✓
api.New(p, st, 67108864)                                                   // ✗ 得回来翻签名
checker.Compare(checker.Options{StrictWhitespace: true}, got, want)        // ✓
checker.Compare(true, got, want)                                           // ✗ true 是什么？
```

**`Options` 的零值必须是安全的默认**，别让调用方漏填就整个跑错：

```go
func New(exec Executor, st store.Store, opts Options) *Server {
    if opts.MaxBlobBytes <= 0 {  // 没配 ≠ 不许上传
        opts.MaxBlobBytes = defaultMaxBlobBytes
    }
    ...
}
```

---

## 5. 错误处理

- **不返回恒为 nil 的 error。** 只会让每个调用点白写一次 `if err != nil`
  （`testcase.FromSpecs` 踩过：它只是把内存里的字符串包一层，没有会失败的动作）。
- **用 `%w` 包装**，让上层能 `errors.Is` 判断具体原因（`store.ErrNotFound`）。
- **错误信息要能定位**：带上路径、字段名、对方返回的 body 片段。
  `unexpected status 400` 会让人调试到怀疑人生。
- **未知情况往严格的方向倒。** `worse()` 查不到的 verdict 当成最严重——
  写成「查不到返回 a」的话，某天加了新 verdict 忘了进表，结果是**错题判成 AC**。
- **别把「业务失败」当成 error。** `client.Run` 返回 `(RunResult{TLE}, nil)` 是
  完全正常的：HTTP 对话成功了，只是被跑的程序超时了。混了会把 TLE 报成 SE。
- **外部字符串拼进路径前先用正则关死。** 已出现三次：`container.resolve`、
  `store.refPattern`、`testcase.idPattern`。`filepath.Join(root, "../../etc")`
  会老老实实跳出去。

---

## 6. 资源与生命周期

- **能流式就别攒全量。** 用 `io.Copy` 搬，别 `io.ReadAll`。只存「怎么打开」
  而不是内容（`testcase.Blob`）。几十 MB 的测例乘以并发数就是几个 GB。
- **`defer` 是函数级的，不是块级。** 写在 `for` 里，100 个资源会攒到函数返回
  才一起释放，量一大就撞 `too many open files`。**循环内的资源循环内关。**
- **清理动作别用那个正在被取消的 ctx。** 请求取消后 `ctx` 已死，
  `defer sb.Delete(ctx, ref)` 会立刻失败、ref 永久泄漏。用
  `context.WithoutCancel(ctx)`（Go 1.21+）。
- **`http.Client` 的零值永不超时。** 必须显式设 `Timeout`，且要大于对端最慢的
  一次操作——设小了会出现「沙箱正常跑着，judge 自己先超时了」，报出来是 SE，
  查半天查不到原因。
- **每个会阻塞的方法都收 `ctx`**，并用 `http.NewRequestWithContext` 传下去。
  漏掉的话上游的取消信号会在这里断掉。

---

## 7. 值语义的坑

**结构体按值返回时，切片/map 字段仍与原值共享底层数据。**

```go
lang, _ := language.Get("cpp")
lang.Compile[0] = "..."   // 改的是全局 registry！
```

`Language` 是按值返回的，看着安全，但切片头是值、底层数组是共享的。
这类 bug 极难查：**污染发生在一次判题里，症状出现在之后所有判题上**，
进程重启才恢复。返回前 `slices.Clone` 一下（`language.Get` 踩过）。

---

## 8. 并发

- 信号量用**带缓冲的 channel**：`sem chan struct{}`。`struct{}` 是零字节，
  我们只关心「桶里占了几个」。
- **拿了必还，且立刻 `defer`**：

  ```go
  select {
  case p.sem <- struct{}{}:
      defer func() { <-p.sem }()   // 拿到手就登记，别放到函数末尾
  case <-ctx.Done():
      return ..., ctx.Err()         // 排队期间要能被打断
  }
  ```

  漏还的话并发数只减不增，最后整个服务卡死。
- 非阻塞收发用 `select` + `default`。
- **`make(chan T, 0)` 是无缓冲**，不是「容量为 0 的缓冲」。`parallelism` 配成 0
  会让第一个请求就永久阻塞——零值必须在构造函数里兜底。
- 并发测试**必须 `-race`**，且要**两个方向都断言**：只断言「不超过 N」的话，
  写成完全串行也能过；加上「峰值 ≥ 2」才证明并发确实发生了。

---

## 9. 日志

- 用 `log/slog`，结构化字段，别拼字符串。
- **库代码不要用包级 `log.Printf`**：调用方接管不了，测试也没法断言
  「确实警告了」。走可注入的 `*slog.Logger`：

  ```go
  type Options struct {
      Logger *slog.Logger // nil = slog.Default()
  }
  ```

  这样测试可以注入一个写进 `bytes.Buffer` 的 handler 来断言警告内容
  （`testcase.Options`）。
- **静默跳过是事故。** 出题人少传一个 `.out`，静默跳过就变成「这题只有 9 个
  测试点」——错解可能因此拿到 AC。跳过必须留痕，且警告里要能定位到
  「哪道题的哪个点」。

---

## 10. 标准库优先

- **第三方依赖能不加就不加。** 目前整个模块只有 `gopkg.in/yaml.v3`，
  因为标准库不解析 YAML。加依赖前先确认标准库真的做不到。
- HTTP 路由用标准库 `ServeMux` 的 `"POST /run"` / `"GET /blobs/{ref}"` 语法
  （Go 1.22+），不引第三方路由。路径参数用 `r.PathValue("ref")`。
- **`bufio.Scanner` 有 64 KB 单行上限。** 有的题输出一行几百万个数字，会报
  `token too long` 然后被上层当成 SE，而题目本身没毛病。要么
  `scanner.Buffer(...)` 调大，要么用 `bufio.Reader` 逐字节推进（`checker` 走的
  是后者，顺带内存恒定）。
- 写响应时 **`WriteHeader` 必须在 body 之前，且只能调一次**。一旦写了 body，
  Go 会自动发出 200，之后再调不生效，还会打印 `superfluous response.WriteHeader call`。

---

## 11. 子进程（`os/exec`）

这个项目大量起子进程，几条踩过的：

- **命令一律写裸名字走 PATH**（`g++`、`python3`、`sh`），别硬编码
  `/usr/bin/python3`——那会绕过部署环境的选择（macOS 上 `/usr/bin/python3` 是
  系统自带的 3.9，而机器上装的可能是 3.12）。
- 工作目录内的可执行文件也**直接写名字**，不要 `./x`：container 的规则是
  「命令名不含 `/` 且该文件存在于 workDir 时才解析成绝对路径」，`./x` 绕过了它。
- **`os/exec` 不会像 shell 那样帮你回退到 `/bin/sh`。** 脚本必须带 shebang
  （`#!/bin/sh`），否则 `execve` 直接报 `exec format error`。权限要 `0o755`
  （可读**且**可执行），只给 `0o111` 解释器读不到内容。
- **超时要杀整个进程组**：只 kill 主进程的话，`g++` 的 `cc1`/`as` 会变成孤儿。
  `SysProcAttr{Setpgid: true}` + `cmd.Cancel = Kill(-pid, SIGKILL)` +
  `cmd.WaitDelay` 防卡。
- **`Maxrss` 单位分平台**：Linux 是 KiB（要 ×1024），Darwin 是 Bytes。

---

## 12. 测试

通用的测试观念见 [`conventions.md`](./conventions.md) §1.8，这里只写 Go 的写法：

- **表驱动 + `t.Run`**，用例是数据、断言只有一份。子测试名用中文没问题。
- **`t.TempDir()`** 建临时目录，自动清理、互不干扰。别用 `os.TempDir()` 在
  `/tmp` 留垃圾。
- **`t.Cleanup` 只在一处登记。** helper 里登记过就别在用例里再登记一遍
  （会重复调用，虽然幂等但读起来困惑）。
- **`t.Helper()`** 别忘，失败时行号才会指向调用处。
- **别在测试里吞 error**：`c, _ := NewHost()` 失败时 `c` 是 nil，报错推迟到下
  一行变成莫名其妙的 nil panic，行号还指错地方。
- **假替身优先于真环境**：`httptest.NewServer` 假装 sandbox、手写的
  `fakeSandbox` 假装整个执行层。判题逻辑的单测不该需要真起一个沙箱。
- **黑盒（`package foo_test`）优先**；只有当断言必须读非导出字段或遍历非导出
  注册表时才用白盒，并在文件头注明理由（`language` 的一致性检查、
  `container` 要读 `workDir`）。
- **依赖本机工具链的集成测试用 `t.Skip`** 优雅退化，并在 CI 里打印工具链版本
  ——否则某天镜像变了、测试静默跳过也没人发现。
