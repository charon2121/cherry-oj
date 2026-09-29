"""Fixed resource ownership shared by rendering and administration."""
from pathlib import Path

ETC = Path('/etc/cherry-sandbox')
STATE = Path('/var/lib/cherry-sandbox')
UNITS = ('cherry-sandbox.slice', 'cherry-sandbox.service', 'cherry-sandbox-judge.service')
# 执行器按「基数 + box 序号」分配身份：payload 与 init 各占一段连续编号，box 最多 4 个。
BOXES = 4
ACCOUNTS = {'cherry-sandbox': 61001, 'cherry-judge': 61010,
            **{'cherry-payload-' + str(i): 61002 + i for i in range(BOXES)},
            **{'cherry-init-' + str(i): 61006 + i for i in range(BOXES)}}
PAYLOAD_UID, INIT_UID = 61002, 61006
GROUP = '/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice'
# 执行器（setuid-root）与 sandbox HTTP 服务装在同一个 release 里。
EXECUTOR = STATE / 'current/libexec/sandbox'
BOXES_ROOT = STATE / 'service/boxes'
# All byte and CPU values are explicit; period 100ms is fixed by the units.
# sandbox 服务的委派子树分两支：supervisor 放服务进程与执行器，jobs 放各次执行的组。
BUDGETS = {'': (1536 << 20, 384, '200000 100000'),
           '/cherry-sandbox.service': (896 << 20, 224, '150000 100000'),
           '/cherry-sandbox-judge.service': (384 << 20, 96, '50000 100000'),
           '/cherry-sandbox.service/supervisor': (256 << 20, 64, '50000 100000'),
           '/cherry-sandbox.service/jobs': (640 << 20, 160, '100000 100000')}
