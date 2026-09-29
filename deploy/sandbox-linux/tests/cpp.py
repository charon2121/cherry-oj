#!/usr/bin/env python3
"""真实 g++ 编译、产物读取和新执行组运行；输入和产物都经过执行器的 box。

    cpp.py <fixture>
"""
import json
from pathlib import Path
import sys

from executor_client import Executor, drop_to_service

base = Path(sys.argv[1])
assert base.parent == Path('/var/lib/cherry-sandbox-test')
drop_to_service()
executor = Executor(base)
source = b'#include <iostream>\nint main(){std::cout << "cherry-linux-cpp" << std::endl;}\n'
facts, out, err, files = executor.call(['g++', '-std=c++17', '-O2', 'main.cpp', '-o', 'program'],
                                       inputs=[('main.cpp', source, False)], outputs=['program'], cpu_ns=3000000000)
print(json.dumps(dict(phase='compile', facts=facts, stderr=err.decode(errors='replace')[:2000])), flush=True)
assert not facts['reason'] and facts['exitCode'] == 0 and files.get('program'), facts
facts, out, err, _ = executor.call(['program'], inputs=[('program', files['program'], True)])
print(json.dumps(dict(phase='execute', facts=facts, stdout=out.decode(errors='replace'),
                      stderr=err.decode(errors='replace')[:1000])), flush=True)
assert not facts['reason'] and facts['exitCode'] == 0 and out == b'cherry-linux-cpp\n'
print('C++ compile / artifact transfer / fresh execution assertions passed', flush=True)
