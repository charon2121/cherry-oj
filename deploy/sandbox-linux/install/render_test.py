"""Exercise deployment boundaries without creating accounts or system resources."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import manage
from render import render


class DeploymentTests(unittest.TestCase):
    def test_review_plan_has_no_embedded_token_and_separate_identities(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'review'
            render(root,'work048-linux-v1','cherry-linux-1','http://127.0.0.1:18084',
                   'http://127.0.0.1:15051','a'*64)
            plan=json.loads((root/'plan.json').read_text())
            self.assertEqual(len(set(plan['accounts'].values())),3)
            self.assertFalse(plan['startAutomatically'])
            self.assertFalse(plan['reboot'])
            executor=dict(line.split('=',1) for line in (root/'executor.conf').read_text().splitlines())
            # 执行层的并发数必须等于执行器的 box 数：每次执行占用一个 box。
            judge=json.loads((root/'judge.json').read_text())
            self.assertEqual(judge['execution']['parallelism'],int(executor['box_count']))
            self.assertEqual(judge['execution']['boxesRoot'],executor['boxes'])
            # 执行器的调用方就是 judge：服务身份必须与 judge 单元的账户一致，且与 box 身份分离。
            self.assertEqual(int(executor['service_uid']),plan['accounts']['cherry-judge'])
            self.assertEqual(len({executor['service_uid'],executor['payload_uid'],executor['init_uid']}),3)
            start=json.loads((root/'judge-start.json').read_text())
            # 资源限制只加在每次执行的组上，judge 的启动配置不带总量上限。
            self.assertEqual(set(start),{'group','rootfs','manifest','manifestSha256','command'})
            self.assertEqual(start['group']+'/jobs',executor['cgroup'])
            self.assertEqual(start['command'][-1],str(manage.ETC/'judge.json'))
            manifest=json.loads((root/'deployment.template.json').read_text())
            self.assertNotIn('limits',manifest)
            self.assertEqual(len(manifest['files']),8)
            self.assertNotIn('judgeConfig',manifest['files'])
            unit=(root/'cherry-sandbox-judge.service').read_text()
            self.assertIn('User=cherry-judge',unit)
            self.assertIn('Group=cherry-judge',unit)
            self.assertIn('Delegate=cpu memory pids',unit)
            # setuid 执行器要求调用方单元不带 NoNewPrivileges 及其隐含项。
            for option in ('NoNewPrivileges','RestrictSUIDSGID','ProtectControlGroups','RestrictNamespaces'):
                self.assertNotIn(option+'=',unit)
            self.assertFalse((root/'cherry-sandbox.service').exists())
            self.assertIn('REPLACE_FROM_PRIVATE_TOKEN_FILE',(root/'judge.json').read_text())

    def test_rejects_path_escape_public_listener_and_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            for release,url in [('../escape','http://127.0.0.1:18084'),
                                ('v1','http://example.com:8084')]:
                with self.assertRaises(ValueError):
                    render(Path(temp)/'review',release,'node-1',url,
                           'http://127.0.0.1:15051','a'*64)
            with self.assertRaises(FileExistsError):
                render(Path(temp),'v1','node-1','http://127.0.0.1:18084',
                       'http://127.0.0.1:15051','a'*64)

    def test_operations_refuse_modified_installation_before_systemctl(self):
        with patch.object(manage,'owned',side_effect=ValueError('modified installation')), \
             patch.object(manage,'run') as command:
            for action in ('start','stop','uninstall'):
                with self.assertRaises(ValueError):
                    manage.operate(action)
            command.assert_not_called()

    def test_only_empty_inactive_implicit_slice_is_available(self):
        facts=dict(LoadState='loaded',ActiveState='inactive',Transient='no',
                   FragmentPath='',DropInPaths='',ControlGroup='')
        self.assertFalse(manage.unit_conflicts('cherry-sandbox.slice',facts))
        self.assertTrue(manage.unit_conflicts('cherry-sandbox-judge.service',facts))
        for key,value in [('FragmentPath','/etc/unit'),('DropInPaths','/etc/override'),
                          ('ControlGroup','/live'),('ActiveState','active'),('Transient','yes')]:
            changed={**facts,key:value}
            self.assertTrue(manage.unit_conflicts('cherry-sandbox.slice',changed))

    def test_start_failure_stops_only_owned_services(self):
        commands=[]
        def command(*args):
            commands.append(args)
            if args[1]=='start':
                raise OSError('dependency failed')
            return ''
        with patch.object(manage,'owned',return_value={'status':'installed'}), \
             patch.object(manage,'run',side_effect=command):
            with self.assertRaises(OSError):
                manage.operate('start')
        self.assertEqual(commands[-1],('systemctl','stop',*reversed(manage.UNITS[1:])))

    def test_parallelism_sets_box_count_judge_parallelism_and_account_count(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'review'
            render(root,'work048-linux-v1','cherry-linux-1','http://127.0.0.1:18084',
                   'http://127.0.0.1:15051','a'*64,parallelism=3)
            plan=json.loads((root/'plan.json').read_text())
            executor=dict(line.split('=',1) for line in (root/'executor.conf').read_text().splitlines())
            judge=json.loads((root/'judge.json').read_text())
            self.assertEqual(plan['parallelism'],3)
            self.assertEqual(int(executor['box_count']),3)
            self.assertEqual(judge['execution']['parallelism'],3)
            # 1 个服务账号 + 每个 box 一个 payload 和一个 init。
            self.assertEqual(len(plan['accounts']),1+2*3)
            self.assertEqual(plan['accounts']['cherry-payload-2'],int(executor['payload_uid'])+2)
            self.assertEqual(plan['accounts']['cherry-init-2'],int(executor['init_uid'])+2)
        for bad in (0,257,True):
            with tempfile.TemporaryDirectory() as temp:
                with self.assertRaises(ValueError):
                    render(Path(temp)/'review','work048-linux-v1','cherry-linux-1','http://127.0.0.1:18084',
                           'http://127.0.0.1:15051','a'*64,parallelism=bad)

    def test_max_parallelism_keeps_identity_ranges_apart(self):
        from layout import INIT_UID, JUDGE_UID, MAX_PARALLELISM, PAYLOAD_UID, accounts
        values=list(accounts(MAX_PARALLELISM).values())
        self.assertEqual(len(set(values)),len(values))
        self.assertEqual(len(values),1+2*MAX_PARALLELISM)
        self.assertLessEqual(PAYLOAD_UID+MAX_PARALLELISM,INIT_UID)
        self.assertNotIn(JUDGE_UID,range(PAYLOAD_UID,INIT_UID+MAX_PARALLELISM))

    def test_uninstall_preserves_data_and_accounts(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            for unit in manage.UNITS:
                (root/unit).touch()
            commands=[]
            with patch.object(manage,'SYSTEMD',root), \
                 patch.object(manage,'owned',return_value={'status':'stopped'}), \
                 patch.object(manage,'write_json') as receipt, \
                 patch.object(manage,'backup_units'), \
                 patch.object(manage,'run',side_effect=lambda *args:commands.append(args)):
                manage.operate('uninstall')
            self.assertFalse(any(root.iterdir()))
            self.assertEqual(receipt.call_args.args[1]['status'],'uninstalled')
            self.assertTrue(all(c[0]=='systemctl' for c in commands))
            self.assertFalse(any('userdel' in c or 'groupdel' in c for c in commands))


if __name__=='__main__':
    unittest.main()
