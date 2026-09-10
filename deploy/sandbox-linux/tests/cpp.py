#!/usr/bin/env python3
"""真实 g++ 编译、产物读取和新执行组运行；输入和产物都经过 helper。"""
import json
import os
import sys
from client import call,request
path=sys.argv[1]
assert path.startswith('/run/cherry-sandbox-test-')
os.setgroups([]);os.setgid(61001);os.setuid(61001)
source=b'#include <iostream>\nint main(){std::cout << "cherry-linux-cpp" << std::endl;}\n'
req,data=request(['g++','-std=c++17','-O2','main.cpp','-o','program'],inputs=[('main.cpp',source,False)],outputs=['program'],cpuNs=3000000000)
facts,out,err,files=call(path,req,data)
print(json.dumps(dict(phase='compile',facts=facts,stderr=err.decode(errors='replace')[:2000])),flush=True)
assert not facts['Reason'] and facts['ExitCode']==0 and files.get('program'),facts
req,data=request(['program'],inputs=[('program',files['program'],True)])
facts,out,err,_=call(path,req,data)
print(json.dumps(dict(phase='execute',facts=facts,stdout=out.decode(errors='replace'),stderr=err.decode(errors='replace')[:1000])),flush=True)
assert not facts['Reason'] and facts['ExitCode']==0 and out==b'cherry-linux-cpp\n'
print('C++ compile / artifact transfer / fresh execution assertions passed',flush=True)
