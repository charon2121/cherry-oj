package policy

import (
	"encoding/binary"
	"testing"
)

// 只解释生成器使用的 classic BPF 指令，测试最终字节码的行为而非重复白名单生成逻辑。
func evaluate(t *testing.T, p []Instruction, arch, nr uint32, arg uint64) uint32 {
	t.Helper()
	data := make([]byte, 64)
	binary.LittleEndian.PutUint32(data, nr)
	binary.LittleEndian.PutUint32(data[4:], arch)
	binary.LittleEndian.PutUint64(data[16:], arg)
	var a uint32
	for pc := 0; pc < len(p); pc++ {
		in := p[pc]
		switch in.Code {
		case loadWord:
			if int(in.K)+4 > len(data) {
				t.Fatal("load out of bounds")
			}
			a = binary.LittleEndian.Uint32(data[in.K:])
		case jumpEqual:
			if a == in.K {
				pc += int(in.JT)
			} else {
				pc += int(in.JF)
			}
		case jumpSet:
			if a&in.K != 0 {
				pc += int(in.JT)
			} else {
				pc += int(in.JF)
			}
		case 0x54:
			a &= in.K
		case ret:
			return in.K
		default:
			t.Fatalf("unexpected opcode %x", in.Code)
		}
	}
	t.Fatal("program did not return")
	return 0
}

func TestAMD64PolicyBoundary(t *testing.T) {
	for _, profile := range []Profile{Command, Toolchain, Supervisor} {
		t.Run(string(profile), func(t *testing.T) {
			p, err := AMD64(profile)
			if err != nil {
				t.Fatal(err)
			}
			for _, tc := range []struct {
				name     string
				arch, nr uint32
				arg      uint64
				want     uint32
			}{
				{"read", auditAMD64, 0, 0, allow},
				{"other ABI", 0x40000003, 0, 0, killProcess},
				{"x32", auditAMD64, 0x40000000, 0, killProcess},
				{"socket", auditAMD64, 41, 0, killProcess},
				{"mount", auditAMD64, 165, 0, killProcess},
				{"ptrace", auditAMD64, 101, 0, killProcess},
				{"bpf", auditAMD64, 321, 0, killProcess},
				{"io_uring", auditAMD64, 425, 0, killProcess},
				{"setuid", auditAMD64, 105, 0, killProcess},
				{"capset", auditAMD64, 126, 0, killProcess},
				{"setns", auditAMD64, 308, 0, killProcess},
				{"unshare", auditAMD64, 272, 0, killProcess},
				{"hardlink", auditAMD64, 86, 0, killProcess},
				{"fork clone", auditAMD64, 56, 17, allow},
				{"thread clone", auditAMD64, 56, 0x100 | 0x200 | 0x400 | 0x800 | 0x10000 | 0x40000 | 0x80000 | 0x100000 | 0x200000, allow},
				{"userns clone", auditAMD64, 56, 0x10000000 | 17, killProcess},
				{"netns clone", auditAMD64, 56, 0x40000000 | 17, killProcess},
				{"parent clone", auditAMD64, 56, 0x8000 | 17, killProcess},
				{"high bits clone", auditAMD64, 56, 1<<40 | 17, killProcess},
				{"exit signal clone", auditAMD64, 56, 9, killProcess},
				{"clone3", auditAMD64, 435, 0, errno | 38},
				{"pidfd feature probe", auditAMD64, 434, 0, errno | 38},
				{"ioctl", auditAMD64, 16, 0, errno | 25},
				{"query nnp", auditAMD64, 157, 39, allow},
				{"set dumpable", auditAMD64, 157, 4, killProcess},
				{"unknown", auditAMD64, 99999, 0, killProcess},
			} {
				t.Run(tc.name, func(t *testing.T) {
					if got := evaluate(t, p, tc.arch, tc.nr, tc.arg); got != tc.want {
						t.Fatalf("got %#x want %#x", got, tc.want)
					}
				})
			}
			want := uint32(allow)
			if profile == Supervisor {
				want = killProcess
			}
			if got := evaluate(t, p, auditAMD64, 59, 0); got != want {
				t.Fatalf("execve=%x want %x", got, want)
			}
		})
	}
	if _, err := AMD64("unknown"); err == nil {
		t.Fatal("unknown profile accepted")
	}
}
