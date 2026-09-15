// Package checker 比对选手输出和标准答案。
//
// 算法参考 HustOJ 的 compare_zoj（ZOJ 移植版）：**单遍、逐字节、流式**，
// 两个流并排推进，内存恒定，不限文件大小。
//
// HustOJ 里另有一版早期实现，把两份文件的 token 全部拼成一个大字符串再
// strcmp。那版有三个我们不想继承的问题：
//   - 有文件大小上限，且不论实际多大都先分配一整块
//   - fscanf("%s") 不限宽度，超长 token 直接缓冲区溢出
//   - 标准答案打不开时 return OJ_AC —— 少传一个 .out，全场 AC
//
// # 三种结论
//
//	token 序列不同         → WA
//	token 相同、空白不同    → 严格判 PE，宽松判 AC（Options.StrictWhitespace）
//	完全一致               → AC
//
// **空白差异永远不会变成 WA。** 答案的内容是对的，只是排版不同；判成 WA
// 会让选手去查一个根本不存在的算法错误。严不严格只影响「PE 还是 AC」这一档，
// 不影响「是不是 WA」。
package checker

import (
	"bufio"
	"io"
	"unicode/utf8"

	"cherry-oj/judge-engine/internal/contract"
)

// excerptBytes：Diff 里每行片段最多带回多少字节。
// 有的题一行就有几百万个数字，整行塞进响应没有意义。
const excerptBytes = 200

type Options struct {
	// StrictWhitespace：空白排布不一致时判 PE（true）还是 AC（false）。
	// 用结构体而不是裸 bool 参数——Compare(true, a, b) 得回来翻签名才知道 true 是什么。
	StrictWhitespace bool
}

// Compare 比对 got（选手输出）和 expected（标准答案）。
//
// 第二个返回值是第一处不同的位置，仅在 WA / PE 时有意义。
// 它总是把 Want 填上，要不要回传给用户由调用方按配置决定（judge.revealExpected）——
// 产出信息的地方不做权限判断。
func Compare(opts Options, got, expected io.Reader) (contract.Verdict, contract.Diff, error) {
	a, b := newScanner(got), newScanner(expected)

	wsEqual := true
	wsDiff := contract.Diff{} // 第一处空白差异的位置

	for {
		// 跳过两边的空白，顺带记录排布是否一致
		same, at, err := skipSpaces(a, b)
		if err != nil {
			return contract.VerdictSE, contract.Diff{}, err
		}
		if !same && wsEqual {
			wsEqual = false
			wsDiff = contract.Diff{Line: at}
		}

		ca, err := a.peek()
		if err != nil {
			return contract.VerdictSE, contract.Diff{}, err
		}
		cb, err := b.peek()
		if err != nil {
			return contract.VerdictSE, contract.Diff{}, err
		}

		if ca == eof && cb == eof {
			break // 两边同时读完 = token 序列一致
		}
		if ca == eof || cb == eof {
			return contract.VerdictWA, makeDiff(a, b), nil // 一边还有 token
		}

		equal, err := compareOneToken(a, b)
		if err != nil {
			return contract.VerdictSE, contract.Diff{}, err
		}
		if !equal {
			return contract.VerdictWA, makeDiff(a, b), nil
		}
	}

	switch {
	case wsEqual:
		return contract.VerdictAC, contract.Diff{}, nil
	case opts.StrictWhitespace:
		return contract.VerdictPE, wsDiff, nil
	default:
		return contract.VerdictAC, contract.Diff{}, nil
	}
}

// skipSpaces 把两个流推进到各自的下一个非空白字符，
// 返回：这两段空白是否逐字节一致、第一处不一致所在的行号。
//
// 这段对应 compare_zoj 的 find_next_nonspace：一边是空白另一边不是、
// 或者同为空白但字符不同（' ' vs '\t'、'\r\n' vs '\n'），都算排布不一致。
//
// ★ 行号必须在**检测到的当下**记，不能等函数返回后再取 a.line ——
// 那时候可能已经跨过好几个换行了（"3\n\n\n" vs "3\n" 会报到第 4 行）。
func skipSpaces(a, b *scanner) (same bool, diffLine int, err error) {
	same = true
	mark := func() {
		if same {
			same = false
			diffLine = a.line
		}
	}

	for {
		ca, err := a.peek()
		if err != nil {
			return false, 0, err
		}
		cb, err := b.peek()
		if err != nil {
			return false, 0, err
		}

		spaceA := ca != eof && isSpace(ca)
		spaceB := cb != eof && isSpace(cb)

		switch {
		case !spaceA && !spaceB:
			return same, diffLine, nil
		case spaceA && spaceB:
			if ca != cb {
				mark()
			}
			if _, err := a.next(); err != nil {
				return false, 0, err
			}
			if _, err := b.next(); err != nil {
				return false, 0, err
			}
		case spaceA:
			// 一边还有空白另一边已经到 token/EOF —— 排布必然不同
			mark()
			if _, err := a.next(); err != nil {
				return false, 0, err
			}
		default:
			mark()
			if _, err := b.next(); err != nil {
				return false, 0, err
			}
		}
	}
}

// compareOneToken 并排读完两边各自的一个 token（连续非空白），返回是否相同。
// 调用时两边都已确定停在非空白字符上。
func compareOneToken(a, b *scanner) (bool, error) {
	for {
		ca, err := a.peek()
		if err != nil {
			return false, err
		}
		cb, err := b.peek()
		if err != nil {
			return false, err
		}

		endA := ca == eof || isSpace(ca)
		endB := cb == eof || isSpace(cb)

		if endA && endB {
			return true, nil // 两个 token 同时结束且一路相同
		}
		if endA != endB {
			return false, nil // 一个 token 是另一个的前缀
		}
		if ca != cb {
			return false, nil
		}
		if _, err := a.next(); err != nil {
			return false, err
		}
		if _, err := b.next(); err != nil {
			return false, err
		}
	}
}

// isSpace 只认 ASCII 空白。
//
// 用字节而不是 rune 是安全的：UTF-8 多字节字符的每个字节都 >= 0x80，
// 不可能等于任何 ASCII 空白，所以逐字节比对不会把一个汉字劈开误判。
func isSpace(c int) bool {
	switch c {
	case ' ', '\t', '\n', '\r', '\v', '\f':
		return true
	}
	return false
}

const eof = -1

// scanner 是一个能回看一个字节的字节流，同时维护行号和当前行的片段。
type scanner struct {
	br   *bufio.Reader
	line int    // 当前行号，从 1 起
	cur  []byte // 当前行已读过的内容，截断到 excerptBytes
}

func newScanner(r io.Reader) *scanner {
	return &scanner{br: bufio.NewReader(r), line: 1}
}

// peek 看下一个字节但不消费，读完返回 eof。
func (s *scanner) peek() (int, error) {
	c, err := s.br.ReadByte()
	if err == io.EOF {
		return eof, nil
	}
	if err != nil {
		return 0, err
	}
	if err := s.br.UnreadByte(); err != nil {
		return 0, err
	}
	return int(c), nil
}

func (s *scanner) next() (int, error) {
	c, err := s.br.ReadByte()
	if err == io.EOF {
		return eof, nil
	}
	if err != nil {
		return 0, err
	}
	if c == '\n' {
		s.line++
		s.cur = s.cur[:0]
	} else if len(s.cur) < excerptBytes {
		s.cur = append(s.cur, c)
	}
	return int(c), nil
}

// lineExcerpt 返回当前行的片段：已读过的部分 + 读到行尾的剩余部分。
func (s *scanner) lineExcerpt() string {
	out := make([]byte, len(s.cur))
	copy(out, s.cur)
	for len(out) < excerptBytes {
		c, err := s.br.ReadByte()
		if err != nil || c == '\n' {
			break
		}
		if c != '\r' {
			out = append(out, c)
		}
	}
	return trimPartialRune(string(out))
}

// trimPartialRune 砍掉结尾那个被截断的多字节字符。
// 不处理的话，一个三字节汉字被砍成两半，前端显示成乱码方块。
func trimPartialRune(s string) string {
	for len(s) > 0 && !utf8.ValidString(s) {
		s = s[:len(s)-1]
	}
	return s
}

func makeDiff(a, b *scanner) contract.Diff {
	return contract.Diff{
		Line: a.line,
		Got:  a.lineExcerpt(),
		Want: b.lineExcerpt(),
	}
}
