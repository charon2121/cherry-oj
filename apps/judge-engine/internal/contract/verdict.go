package contract

type Verdict string

const (
	VerdictAC  Verdict = "AC"  // 答案正确
	VerdictWA  Verdict = "WA"  // 答案错误
	VerdictTLE Verdict = "TLE" // 超时
	VerdictMLE Verdict = "MLE" // 超内存
	VerdictRE  Verdict = "RE"  // 运行错误
	VerdictCE  Verdict = "CE"  // 编译错误
	VerdictPE  Verdict = "PE"  // 格式错误
	VerdictSE  Verdict = "SE"  // 系统内部错误
	VerdictRAN Verdict = "RAN" // 已运行未比对（自定义测试无 expected）
)

func (v Verdict) IsValid() bool {
	switch v {
	case
		VerdictAC,
		VerdictWA,
		VerdictTLE,
		VerdictMLE,
		VerdictRE,
		VerdictCE,
		VerdictPE,
		VerdictSE,
		VerdictRAN:
		return true
	default:
		return false
	}
}
