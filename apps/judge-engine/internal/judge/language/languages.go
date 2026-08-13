package language

import "slices"

type Language struct {
	Name             string
	SourceName       string   // 源码文件名
	Compile          []string // 编译命令
	CompiledArtifact string   // 编译后产物文件名
	Run              []string // 运行命令
}

var registry = map[string]Language{
	"cpp": {
		Name:             "cpp",
		SourceName:       "Main.cpp",
		Compile:          []string{"g++", "Main.cpp", "-o", "Main", "-O2", "-std=c++17"},
		CompiledArtifact: "Main",
		Run:              []string{"Main"}, // 不带 "./"
	},
	"python": {
		Name:       "python",
		SourceName: "Main.py",
		// 写 python3 而不是 /usr/bin/python3：解释器/编译器一律走 PATH。
		// 硬编码绝对路径会绕过部署环境的选择——macOS 上 /usr/bin/python3 是
		// 系统自带的 3.9，而机器上装的可能是 3.12，判出来的结果和用户本地不一致。
		// 命令名不含 "/" 且 workDir 里没有同名文件时，container 会交给 PATH 解析。
		Run: []string{"python3", "Main.py"}, // 没有 Compile
	},
	"java": {
		Name:       "java",
		SourceName: "Main.java",
		// javac 会为内部类/匿名类额外产出 Main$Node.class、Main$1.class……
		// 而 RunSpec.Artifacts 只能按确切文件名收，没有通配。只收 Main.class
		// 的话，用了内部类的提交会在运行时 NoClassDefFoundError——而且
		// A+B 这种简单题测不出来，等真有人写内部类才炸。
		// 所以编译完顺手打成一个 jar，产物就只有一个确定的名字。
		Compile:          []string{"sh", "-c", "javac Main.java && jar cf Main.jar *.class"},
		CompiledArtifact: "Main.jar",
		Run:              []string{"java", "-cp", "Main.jar", "Main"},
	},
}

// Get 按名字取语言配置。
//
// 返回的是**独立副本**：Language 虽然按值返回，但 Compile / Run 是切片，
// 切片头是值、底层数组却和 registry 共享。调用方改一下（或 append 到一个
// cap 有富余的切片上），改的就是全局 registry——污染发生在一次判题里，
// 症状出现在之后所有判题上，进程重启才恢复。
func Get(name string) (Language, bool) {
	lang, ok := registry[name]
	if !ok {
		return Language{}, false
	}
	lang.Compile = slices.Clone(lang.Compile)
	lang.Run = slices.Clone(lang.Run)
	return lang, true
}

func (l Language) NeedsCompile() bool { return len(l.Compile) > 0 }
