import json
import tempfile
import unittest
from pathlib import Path

import toolchains


def lock(language, packages):
    return {'version': 1, 'architecture': 'amd64', 'layout': 'usr-merged', 'source': 'test', 'language': language,
            'roots': [], 'packages': [{'file': f, 'package': f.split('_')[0], 'version': '1', 'architecture': 'amd64',
                                       'sha256': sha} for f, sha in packages]}


class ToolchainsTest(unittest.TestCase):
    def write(self, directory, name, document):
        path = Path(directory) / f'{name}.lock.json'
        path.write_text(json.dumps(document))
        return path

    def test_union_deduplicates_shared_packages(self):
        with tempfile.TemporaryDirectory() as temp:
            a = self.write(temp, 'c', lock('c', [('libc6_1_amd64.deb', 'a' * 64), ('gcc_1_amd64.deb', 'b' * 64)]))
            b = self.write(temp, 'cpp', lock('cpp', [('libc6_1_amd64.deb', 'a' * 64), ('g++_1_amd64.deb', 'c' * 64)]))
            union = toolchains.union([a, b])
            self.assertEqual([p['file'] for p in union['packages']],
                             ['g++_1_amd64.deb', 'gcc_1_amd64.deb', 'libc6_1_amd64.deb'])

    def test_union_refuses_locks_from_different_snapshots(self):
        with tempfile.TemporaryDirectory() as temp:
            a = self.write(temp, 'c', lock('c', [('libc6_1_amd64.deb', 'a' * 64)]))
            b = self.write(temp, 'cpp', lock('cpp', [('libc6_1_amd64.deb', 'f' * 64)]))
            with self.assertRaises(ValueError):
                toolchains.union([a, b])

    def test_lock_file_name_must_be_its_language(self):
        with tempfile.TemporaryDirectory() as temp:
            self.write(temp, 'c', lock('cpp', []))
            with self.assertRaises(ValueError):
                toolchains.locks(Path(temp))
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                toolchains.locks(Path(temp))

    def test_prune_removes_only_unlisted_packages(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            union = self.write(temp, 'c', lock('c', [('gcc_1_amd64.deb', 'b' * 64)]))
            packages = root / 'packages'
            packages.mkdir()
            for name in ('gcc_1_amd64.deb', 'gcc_0_amd64.deb', 'notes.txt'):
                (packages / name).write_bytes(b'x')
            toolchains.prune(union, packages)
            self.assertEqual(sorted(p.name for p in packages.iterdir()), ['gcc_1_amd64.deb', 'notes.txt'])

    def test_repository_locks_union_into_one_consistent_set(self):
        found = toolchains.locks()
        self.assertIn('cpp', found)
        toolchains.union(list(found.values()))


if __name__ == '__main__':
    unittest.main()
