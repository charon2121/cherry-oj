#!/usr/bin/env python3
"""验证运行时归档的来源、路径、链接、资源边界与命令接入。"""
from __future__ import annotations

import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import artifacts


def record(content: bytes = b'archive') -> dict:
    return dict(file='runtime.tar.gz', url='https://runtime.example/v1/runtime.tar.gz',
                sha256=hashlib.sha256(content).hexdigest(), sizeBytes=len(content),
                stripPrefix='runtime', target='usr/lib/runtime', bin={'runtime': 'bin/runtime'})


def write_tar(path: Path, entries: list[tuple[str, bytes | str, bytes]]) -> artifacts.Archive:
    with tarfile.open(path, 'w:gz') as stream:
        for name, content, kind in entries:
            info = tarfile.TarInfo(name)
            info.type = kind
            info.mode = 0o6777
            if kind == tarfile.REGTYPE:
                info.size = len(content)
                stream.addfile(info, io.BytesIO(content))
            elif kind in (tarfile.SYMTYPE, tarfile.LNKTYPE):
                info.linkname = content
                stream.addfile(info)
            else:
                stream.addfile(info)
    return artifacts.Archive.parse(record(path.read_bytes()))


class ArchiveTests(unittest.TestCase):
    def test_official_build_number_is_allowed_only_in_top_directory_name(self) -> None:
        archive = artifacts.Archive.parse(dict(record(), stripPrefix='jdk-21.0.13+9'))
        self.assertEqual(archive.strip_prefix, 'jdk-21.0.13+9')
        for fields in ({'stripPrefix': '../jdk+9'}, {'file': 'runtime+9.tar.gz'},
                       {'bin': {'runtime+9': 'bin/runtime'}}):
            with self.subTest(fields=fields), self.assertRaises(ValueError):
                artifacts.Archive.parse(dict(record(), **fields))

    def test_complete_tree_and_license_are_preserved_and_command_is_relative(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = write_tar(root / 'runtime.tar.gz', [
                ('runtime/bin/real', b'program', tarfile.REGTYPE),
                ('runtime/bin/runtime', 'real', tarfile.SYMTYPE),
                ('runtime/LICENSE', b'license', tarfile.REGTYPE),
            ])
            (root / 'rootfs').mkdir()
            artifacts.unpack(archive, root / archive.file, root / 'rootfs')
            command = root / 'rootfs/usr/bin/runtime'
            self.assertEqual(command.readlink().as_posix(), '../lib/runtime/bin/runtime')
            self.assertEqual(command.read_bytes(), b'program')
            self.assertEqual((root / 'rootfs/usr/lib/runtime/LICENSE').read_bytes(), b'license')
            self.assertEqual(command.stat().st_mode & 0o7777, 0o755)

    def test_invalid_declarations_and_unknown_versions_are_rejected(self) -> None:
        for key, value in [('sizeBytes', True), ('sizeBytes', 0), ('sizeBytes', 1 << 29),
                           ('sha256', 'bad'), ('url', 'http://runtime.example/file'),
                           ('url', 'https://user@runtime.example/file'),
                           ('url', 'https://runtime.example/file?latest=1'),
                           ('target', '../outside'), ('file', '../runtime.tar.gz'),
                           ('stripPrefix', 'a/b'), ('bin', {'../bad': 'bin/runtime'}),
                           ('bin', {'runtime': '../outside'})]:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                artifacts.Archive.parse(dict(record(), **{key: value}))
        for mutation in ('version', 'missing', 'duplicate', 'overlap', 'unknown'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as temporary:
                lock = Path(temporary) / 'lock.json'
                data = dict(version=2, architecture='amd64', source='test', archives=[record()],
                            packages=[dict(file='base_1_amd64.deb', sha256='a' * 64)])
                if mutation == 'version': data['version'] = 1
                elif mutation == 'missing': del data['archives']
                elif mutation == 'duplicate': data['archives'].append(record())
                elif mutation == 'overlap':
                    data['archives'].append(dict(record(), file='other.tar.gz', target='usr/lib/runtime/other', bin={}))
                else: data['archives'][0]['installScript'] = 'run me'
                lock.write_text(json.dumps(data))
                with self.assertRaises(ValueError): artifacts.read_lock(lock)

    def test_union_lock_cannot_be_used_as_a_language_build_lock(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'union.json'
            path.write_text(json.dumps(dict(version=2, architecture='amd64', source='union', archives=[],
                                           packages=[dict(file='base_1_amd64.deb', sha256='a' * 64)])))
            artifacts.read_lock(path)
            with self.assertRaises(ValueError): artifacts.read_lock(path, for_build=True)

    def test_host_escape_links_special_files_and_duplicate_entries_fail(self) -> None:
        bad = [
            [('/runtime/bin/runtime', b'x', tarfile.REGTYPE)],
            [('runtime/../outside', b'x', tarfile.REGTYPE)],
            [('other/bin/runtime', b'x', tarfile.REGTYPE)],
            [('runtime/bin/runtime', '/etc/passwd', tarfile.SYMTYPE)],
            [('runtime/bin/runtime', '../../../../outside', tarfile.SYMTYPE)],
            [('runtime/bin/runtime', 'runtime/LICENSE', tarfile.LNKTYPE)],
            [('runtime/device', b'', tarfile.CHRTYPE)],
            [('runtime/pipe', b'', tarfile.FIFOTYPE)],
            [('runtime/bin/runtime', b'a', tarfile.REGTYPE), ('runtime/bin/runtime', b'b', tarfile.REGTYPE)],
            [('runtime/bin', '../lib', tarfile.SYMTYPE), ('runtime/bin/runtime', b'x', tarfile.REGTYPE)],
            [('runtime/bin/runtime', 'runtime', tarfile.SYMTYPE)],
        ]
        for entries in bad:
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive = write_tar(root / 'runtime.tar.gz', entries)
                (root / 'rootfs').mkdir()
                with self.assertRaises(ValueError): artifacts.unpack(archive, root / archive.file, root / 'rootfs')
                self.assertFalse((root / 'outside').exists())

    def test_existing_content_and_symlink_ancestors_are_never_overwritten(self) -> None:
        for kind in ('target', 'ancestor', 'command'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive = write_tar(root / 'runtime.tar.gz', [('runtime/bin/runtime', b'x', tarfile.REGTYPE)])
                fs = root / 'rootfs'
                fs.mkdir()
                if kind == 'ancestor':
                    (root / 'outside').mkdir()
                    (fs / 'usr').symlink_to(root / 'outside')
                else:
                    (fs / 'usr/bin').mkdir(parents=True)
                    if kind == 'target': (fs / 'usr/lib/runtime').mkdir(parents=True)
                    else: (fs / 'usr/bin/runtime').write_bytes(b'old')
                with self.assertRaises(ValueError): artifacts.unpack(archive, root / archive.file, fs)
                if kind == 'command': self.assertEqual((fs / 'usr/bin/runtime').read_bytes(), b'old')

    def test_digest_size_and_expansion_limits_are_checked_before_accepting_content(self) -> None:
        for kind in ('digest', 'size', 'expanded', 'entries'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive = write_tar(root / 'runtime.tar.gz', [('runtime/bin/runtime', b'large', tarfile.REGTYPE)])
                if kind in ('digest', 'size'):
                    document = record((root / archive.file).read_bytes())
                    if kind == 'digest': document['sha256'] = '0' * 64
                    else: document['sizeBytes'] += 1
                    archive = artifacts.Archive.parse(document)
                (root / 'rootfs').mkdir()
                with patch.object(artifacts, 'EXPANDED_LIMIT', 1 if kind == 'expanded' else 1024), \
                        patch.object(artifacts, 'ENTRY_LIMIT', 0 if kind == 'entries' else 32), self.assertRaises(ValueError):
                    artifacts.unpack(archive, root / archive.file, root / 'rootfs')


if __name__ == '__main__':
    unittest.main()
