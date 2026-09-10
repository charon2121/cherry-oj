"""Fixed resource ownership shared by rendering and administration."""
from pathlib import Path

ETC = Path('/etc/cherry-sandbox')
STATE = Path('/var/lib/cherry-sandbox')
UNITS = ('cherry-sandbox.slice', 'cherry-sandbox-helper.service',
         'cherry-sandbox.service', 'cherry-sandbox-judge.service')
ACCOUNTS = {'cherry-sandbox': 61001, 'cherry-judge': 61010,
            **{'cherry-payload-' + str(i): 61002 + 2*i for i in range(4)},
            **{'cherry-init-' + str(i): 61003 + 2*i for i in range(4)}}
GROUP = '/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice'
# All byte and CPU values are explicit; period 100ms is fixed by the units.
BUDGETS = {'': (1536 << 20, 384, '200000 100000'),
           '/cherry-sandbox-helper.service': (768 << 20, 192, '100000 100000'),
           '/cherry-sandbox.service': (256 << 20, 96, '50000 100000'),
           '/cherry-sandbox-judge.service': (384 << 20, 96, '50000 100000')}

BUDGETS.update({
    '/cherry-sandbox-helper.service/supervisor': (128 << 20, 32, '50000 100000'),
    '/cherry-sandbox-helper.service/jobs': (640 << 20, 160, '100000 100000'),
})
