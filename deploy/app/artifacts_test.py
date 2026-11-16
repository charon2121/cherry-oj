#!/usr/bin/env python3
"""验证应用来源绑定、旧发布兼容及归档拒绝边界。"""

from __future__ import annotations

import hashlib
import io
import tarfile
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import artifacts
from fixtures import bundle, legacy


class ArtifactsTests(unittest.TestCase):
    def test_valid_bundle_binds_source_files_and_modes(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive, expected, name = bundle(root)
            manifest = artifacts.unpack(archive, expected, root / 'release')
            self.assertEqual(manifest.release, name)
            self.assertEqual(manifest.source_sha, 'a' * 40)
            self.assertEqual((root / 'release' / artifacts.OPS).stat().st_mode & 0o777, 0o755)
            self.assertEqual(artifacts.migration_files(root / 'release')['user'],
                             {'BOOT-INF/classes/db/migration/V1__fixture.sql': hashlib.sha256(b'schema').hexdigest()})

    def test_legacy_can_only_be_read_as_an_installed_release_and_is_not_rewritten(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            legacy(root / 'legacy')
            path = root / 'legacy/release.json'
            original = path.read_bytes()
            with self.assertRaises(ValueError): artifacts.verify_tree(path.parent)
            self.assertTrue(artifacts.verify_tree(path.parent, legacy=True).legacy)
            self.assertEqual(path.read_bytes(), original)

    def test_tampered_extra_missing_and_linked_files_are_rejected(self) -> None:
        for kind in ('tamper', 'extra', 'missing', 'link', 'directory-link', 'extra-directory'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                legacy(root / 'release')
                path = root / 'release/web/index.html'
                if kind == 'tamper': path.write_text('changed')
                elif kind == 'extra': (path.parent / 'extra.js').write_text('extra')
                elif kind == 'missing': path.unlink()
                elif kind == 'link':
                    path.unlink()
                    path.symlink_to(root / 'outside')
                elif kind == 'directory-link': (path.parent / 'linked').symlink_to(root)
                else: (path.parent / 'empty').mkdir()
                with self.assertRaises(ValueError): artifacts.verify_tree(root / 'release', legacy=True)

    def test_manifest_missing_unknown_and_coerced_fields_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive, expected, _ = bundle(root)
            artifacts.unpack(archive, expected, root / 'release')
            original = artifacts.read_json(root / 'release/release.json')
            for key, value in [('version', True), ('version', 2), ('sourceSha', None),
                               ('release', '../bad'), ('artifactSetSha256', '0' * 64), ('unknown', 'field')]:
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    artifacts.Manifest.parse(dict(original, **{key: value}))
            for key in original:
                missing = dict(original)
                del missing[key]
                with self.subTest(missing=key), self.assertRaises((ValueError, KeyError)):
                    artifacts.Manifest.parse(missing)

    def test_archive_traversal_duplicates_links_devices_and_collisions_are_rejected(self) -> None:
        cases = [
            [('../outside', tarfile.REGTYPE)], [('/outside', tarfile.REGTYPE)],
            [('web/a', tarfile.REGTYPE), ('web/a', tarfile.REGTYPE)],
            [('web/link', tarfile.SYMTYPE)], [('web/link', tarfile.LNKTYPE)],
            [('web/device', tarfile.CHRTYPE)], [('web/pipe', tarfile.FIFOTYPE)],
            [('web/a', tarfile.REGTYPE), ('web/a/child', tarfile.REGTYPE)],
        ]
        for entries in cases:
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive = root / 'input.tar.gz'
                with tarfile.open(archive, 'w:gz') as output:
                    for name, kind in entries:
                        member = tarfile.TarInfo(name)
                        member.type = kind
                        member.linkname = '../../outside'
                        member.size = 1 if kind == tarfile.REGTYPE else 0
                        output.addfile(member, io.BytesIO(b'x') if member.size else None)
                with self.assertRaises((ValueError, OSError)):
                    artifacts.unpack(archive, artifacts.digest(archive), root / 'release')
                self.assertFalse((root / 'outside').exists())

    def test_oversize_digest_and_output_reuse_fail_without_clobbering(self) -> None:
        for kind in ('digest', 'package', 'expanded', 'count', 'reuse'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                archive, expected, _ = bundle(root)
                if kind == 'reuse':
                    (root / 'release').mkdir()
                    (root / 'release/keep').write_text('keep')
                with patch.object(artifacts, 'PACKAGE_LIMIT', 1 if kind == 'package' else 1 << 30), \
                        patch.object(artifacts, 'EXPANDED_LIMIT', 1 if kind == 'expanded' else 1 << 30), \
                        patch.object(artifacts, 'FILE_LIMIT', 1 if kind == 'count' else 16384), \
                        self.assertRaises((ValueError, OSError)):
                    artifacts.unpack(archive, '0' * 64 if kind == 'digest' else expected, root / 'release')
                if kind == 'reuse': self.assertEqual((root / 'release/keep').read_text(), 'keep')

    def test_duplicate_json_keys_are_not_silently_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'document.json'
            path.write_text('{"version":1,"version":2}')
            with self.assertRaises(ValueError): artifacts.read_json(path)

    def test_long_path_metadata_is_supported_but_oversized_headers_are_rejected_before_reading(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            info = artifacts.bounded_tar_info()
            header = tarfile.TarInfo('pax')
            header.type = tarfile.XHDTYPE
            header.size = artifacts.MANIFEST_LIMIT + 1
            with self.assertRaises(ValueError): info.frombuf(header.tobuf(), 'utf-8', 'strict')
            archive, expected, _ = bundle(root)
            manifest = artifacts.unpack(archive, expected, root / 'valid')
            name = 'web/' + 'long-' * 30 + '.js'
            path = root / 'valid' / name
            path.write_text('long asset')
            from release import package

            output = root / 'long-package'
            output.mkdir()
            inputs = {key: root / 'valid' / key for key in manifest.files}
            inputs[name] = path
            metadata = package('a' * 40, inputs, output)
            result = artifacts.unpack(output / 'app.tar.gz', metadata['sha256'], root / 'long-result')
            self.assertIn(name, result.files)


if __name__ == '__main__':
    unittest.main()
