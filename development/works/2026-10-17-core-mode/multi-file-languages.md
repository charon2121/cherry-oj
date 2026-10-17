# 考察：CORE 用多文件编译，13 门语言的驱动代码与用户代码

属于 [C++ CORE 模式](./00-work.md) 的方案考察。2026-10-17 用户据此选定方案（见 §8），设计见 [design.md](./design.md)。

第 2、3 节是"只做多文件、不加前缀"时每门语言的写法；最终方案在用户文件前再加语言前缀并改写报错行号（§6.3、§7），
所以表中"用户必须写的样板"（Java 的 import、Go 的 `package main` 等）在最终方案里由前缀提供，用户不用写。

## 1. 要回答的问题

单文件合并（把用户代码替换进模板的占位符）有一个绕不开的问题：很多语言要求 `package`、`import`、`using`、`<?php`
写在文件最前面，用户代码前面就一定有前缀，报错行号就会错位（见 §6.1）。

多文件编译的做法：**用户代码单独存成一个文件，驱动代码（读输入、调用用户函数、输出结果）存成另一个文件**，两个文件一起
编译或运行。每个文件从第 1 行开始编号，报错直接写出是哪个文件的哪一行，不需要改写报错，也不需要显示前缀。

这份文档用同一道题，把 13 门热门语言的用户文件、驱动文件、编译运行命令逐一写出来，**并全部实测**，用来判断这个做法是否可行。

## 2. 结论速览

**13 门语言全部可行。** 正确代码都输出 `0 1`；在用户代码某一行引用未定义的名字，报错都指向用户文件的原始行号。

| 语言 | 用户文件 | 驱动文件 | 两个文件怎么连起来 | 用户必须写的样板 | 报错位置（实测） | 首次编译 |
|---|---|---|---|---|---|---|
| C | `solution.c` | `Main.c` | 驱动 `#include "solution.c"` | 无 | `solution.c:7:28` | — |
| C++ | `solution.cpp` | `Main.cpp` | 驱动 `#include "solution.cpp"` | 无 | `solution.cpp:7:59` | 1.7 秒 |
| Java | `Solution.java` | `Main.java` | 一起 `javac` | 自己用到的 `import` | `Solution.java:8` | 0.6 秒 |
| Python 3 | `solution.py` | `Main.py` | `from solution import Solution` | 无 | `"/w/solution.py", line 6` | 不编译 |
| Go | `solution.go` | `main.go` | 同一个 package 一起编译 | `package main` 与自己用到的 `import` | `./solution.go:7:24` | — |
| JavaScript | `solution.js` | `Main.js` | `vm.runInThisContext(..., {filename})` | 无 | `solution.js:9:81` | 不编译 |
| TypeScript | `solution.ts` | `Main.ts` | `tsc --outFile` 加 source map | 无 | `solution.ts(5,45)` | — |
| C# | `Solution.cs` | `Main.cs` + 固定的 `Main.csproj` | 同一个项目；`global using` | 无 | `/w/Solution.cs(5,86)` | 3.2 秒 |
| Kotlin | `Solution.kt` | `Main.kt` | 一起 `kotlinc` | 无 | `Solution.kt:6:53` | **16 秒** |
| Rust | `solution.rs` | `main.rs` | 驱动 `include!("solution.rs")` | 自己用到的 `use` | `solution.rs:8:50` | 1.0 秒 |
| Swift | `solution.swift` | `main.swift` | 同一个模块一起编译 | 无 | `solution.swift:5:58` | 1.2 秒 |
| PHP | `solution.php` | `Main.php` | `require __DIR__ . '/solution.php'` | 第一行 `<?php` | `/w/solution.php:12` | 不编译 |
| Ruby | `solution.rb` | `main.rb` | `require_relative "solution"` | 无 | `/w/solution.rb:7` | 不编译 |

"用户必须写的样板"就是这门语言平时写一个文件本来就要写的东西，放在预设代码里，用户看得到、改得了。
连接方式归纳起来只有四种：

1. **文本包含**（C、C++、Rust）：驱动文件先写好头文件或公共定义，再把用户文件整个包含进来。编译器仍按 `solution.*` 报行号，用户一行 include 都不用写。
2. **同一次编译的多个源文件**（Java、Kotlin、C#、Go、Swift、TypeScript）：语言本来就支持一个程序由多个文件组成。
3. **模块导入**（Python、PHP、Ruby）：驱动文件导入用户文件。
4. **按脚本载入**（JavaScript）：驱动文件读入用户文件当脚本执行，并指定文件名，栈帧仍显示 `solution.js` 的行号。

编译耗时在 Mac 的 Docker Desktop 上测得，只作量级参考；"—"表示没有单独计时。

## 3. 各语言的写法

示例题目统一为"两数之和"。输入格式：第一行 `n`，第二行 `n` 个整数，第三行 `target`；输出两个下标，用空格隔开。
实测输入 `4 / 2 7 11 15 / 9`，期望输出 `0 1`。驱动代码只负责按这个格式读入、调用用户函数、输出结果。

实测环境（官方 Docker 镜像）：`gcc:13`、`eclipse-temurin:21-jdk`（Java 与 Kotlin 2.0.21）、`python:3.12-slim`、
`golang:1.26.3-bookworm`、`node:22-slim`（TypeScript 5.6.3）、`mcr.microsoft.com/dotnet/sdk:8.0`、`rust:1.83-slim`、
`swift:6.0`、`php:8.3-cli`、`ruby:3.3-slim`。报错中的 `/w/` 是容器里的工作目录。

### 3.1 C

- **连接方式：** 文本包含。驱动文件先 include 常用头文件，再 `#include "solution.c"`，只编译 `Main.c`。
- **用户要写的样板：** 无。
- 和 LeetCode 一样，返回数组由用户 `malloc`，长度写进 `returnSize`；签名约定写在题面里。

**用户文件 `solution.c`（预设代码，用户在此基础上作答）：**

```c
int* twoSum(int* nums, int numsSize, int target, int* returnSize) {

}
```

**驱动文件 `Main.c`（题目的判题模板）：**

```c
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <limits.h>
#include <math.h>
#include "solution.c"

int main(void) {
    int n;
    if (scanf("%d", &n) != 1) return 1;
    int *nums = malloc(sizeof(int) * (n > 0 ? n : 1));
    for (int i = 0; i < n; i++) scanf("%d", &nums[i]);
    int target;
    scanf("%d", &target);
    int size = 0;
    int *ans = twoSum(nums, n, target, &size);
    for (int i = 0; i < size; i++) printf(i ? " %d" : "%d", ans[i]);
    printf("\n");
    return 0;
}
```

**编译与运行：** `gcc -O2 -std=c11 Main.c -o Main -lm`；`./Main < 输入`

**实测：在一份完整解答的第 7 行加上未定义的 `missing`，得到：**

```text
In file included from Main.c:7:
solution.c: In function 'twoSum':
solution.c:7:28: error: 'missing' undeclared (first use in this function); did you mean 'isinf'?
    7 |                 r[1] = j + missing;
      |                            ^~~~~~~
      |                            isinf
```

### 3.2 C++

- **连接方式：** 文本包含。驱动文件先 `#include <bits/stdc++.h>` 和 `using namespace std;`，再 `#include "solution.cpp"`，只编译 `Main.cpp`。
- **用户要写的样板：** 无。这和现在的 `languages.go` 编译命令 `g++ Main.cpp ...` 完全一样，只是沙箱里多了一个 `solution.cpp`。

**用户文件 `solution.cpp`（预设代码，用户在此基础上作答）：**

```cpp
class Solution {
public:
    vector<int> twoSum(vector<int>& nums, int target) {

    }
};
```

**驱动文件 `Main.cpp`（题目的判题模板）：**

```cpp
#include <bits/stdc++.h>
using namespace std;
#include "solution.cpp"

int main() {
    int n;
    cin >> n;
    vector<int> nums(n);
    for (auto &x : nums) cin >> x;
    int target;
    cin >> target;
    vector<int> ans = Solution().twoSum(nums, target);
    for (size_t i = 0; i < ans.size(); i++) cout << (i ? " " : "") << ans[i];
    cout << "\n";
}
```

**编译与运行：** `g++ -O2 -std=c++17 Main.cpp -o Main`；`./Main < 输入`

**实测：在一份完整解答的第 7 行加上未定义的 `missing`，得到：**

```text
In file included from Main.cpp:3:
solution.cpp: In member function 'std::vector<int> Solution::twoSum(std::vector<int>&, int)':
solution.cpp:7:59: error: 'missing' was not declared in this scope
    7 |             if (it != seen.end()) return {it->second, i + missing};
      |                                                           ^~~~~~~
（后面还有一条由同一处错误引起的连带报错，略）
```

### 3.3 Java

- **连接方式：** 同一次编译的两个源文件。`Solution` 在 `Solution.java` 里，可以写成 `public class`，也可以不写 `public`。
- **用户要写的样板：** 自己用到的 `import`（`java.lang` 不用写）。预设代码里放上 `import java.util.*;`。
- 用户代码里不能再定义 `Main` 类，否则和驱动文件冲突（编译错误，报在用户文件里）。

**用户文件 `Solution.java`（预设代码，用户在此基础上作答）：**

```java
import java.util.*;

class Solution {
    public int[] twoSum(int[] nums, int target) {

    }
}
```

**驱动文件 `Main.java`（题目的判题模板）：**

```java
import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws IOException {
        StreamTokenizer in = new StreamTokenizer(new BufferedReader(new InputStreamReader(System.in)));
        in.nextToken();
        int n = (int) in.nval;
        int[] nums = new int[n];
        for (int i = 0; i < n; i++) { in.nextToken(); nums[i] = (int) in.nval; }
        in.nextToken();
        int target = (int) in.nval;
        int[] ans = new Solution().twoSum(nums, target);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < ans.length; i++) { if (i > 0) out.append(' '); out.append(ans[i]); }
        System.out.println(out);
    }
}
```

**编译与运行：** `javac -encoding UTF-8 Main.java Solution.java`；`java Main < 输入`

**实测：在一份完整解答的第 8 行加上未定义的 `missing`，得到：**

```text
Solution.java:8: error: cannot find symbol
            if (j != null) return new int[]{j, i + missing};
                                                   ^
  symbol:   variable missing
  location: class Solution
1 error
```

### 3.4 Python 3

- **连接方式：** 模块导入，驱动文件 `from solution import Solution`。
- **用户要写的样板：** 无。驱动文件在导入用户模块之前，把 `List`、`Optional`、`collections`、`heapq` 等名字放进 `builtins`，用户不写 import 也能用；用户自己写 import 同样可以。
- **运行参数：** 不能用 `python3 -I`（它会把脚本所在目录移出 `sys.path`，导致 `No module named 'solution'`）；用 `-B`，避免在沙箱里写 `__pycache__`。

**用户文件 `solution.py`（预设代码，用户在此基础上作答）：**

```python
class Solution:
    def twoSum(self, nums: List[int], target: int) -> List[int]:
        pass
```

**驱动文件 `Main.py`（题目的判题模板）：**

```python
import bisect
import builtins
import collections
import functools
import heapq
import itertools
import math
import sys
import typing

# 用户代码不写 import 也能用常见名字，相当于 LeetCode 的预导入；不占用户文件的行
for _name in ("List", "Dict", "Set", "Tuple", "Optional"):
    setattr(builtins, _name, getattr(typing, _name))
for _module in (bisect, collections, functools, heapq, itertools, math):
    setattr(builtins, _module.__name__, _module)

from solution import Solution


def main() -> None:
    data = sys.stdin.read().split()
    n = int(data[0])
    nums = list(map(int, data[1:1 + n]))
    target = int(data[1 + n])
    print(" ".join(map(str, Solution().twoSum(nums, target))))


main()
```

**编译与运行：** 无需编译；运行 `python3 -B Main.py < 输入`

**实测：在一份完整解答的第 6 行加上未定义的 `missing`，得到：**

```text
Traceback (most recent call last):
  File "/w/Main.py", line 28, in <module>
    main()
  File "/w/Main.py", line 25, in main
    print(" ".join(map(str, Solution().twoSum(nums, target))))
                            ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/w/solution.py", line 6, in twoSum
    return [seen[target - x], i + missing]
                                  ^^^^^^^
NameError: name 'missing' is not defined
```

### 3.5 Go

- **连接方式：** 同一个 `package main` 的两个文件，一起编译。
- **用户要写的样板：** 第一行 `package main`，以及自己用到的 `import`。Go 不允许出现未使用的 import，所以平台不能替用户预先导入。
- 用户不能定义 `main` 函数，也不能和驱动文件里的顶层名字重名（编译错误，报在两个文件之一）。

**用户文件 `solution.go`（预设代码，用户在此基础上作答）：**

```go
package main

func twoSum(nums []int, target int) []int {

}
```

**驱动文件 `main.go`（题目的判题模板）：**

```go
package main

import (
	"bufio"
	"fmt"
	"os"
	"strings"
)

func main() {
	in := bufio.NewReader(os.Stdin)
	var n int
	fmt.Fscan(in, &n)
	nums := make([]int, n)
	for i := range nums {
		fmt.Fscan(in, &nums[i])
	}
	var target int
	fmt.Fscan(in, &target)
	ans := twoSum(nums, target)
	parts := make([]string, len(ans))
	for i, x := range ans {
		parts[i] = fmt.Sprint(x)
	}
	fmt.Println(strings.Join(parts, " "))
}
```

**编译与运行：** `go build -o Main main.go solution.go`；`./Main < 输入`

**实测：在一份完整解答的第 7 行加上未定义的 `missing`，得到：**

```text
# command-line-arguments
./solution.go:7:24: undefined: missing
```

### 3.6 JavaScript（Node.js）

- **连接方式：** 驱动文件用 `vm.runInThisContext` 把 `solution.js` 当作普通脚本执行，并指定 `filename: "solution.js"`。用户沿用 LeetCode 的写法 `var twoSum = function ...`，不必写 `module.exports`。
- **用户要写的样板：** 无。
- 用户的顶层声明会进入全局作用域，驱动文件自己的变量要放在函数里或使用不容易重名的名字。

**用户文件 `solution.js`（预设代码，用户在此基础上作答）：**

```js
/**
 * @param {number[]} nums
 * @param {number} target
 * @return {number[]}
 */
var twoSum = function(nums, target) {

};
```

**驱动文件 `Main.js`（题目的判题模板）：**

```js
const fs = require("fs");
const path = require("path");
const vm = require("vm");

// 以普通脚本载入用户文件：用户不必写 module.exports；报错与调用栈仍是 solution.js 自己的行号
const userFile = path.join(__dirname, "solution.js");
vm.runInThisContext(fs.readFileSync(userFile, "utf8"), { filename: "solution.js" });

const data = fs.readFileSync(0, "utf8").trim().split(/\s+/).map(Number);
const n = data[0];
const nums = data.slice(1, 1 + n);
const target = data[1 + n];
console.log(twoSum(nums, target).join(" "));
```

**编译与运行：** 无需编译；运行 `node Main.js < 输入`

**实测：在一份完整解答的第 9 行加上未定义的 `missing`，得到：**

```text
solution.js:9
        if (seen.has(target - nums[i])) return [seen.get(target - nums[i]), i + missing];
                                                                                ^
ReferenceError: missing is not defined
    at twoSum (solution.js:9:81)
    at Object.<anonymous> (/w/Main.js:13:13)
```

### 3.7 TypeScript

- **连接方式：** 两个文件都是全局脚本，`tsc --outFile` 合并成一个 `Main.js` 并生成 source map；运行时用 `node --enable-source-maps`。
- **用户要写的样板：** 无。
- 类型错误由 `tsc` 按各自文件报告（`solution.ts(5,45)`），属于 CE；运行时的栈帧通过 source map 映射回 `.ts` 的行号。
- 编译机需要预装 `typescript`（实测用的是 5.6.3）；驱动文件里自己声明 `require`、`console`，不依赖 `@types/node`。

**用户文件 `solution.ts`（预设代码，用户在此基础上作答）：**

```ts
function twoSum(nums: number[], target: number): number[] {

};
```

**驱动文件 `Main.ts`（题目的判题模板）：**

```ts
declare function require(name: string): any;
declare const console: { log(...values: unknown[]): void };
const fs = require("fs");

const data: number[] = fs.readFileSync(0, "utf8").trim().split(/\s+/).map(Number);
const n = data[0];
const nums = data.slice(1, 1 + n);
const target = data[1 + n];
console.log(twoSum(nums, target).join(" "));
```

**编译与运行：** `tsc --strict --target es2022 --lib es2022 --module none --sourceMap --outFile Main.js solution.ts Main.ts`；`node --enable-source-maps Main.js < 输入`

**实测：在一份完整解答的第 5 行加上未定义的 `missing`，得到：**

```text
solution.ts(5,45): error TS2304: Cannot find name 'missing'.
```

**实测：用户代码第 5 行在运行时抛异常（source map 把栈帧映射回 `.ts`）：**

```text
/w/solution.ts:5
    return bad.boom();
               ^

TypeError: Cannot read properties of null (reading 'boom')
    at twoSum (/w/solution.ts:5:16)
```

### 3.8 C#

- **连接方式：** 同一个项目的两个源文件。驱动文件里的 `global using` 对用户文件同样生效。
- **用户要写的样板：** 无。
- 需要一个固定的 `Main.csproj`，属于语言配置而不是题目。`dotnet build` 实测首次编译约 3.2 秒，需要确认能离线 restore（没有 NuGet 包引用时可以）。

**用户文件 `Solution.cs`（预设代码，用户在此基础上作答）：**

```csharp
public class Solution {
    public int[] TwoSum(int[] nums, int target) {

    }
}
```

**驱动文件 `Main.cs`（题目的判题模板）：**

```csharp
global using System;
global using System.Collections.Generic;
global using System.Linq;
global using System.Text;

public static class Program {
    public static void Main() {
        int[] data = Console.In.ReadToEnd()
            .Split((char[])null, StringSplitOptions.RemoveEmptyEntries)
            .Select(int.Parse).ToArray();
        int n = data[0];
        int[] nums = data.Skip(1).Take(n).ToArray();
        int target = data[1 + n];
        Console.WriteLine(string.Join(" ", new Solution().TwoSum(nums, target)));
    }
}
```

**语言配置附带的项目文件 `Main.csproj`（每门语言一份，不属于题目）：**

```xml
<Project Sdk="Microsoft.NET.Sdk">
  <PropertyGroup>
    <OutputType>Exe</OutputType>
    <TargetFramework>net8.0</TargetFramework>
    <ImplicitUsings>disable</ImplicitUsings>
    <Nullable>disable</Nullable>
    <AssemblyName>Main</AssemblyName>
  </PropertyGroup>
</Project>
```

**编译与运行：** `dotnet build -c Release -o out --nologo -v q -clp:NoSummary`；`dotnet out/Main.dll < 输入`

**实测：在一份完整解答的第 5 行加上未定义的 `missing`，得到：**

```text
/w/Solution.cs(5,86): error CS0103: The name 'missing' does not exist in the current context [/w/Main.csproj]
```

### 3.9 Kotlin

- **连接方式：** `kotlinc` 同时编译两个文件，打成一个 jar。
- **用户要写的样板：** 无。Kotlin 默认导入 `kotlin.collections.*` 等常用包；用到 `java.util.*` 时用户自己 import。
- **编译很慢：** 实测首次编译约 16 秒（`kotlinc` 自身启动 JVM）。要么放宽编译时限，要么改用常驻的编译守护进程，需要单独评估。

**用户文件 `Solution.kt`（预设代码，用户在此基础上作答）：**

```kotlin
class Solution {
    fun twoSum(nums: IntArray, target: Int): IntArray {

    }
}
```

**驱动文件 `Main.kt`（题目的判题模板）：**

```kotlin
fun main() {
    val data = generateSequence(::readLine).joinToString(" ")
        .split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt() }
    val n = data[0]
    val nums = data.subList(1, 1 + n).toIntArray()
    val target = data[1 + n]
    println(Solution().twoSum(nums, target).joinToString(" "))
}
```

**编译与运行：** `kotlinc Main.kt Solution.kt -include-runtime -d Main.jar`；`java -jar Main.jar < 输入`

**实测：在一份完整解答的第 6 行加上未定义的 `missing`，得到：**

```text
Solution.kt:6:53: error: unresolved reference 'missing'.
            if (j != null) return intArrayOf(j, i + missing)
                                                    ^^^^^^^
```

### 3.10 Rust

- **连接方式：** 文本包含。驱动文件先定义 `struct Solution;`，再 `include!("solution.rs")`；用户沿用 LeetCode 的写法，只写 `impl Solution`。
- **用户要写的样板：** 自己用到的 `use`，写在自己文件里就行（`include!` 包含的文件里可以出现 `use`）。
- 报错位置是 `solution.rs:8:50`，`include!` 不影响行号。

**用户文件 `solution.rs`（预设代码，用户在此基础上作答）：**

```rust
impl Solution {
    pub fn two_sum(nums: Vec<i32>, target: i32) -> Vec<i32> {

    }
}
```

**驱动文件 `main.rs`（题目的判题模板）：**

```rust
use std::io::Read;

struct Solution;

include!("solution.rs");

fn main() {
    let mut input = String::new();
    std::io::stdin().read_to_string(&mut input).unwrap();
    let data: Vec<i32> = input.split_whitespace().map(|s| s.parse().unwrap()).collect();
    let n = data[0] as usize;
    let nums = data[1..1 + n].to_vec();
    let target = data[1 + n];
    let ans = Solution::two_sum(nums, target);
    let parts: Vec<String> = ans.iter().map(|x| x.to_string()).collect();
    println!("{}", parts.join(" "));
}
```

**编译与运行：** `rustc -O --edition 2021 main.rs -o Main`；`./Main < 输入`

**实测：在一份完整解答的第 8 行加上未定义的 `missing`，得到：**

```text
error[E0425]: cannot find value `missing` in this scope
 --> solution.rs:8:50
  |
8 |                 return vec![j as i32, i as i32 + missing];
  |                                                  ^^^^^^^ not found in this scope
error: aborting due to 1 previous error
For more information about this error, try `rustc --explain E0425`.
```

### 3.11 Swift

- **连接方式：** 同一个模块的两个文件，一起编译；只有 `main.swift` 允许写顶层执行代码。
- **用户要写的样板：** 无。用到 `Foundation` 时用户自己 `import Foundation`。

**用户文件 `solution.swift`（预设代码，用户在此基础上作答）：**

```swift
class Solution {
    func twoSum(_ nums: [Int], _ target: Int) -> [Int] {

    }
}
```

**驱动文件 `main.swift`（题目的判题模板）：**

```swift
var tokens: [Int] = []
while let line = readLine() {
    tokens += line.split(separator: " ").compactMap { Int($0) }
}
let n = tokens[0]
let nums = Array(tokens[1..<(1 + n)])
let target = tokens[1 + n]
print(Solution().twoSum(nums, target).map(String.init).joined(separator: " "))
```

**编译与运行：** `swiftc -O main.swift solution.swift -o Main`；`./Main < 输入`

**实测：在一份完整解答的第 5 行加上未定义的 `missing`，得到：**

```text
solution.swift:5:58: error: cannot find 'missing' in scope
 3 |         var seen: [Int: Int] = [:]
 4 |         for (i, x) in nums.enumerated() {
 5 |             if let j = seen[target - x] { return [j, i + missing] }
   |                                                          `- error: cannot find 'missing' in scope
 6 |             seen[x] = i
 7 |         }
```

### 3.12 PHP

- **连接方式：** 驱动文件 `require __DIR__ . '/solution.php'`。
- **用户要写的样板：** 第一行 `<?php`。没有它，PHP 会把文件内容当普通文本原样输出。预设代码里带上。
- 动态语言：`missing()` 这类错误要到运行时才报（RE），`php -l` 只能检查语法。
- 报错里是绝对路径 `/w/solution.php:12`，路径清洗要保留文件名。

**用户文件 `solution.php`（预设代码，用户在此基础上作答）：**

```php
<?php

class Solution {
    /**
     * @param Integer[] $nums
     * @param Integer $target
     * @return Integer[]
     */
    function twoSum($nums, $target) {

    }
}
```

**驱动文件 `Main.php`（题目的判题模板）：**

```php
<?php
require __DIR__ . '/solution.php';

$data = array_map('intval', preg_split('/\s+/', trim(stream_get_contents(STDIN))));
$n = $data[0];
$nums = array_slice($data, 1, $n);
$target = $data[1 + $n];
echo implode(' ', (new Solution())->twoSum($nums, $target)), "\n";
```

**编译与运行：** `php -l solution.php`；`php Main.php < 输入`

**实测：在一份完整解答的第 12 行加上未定义的 `missing`，得到：**

```text
No syntax errors detected in solution.php
Fatal error: Uncaught Error: Call to undefined function missing() in /w/solution.php:12
Stack trace:
#0 /w/Main.php(8): Solution->twoSum(Array, 9)
#1 {main}
  thrown in /w/solution.php on line 12
```

### 3.13 Ruby

- **连接方式：** 驱动文件 `require_relative "solution"`；用户沿用 LeetCode 的写法，在顶层定义 `def two_sum`。
- **用户要写的样板：** 无。
- 动态语言：未定义名字要到运行时才报（RE），`ruby -c` 只检查语法。

**用户文件 `solution.rb`（预设代码，用户在此基础上作答）：**

```ruby
# @param {Integer[]} nums
# @param {Integer} target
# @return {Integer[]}
def two_sum(nums, target)

end
```

**驱动文件 `main.rb`（题目的判题模板）：**

```ruby
require_relative "solution"

data = $stdin.read.split.map(&:to_i)
n = data[0]
nums = data[1, n]
target = data[1 + n]
puts two_sum(nums, target).join(" ")
```

**编译与运行：** `ruby -c solution.rb`；`ruby main.rb < 输入`

**实测：在一份完整解答的第 7 行加上未定义的 `missing`，得到：**

```text
Syntax OK
/w/solution.rb:7:in `block in two_sum': undefined local variable or method `missing' for main (NameError)
    return [seen[target - x], i + missing] if seen.key?(target - x)
                                  ^^^^^^^
	from /w/solution.rb:6:in `each'
	from /w/solution.rb:6:in `each_with_index'
	from /w/solution.rb:6:in `two_sum'
	from main.rb:7:in `<main>'
```

## 4. 考察中发现的问题

1. **judge 要能接收两个文件。** 最小的改法：`JudgeRequest` 在 `source`（驱动文件；ACM 时就是用户程序）之外，
   增加一个可选的 `solutionSource`（用户文件）。**文件名由 judge 的语言表决定**（`solution.cpp`、`Solution.java`……），
   不接受外部传入的文件名，不会碰到"外部字符串拼进路径"那条铁律。编译型语言把两个文件都放进编译沙箱，运行时只放编译产物；
   解释型语言运行时两个文件都要在。执行层的 `RunSpec.Inputs` 本来就是文件表，不用改。judge 仍然不知道 CORE 是什么。
2. **报错里的路径清洗要改。** judging-service 现在用正则把所有路径替换成 `<file>`：`./solution.go` 会变成 `.<file>`，
   `/w/solution.php` 会变成 `<file>`，用户就分不清错在自己的文件还是驱动文件。要改成"绝对路径只保留文件名"。
3. **用户文件和驱动文件共享命名空间。** Go 的顶层名字、Java 的类名、JavaScript 和 Ruby 的全局名字、C/C++ 的全局符号，
   两边重名会冲突。驱动代码要尽量少占顶层名字（放进函数里，或用 `__driver_` 前缀），并约定用户不能定义入口
   （Go 的 `main`、Java 的 `Main`）。冲突时报错会写明是哪个文件，用户能看懂。
4. **驱动代码要按题目、按语言各写一份。** 一道题开放 13 门语言，就要写 13 份驱动代码。LeetCode 是用函数签名描述自动生成驱动代码的，
   而我们已确认"不建立通用函数签名和类型系统"。第一版只有 C++，影响不大；以后加语言时，这会是主要的运营成本，
   到时候需要重新评估是否做驱动代码生成器（不在本次范围内）。
5. **动态语言大多数错误是运行时错误。** Python、JavaScript、PHP、Ruby 里未定义名字要运行到那一行才报，判成 RE 而不是 CE；
   编译期只能做语法检查（`php -l`、`ruby -c`）。这和这些语言本地运行的体验一致。
6. **编译时间差别很大。** Kotlin 首次编译 16 秒，C# 约 3 秒，其余在 2 秒以内。编译时限要按语言配置，Kotlin 可能需要常驻编译守护进程。
7. **驱动文件在运行时对用户程序可见。** 解释型语言运行时，驱动文件就在用户程序旁边，用户代码可以读到它。
   这与"模板不保密"（design.md Q4）一致：驱动代码里不能放答案。
8. **Python 的运行参数。** 不能用 `-I`（导入不到用户模块），要用 `-B`（不写 `__pycache__`）。
9. **沙箱里目前只有 C/C++ 工具链。** 其他语言需要先扩展 rootfs，这是"新增语言"的工作，不属于 CORE。

## 5. 与单文件方案相比

| | 单文件合并（占位符） | 多文件编译 |
|---|---|---|
| 用户代码的行号 | 有前缀就会偏移，需要改写报错，或者把前缀显示出来 | 每个文件从 1 开始，天然正确 |
| 分辨谁的错 | 都报在同一个文件里 | 文件名直接区分 `solution.*` 与 `Main.*` |
| 必须写在最前面的 `package`/`import` | 和占位符的位置冲突 | 各写在各自的文件里，不冲突 |
| judge | 不改 | 增加一个可选的用户文件 |
| 已确认决定 | 符合 #4（唯一占位符替换） | 推翻 #4 与 MVP 基线第 4 条，模板改为"完整的驱动文件" |
| 每门语言的配置 | 编译命令 | 编译命令，加上用户文件名，个别语言还有固定的项目文件（C#） |

## 6. 参照：多语言约束与两家平台的实测

### 6.1 什么必须写在用户代码前面

"实测"是在本机或 Docker 中把用户代码放在文件最前、驱动代码接在后面，看能否编译运行；其余按语言规范判断。

| 语言 | 必须出现在文件开头的东西 | 用户代码放在最前能否成立 | 依据 |
|---|---|---|---|
| Go | `package main`，且 `import` 必须在所有声明之前 | ❌ `expected 'package', found 'func'` | 实测 |
| Java | `import` 必须在类型声明之前 | ❌ `需要 class、interface、enum 或 record` | 实测 |
| Python | `from __future__` 必须是第一条语句；类型注解（`List[int]`）在 `def` 时就要求名字已导入 | ❌ `NameError: name 'List' is not defined` | 实测 |
| PHP | `<?php` 开标签；`declare`、`namespace` 必须是第一条语句 | ❌ 没有开标签时用户代码被原样输出 | 实测 |
| Kotlin、C#、Dart、Haskell、Erlang、Racket | `package`/`import`/`using`/`module`/`-module`/`#lang` 在文件头 | ❌ | 规范 |
| Rust | crate 级属性 `#![...]` 必须在最前；`use` 可以放在任意位置 | ⚠️ | 规范 |
| C / C++ | `#include` 可以放在任意位置，但用户代码要用的头文件必须在它前面 | ⚠️ 只有让用户自己写 include 才行 | 实测 |
| JavaScript（ESM）、Ruby | `import`、`require` 可以放在任意位置 | ✅ | 实测 |

所以多文件只解决了"驱动代码的 import 放哪"，**用户文件自己仍需要前缀**（用户不写 import 就能用常用库，也靠前缀）。

### 6.2 LeetCode（leetcode.cn，2026-10-17 实测）

在"两数之和"上用"运行"提交探测代码：打印 `__FILE__`/`__LINE__`、`f_lineno`、栈帧行号，看实际文件；再分别制造用户代码与驱动代码的错误。

| 语言 | 实际文件 | 用户代码前的行数 | 用户看到的位置 |
|---|---|---|---|
| C++（clang） | `prog_joined.cpp` | 9 | `Line 4: Char 17`，源码摘录行号也改成 4 |
| Python 3 | `prog_joined.py` | 58 | `Line 5 in twoSum (Solution.py)` |
| Java | `prog_joined.java`，驱动在 `__Driver__.java` | 49 | `at line 5, Solution.twoSum`；驱动错误标 `[in __Driver__.java]` |

证据：C++ 运行时错误显示为 `Line 5: Char 11: runtime error ... (solution.cpp)`，紧接着的 UBSan 摘要却漏改，露出 `prog_joined.cpp:14:11`（5 + 9）；
Python 自己捕获异常打印到标准输出的原生 traceback 是 `prog_joined.py, line 63`，平台展示的是 `Line 8`。结论：前缀直接拼在源文件里，
事后按语言改写报错行号；C++、Python 单文件，Java 驱动另成文件；驱动代码不保密，出错时源码行直接显示。

### 6.3 LintCode（lintcode.com，2026-10-17 实测）

在"A + B 问题"上用"运行测试数据"提交 Java 探测代码：

| 试验 | 平台展示 | 说明 |
|---|---|---|
| 不写 import 用 `ArrayList`，第 4 行把行号打印到标准输出 | 编译通过；`LINE4:9` | 文件里用户代码前有 5 行（import） |
| 同时把调用栈打印到标准输出 | `Solution.aplusb(Solution.java:10)`、`Main.main(Main.java:19)`；工作目录 `/code` | 驱动在单独的 `Main.java` |
| 用户第 3 行写 `。` | `/code/Solution.java:3: error: illegal character: '。'` | 编译错误的 8 被改写成 3 |
| 用户第 4 行数组越界 | `at Solution.aplusb(Solution.java:4)`、`at Main.main(Main.java:19)` | 异常栈帧改写；驱动文件不改 |
| 用户定义 `class List {}` | `/code/Main.java:12: error: type List does not take parameters`，并显示驱动源码 | 前缀是通配导入；两个文件共用一个包 |

结果面板下方有平台自己的说明："评测过程会影响此处显示的输出结果，但不会影响代码本身输出的值"。结论：**多文件 + 用户文件前缀 +
按用户文件名改写报错行号，标准输出不改**。这就是本工作选定的方案。

## 7. 统一改写的原型实测

用一个不区分语言的改写器（`rewrite(text, 用户文件名, 前缀行数)`）验证"按用户文件名统一改写"是否可行：
13 门语言的用户文件前都加 3 行前缀，制造与 §3 相同的错误，取原始输出改写，检查每处用户文件行号是否回到用户自己的行号。

改写器只认四种位置写法和一种源码摘录：

| 写法 | 出现在 |
|---|---|
| `文件:行` | GCC、clang、Go、Rust、Java、Kotlin、Swift、JavaScript、Ruby、PHP |
| `文件(行,列)` | C#、TypeScript |
| `文件", line 行` | Python |
| `文件 on line 行` | PHP |
| 紧跟在用户文件位置后的 `  行 \|` 源码摘录 | GCC、Rust、Swift |

结果：**13 门全部换算正确**，驱动文件的行号保持原样。

| 语言 | 原始输出里的用户文件行号 | 改写后 |
|---|---|---|
| C | 10（摘录 10） | 7（摘录 7） |
| C++ | 10（摘录 10） | 7（摘录 7） |
| Java | 11 | 8 |
| Python | 9 | 6 |
| Go | 10 | 7 |
| JavaScript | 12 | 9 |
| TypeScript | 8 | 5 |
| C# | 8 | 5 |
| Kotlin | 9 | 6 |
| Rust | 11（摘录 11） | 8（摘录 8） |
| Swift | 8（摘录 6–10） | 5（摘录 3–7） |
| PHP | 15 | 12 |
| Ruby | 10、9 | 7、6（第 6 行是外层的 `each_with_index`） |

改写前后对照（Swift 的多行摘录、Python 与 C# 的沙箱路径）：

```text
原始  solution.swift:8:58: error: cannot find 'missing' in scope
       7 |         for (i, x) in nums.enumerated() {
       8 |             if let j = seen[target - x] { return [j, i + missing] }
改写  solution.swift:5:58: error: cannot find 'missing' in scope
       4 |         for (i, x) in nums.enumerated() {
       5 |             if let j = seen[target - x] { return [j, i + missing] }

原始    File "/w/solution.py", line 9, in twoSum
改写    File "solution.py", line 6, in twoSum

原始  /w/Solution.cs(8,86): error CS0103: The name 'missing' does not exist in the current context [/w/Main.csproj]
改写  Solution.cs(5,86): error CS0103: The name 'missing' does not exist in the current context [/w/Main.csproj]
```

原型暴露的两点已写进设计（design.md §5.2）：摘录行号改写后要保持原宽度（`10 |` → ` 7 |`）；驱动文件与项目文件的路径
（`/w/Main.py`、`/w/Main.csproj`）不归用户文件规则管，要由 judge 按沙箱工作目录统一去掉。

## 8. 结论

2026-10-17 用户选定：**驱动代码单独成文件，用户文件 = 语言前缀 + 用户代码，按用户文件名统一改写报错行号**；
前缀每门语言一份、所有题目复用；改写放在 judging-service，驱动代码不下发也不遮掩。完整决定见 design.md §9。
第 4 节第 4 条（驱动代码按"题目 × 语言"手写）在加第二门语言时重新评估。
