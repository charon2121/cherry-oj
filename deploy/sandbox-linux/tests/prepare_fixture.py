#!/usr/bin/env python3
"""从本地交叉构建 probe 生成可追溯的最小 rootfs，不提供 C++ 工具链。"""
import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import sys
sys.dont_write_bytecode = True

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('bundle',type=Path)
args=parser.parse_args()
build_path=Path(__file__).resolve().parent.parent/'rootfs/build.py'
spec=importlib.util.spec_from_file_location('rootfs_builder',build_path)
builder=importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)
root=args.bundle/'rootfs'
root.mkdir(exist_ok=False)
(root/'usr/bin').mkdir(parents=True)
(root/'bin').mkdir()
for name in ('true','probe'):
    target=root/'usr/bin'/name
    shutil.copyfile(args.bundle/'probe',target)
    target.chmod(0o755)
manifest=builder.seal(root,'WORK-048 static Go test fixture; probe-sha256='+builder.digest(args.bundle/'probe'))
(args.bundle/'manifest.json').write_text(json.dumps(manifest))
print('helper-sha256='+builder.digest(args.bundle/'sandbox-helper'))
print('manifest-sha256='+builder.digest(args.bundle/'manifest.json'))
