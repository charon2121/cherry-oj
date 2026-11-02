"""Upgrade, rollback and prune switch only recorded releases and restore each release's reviewed configuration."""
from contextlib import ExitStack
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import manage
from render import render

TOKEN = 'control-token-0123456789'


class UpgradeTests(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        self.root = Path(self.stack.enter_context(tempfile.TemporaryDirectory()))
        self.state = self.root / 'state'
        self.etc = self.state / 'etc'
        self.units = self.root / 'systemd'
        for path in (self.state, self.etc, self.units, self.state / 'releases'):
            path.mkdir()
        self.receipt_path = self.state / 'installation.json'
        self.commands = []
        self.fail_start = set()
        for key, value in [('STATE', self.state), ('ETC', self.etc), ('SYSTEMD', self.units),
                           ('RECEIPT', self.receipt_path), ('GROUP', str(self.root / 'cgroup'))]:
            self.stack.enter_context(patch.object(manage, key, value))
        # macOS tests use ordinary temporary directories; root ownership, setuid and systemd run on Linux.
        for name in ('protected', 'check_executor'):
            self.stack.enter_context(patch.object(manage, name))
        self.stack.enter_context(patch.object(manage.os, 'chown'))
        self.stack.enter_context(patch.object(manage, 'run', side_effect=self.command))
        # 部署清单里的路径是节点上的真实路径；这里只记下用了哪份审核材料。
        self.stack.enter_context(patch.object(manage, 'write_deployment', side_effect=lambda review: (
            self.etc / 'deployment.json').write_text(json.dumps({'review': review.name}))))
        self.install('v1')

    def command(self, *args):
        self.commands.append(args)
        if args[:2] == ('systemctl', 'start') and self.current() in self.fail_start:
            raise OSError('judge failed to start')
        return ''

    def current(self):
        return os.readlink(self.state / 'current').split('/')[-1]

    def receipt(self):
        return json.loads(self.receipt_path.read_text())

    def release(self, name, language='cpp'):
        source = self.root / ('source-' + name)
        for binary in ('bin/judge', 'libexec/sandbox'):
            (source / binary).parent.mkdir(parents=True, exist_ok=True)
            (source / binary).write_bytes(b'\x7fELF\x02\x01' + b'\0' * 12 + b'\x3e\x00' + name.encode())
        toolchain = source / 'toolchains' / language
        (toolchain / 'rootfs').mkdir(parents=True)
        (toolchain / 'manifest.json').write_text(json.dumps({'release': name}))
        (toolchain / 'packages.lock.json').write_text('{}')
        manifest = hashlib.sha256((toolchain / 'manifest.json').read_bytes()).hexdigest()
        review = self.root / ('review-' + name)
        render(review, name, 'node-1', 'http://127.0.0.1:18084', 'http://127.0.0.1:15051', {language: manifest},
               parallelism=2)
        return review, source

    def install(self, name):
        """装好第一个发布的样子：与 install() 走同一套步骤，跳过账号、系统检查与 systemd。"""
        review, source = self.release(name)
        receipt = dict(version=1, status='running', release=name, previous=None, releases=[name],
                       accounts={n: {'uid': uid} for n, uid in manage.read_plan(review)['accounts'].items()},
                       files={}, enabled=False)
        manage.place_release(source, name)
        (self.state / 'current').symlink_to(Path('releases') / name)
        for unit in manage.UNITS:
            (self.units / unit).write_bytes((review / unit).read_bytes())
            receipt['files'][str(self.units / unit)] = manage.digest(self.units / unit)
        manage.write_config(review, TOKEN, receipt)
        manage.save_operations(receipt)
        manage.save_review(review, name, receipt)
        manage.write_json(self.receipt_path, receipt, 0o600)

    def test_upgrade_switches_current_and_config_and_restarts(self):
        manage.upgrade(*self.release('v2', language='c'))
        receipt = self.receipt()
        self.assertEqual((self.current(), receipt['release'], receipt['previous']), ('v2', 'v2', 'v1'))
        self.assertEqual((receipt['status'], receipt['releases']), ('running', ['v1', 'v2']))
        judge = json.loads((self.etc / 'judge.json').read_text())
        # 令牌沿用已装的，配置换成新发布的（探针语言随工具链变）。
        self.assertEqual(judge['judge']['node']['controlToken'], TOKEN)
        self.assertEqual(judge['execution']['probeToolchain'], 'c')
        self.assertIn('/releases/v2/toolchains/c/', (self.etc / 'judge-start.json').read_text())
        self.assertEqual(receipt['files'][str(self.etc / 'judge.json')], manage.digest(self.etc / 'judge.json'))
        self.assertTrue((manage.reviews() / 'v2' / 'plan.json').is_file())
        stop = self.commands.index(('systemctl', 'stop', *reversed(manage.UNITS[1:])))
        self.assertEqual(self.commands[stop + 1][:2], ('systemctl', 'start'))

    def test_failed_start_switches_back_to_the_previous_release(self):
        self.fail_start.add('v2')
        with self.assertRaisesRegex(ValueError, 'switched back to v1'):
            manage.upgrade(*self.release('v2'))
        receipt = self.receipt()
        self.assertEqual((self.current(), receipt['release'], receipt['status']), ('v1', 'v1', 'running'))
        self.assertEqual(json.loads((self.etc / 'deployment.json').read_text()), {'review': 'v1'})
        # 新发布已经装好，留着供检查，之后可以 prune。
        self.assertIn('v2', receipt['releases'])

    def test_rollback_and_back_again(self):
        manage.upgrade(*self.release('v2'))
        manage.rollback()
        receipt = self.receipt()
        self.assertEqual((self.current(), receipt['release'], receipt['previous']), ('v1', 'v1', 'v2'))
        self.assertIn('/releases/v1/', (self.etc / 'judge-start.json').read_text())
        manage.rollback()
        self.assertEqual(self.current(), 'v2')

    def test_stopped_node_stays_stopped(self):
        receipt = self.receipt()
        receipt['status'] = 'stopped'
        manage.write_json(self.receipt_path, receipt, 0o600)
        manage.upgrade(*self.release('v2'))
        self.assertEqual(self.receipt()['status'], 'stopped')
        self.assertFalse(any(c[:2] == ('systemctl', 'start') for c in self.commands))

    def test_prune_keeps_current_and_previous_only(self):
        manage.upgrade(*self.release('v2'))
        manage.upgrade(*self.release('v3'))
        manage.prune()
        receipt = self.receipt()
        self.assertEqual(receipt['releases'], ['v2', 'v3'])
        self.assertEqual(sorted(p.name for p in (self.state / 'releases').iterdir()), ['v2', 'v3'])
        self.assertEqual(sorted(p.name for p in manage.reviews().iterdir()), ['v2', 'v3'])
        self.assertFalse(any('/reviews/v1/' in path for path in receipt['files']))
        manage.owned()  # 回执里剩下的文件都还在、摘要一致

    def test_upgrade_refuses_changes_that_need_a_reinstall(self):
        review, source = self.release('v2')
        render(self.root / 'wider', 'v3', 'node-1', 'http://127.0.0.1:18084', 'http://127.0.0.1:15051',
               {'cpp': 'a' * 64}, parallelism=3)
        with self.assertRaisesRegex(ValueError, 'parallelism'):
            manage.upgrade(self.root / 'wider', source)
        (review / manage.UNITS[1]).write_text('[Service]\nchanged\n')
        with self.assertRaisesRegex(ValueError, 'systemd units'):
            manage.upgrade(review, source)
        with self.assertRaisesRegex(ValueError, 'already installed'):
            manage.upgrade(self.root / 'review-v1', self.root / 'source-v1')
        self.assertEqual(self.commands, [('systemctl', 'show', unit, '-p', 'DropInPaths', '--value')
                                         for _ in range(3) for unit in manage.UNITS])

    def test_rollback_without_previous_is_refused(self):
        with self.assertRaisesRegex(ValueError, 'no previous release'):
            manage.rollback()


if __name__ == '__main__':
    unittest.main()
