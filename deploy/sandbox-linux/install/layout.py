"""Fixed resource ownership shared by rendering and administration."""
from pathlib import Path

# 本项目在节点上的全部持久文件都在这一个根目录下；systemd 单元与 cgroup 按各自规则放在系统位置。
HOME = Path('/opt/cherry-oj')
STATE = HOME
ETC = HOME / 'etc'
UNITS = ('cherry-sandbox.slice', 'cherry-sandbox-judge.service')
JUDGE_UNIT = 'cherry-sandbox-judge.service'
# judge 在本进程内调用 setuid 执行器，它的身份就是执行器受信配置里的服务身份。
JUDGE_UID = 61010
# 执行器按「基数 + box 序号」分配身份：payload 与 init 各占一段连续编号，box 最多 4 个。
BOXES = 4
ACCOUNTS = {'cherry-judge': JUDGE_UID,
            **{'cherry-payload-' + str(i): 61002 + i for i in range(BOXES)},
            **{'cherry-init-' + str(i): 61006 + i for i in range(BOXES)}}
PAYLOAD_UID, INIT_UID = 61002, 61006
GROUP = '/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice'
JUDGE_GROUP = GROUP + '/' + JUDGE_UNIT
# 执行器（setuid-root）与 judge 装在同一个 release 里。
EXECUTOR = STATE / 'current/libexec/sandbox'
JUDGE_DATA = STATE / 'judge'
BOXES_ROOT = JUDGE_DATA / 'boxes'
BLOBS_ROOT = JUDGE_DATA / 'blobs'
# All byte and CPU values are explicit; period 100ms is fixed by the units.
# judge 服务的委派子树分两支：supervisor 放 judge 进程与执行器，jobs 放各次执行的组。
BUDGETS = {'': (1536 << 20, 384, '200000 100000'),
           '/' + JUDGE_UNIT: (1280 << 20, 320, '200000 100000'),
           '/' + JUDGE_UNIT + '/supervisor': (640 << 20, 160, '100000 100000'),
           '/' + JUDGE_UNIT + '/jobs': (640 << 20, 160, '100000 100000')}
