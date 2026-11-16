#!/usr/bin/env python3
"""证明构建源码来自指定提交，而不是工作区或遗留产物。"""

from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

from release import snapshot


class SourceTests(unittest.TestCase):
    def test_dirty_source_untracked_configuration_and_old_outputs_are_not_exported(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            repo = root / 'repo'
            repo.mkdir()
            subprocess.run(['git', 'init', '-q', str(repo)], check=True)
            (repo / 'source.txt').write_text('committed')
            (repo / '.gitignore').write_text('target/\n.env.local\n')
            subprocess.run(['git', 'add', '.'], cwd=repo, check=True)
            subprocess.run(['git', '-c', 'core.hooksPath=/dev/null', '-c', 'user.name=Fixture',
                            '-c', 'user.email=fixture@example.invalid', 'commit', '-qm', 'fixture'], cwd=repo, check=True)
            sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=repo, text=True).strip()
            (repo / 'source.txt').write_text('dirty')
            (repo / '.env.local').write_text('private config')
            (repo / 'target').mkdir()
            (repo / 'target/old.jar').write_text('stale')
            (repo / 'untracked').write_text('untracked')
            exported = root / 'source'
            self.assertEqual(snapshot(repo, 'HEAD', exported), sha)
            self.assertEqual((exported / 'source.txt').read_text(), 'committed')
            self.assertFalse((exported / 'target').exists())
            self.assertFalse((exported / '.env.local').exists())
            self.assertFalse((exported / 'untracked').exists())
            self.assertEqual((repo / 'source.txt').read_text(), 'dirty')


if __name__ == '__main__':
    unittest.main()
