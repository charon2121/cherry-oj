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
            self.assertEqual(len(set(plan['accounts'].values())),10)
            self.assertFalse(plan['startAutomatically'])
            self.assertFalse(plan['reboot'])
            helper=json.loads((root/'helper.json').read_text())
            self.assertEqual(helper['Parallelism'],1)
            self.assertNotEqual(helper['ServiceUID'],helper['PayloadUID'])
            manifest=json.loads((root/'deployment.template.json').read_text())
            self.assertEqual(len(manifest['limits']),24)
            self.assertEqual(len(manifest['files']),11)
            self.assertNotIn('judgeConfig',manifest['files'])
            for path, value in manifest['limits'].items():
                self.assertNotIn('max',value)
                if path.endswith('memory.swap.max'):
                    self.assertEqual(value,'0')
            judge=(root/'cherry-sandbox-judge.service').read_text()
            self.assertIn('User=cherry-judge',judge)
            self.assertIn('Group=cherry-judge',judge)
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
        self.assertTrue(manage.unit_conflicts('cherry-sandbox.service',facts))
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
