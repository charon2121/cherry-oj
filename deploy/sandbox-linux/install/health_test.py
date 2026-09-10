"""A leftover socket pathname must not release dependent services before listen()."""
from pathlib import Path
import unittest
from unittest.mock import patch

import health


class ReadinessTests(unittest.TestCase):
    path = '/run/cherry-sandbox-helper/helper.sock'
    header = 'Num RefCount Protocol Flags Type St Inode Path\n'

    def test_accepts_live_stream_listener_only(self):
        for flags, kind, state, path, want in [
            ('00010000', '0001', '01', self.path, True),
            ('00000000', '0001', '01', self.path, False),
            ('00010000', '0002', '01', self.path, False),
            ('00010000', '0001', '03', self.path, False),
            ('00010000', '0001', '01', self.path + '.other', False),
        ]:
            with self.subTest(flags=flags, kind=kind, state=state, path=path):
                table = self.header + f'0: 00000002 00000000 {flags} {kind} {state} 123 {path}\n'
                self.assertEqual(health.unix_listener_present(table, self.path), want)

    def test_leftover_inode_without_kernel_listener_is_not_ready(self):
        with patch.object(Path, 'is_socket', return_value=True), \
             patch.object(Path, 'read_text', return_value=self.header):
            self.assertFalse(health.ready('helper'))

    def test_missing_socket_is_not_ready(self):
        with patch.object(Path, 'is_socket', return_value=False):
            self.assertFalse(health.ready('helper'))


if __name__ == '__main__':
    unittest.main()
