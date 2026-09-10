import unittest
from download import archive_path


class ArchiveLocationTests(unittest.TestCase):
    def test_epoch_in_local_apt_name_is_not_in_pool_filename(self):
        package=dict(package='cpp', version='4:13.2.0-7ubuntu1', architecture='amd64')
        path=archive_path(package,'pool/main/g/gcc-defaults/cpp_13.2.0-7ubuntu1_amd64.deb')
        self.assertEqual(str(path),'pool/main/g/gcc-defaults/cpp_13.2.0-7ubuntu1_amd64.deb')

    def test_rejects_archive_escape(self):
        package=dict(package='cpp',version='1',architecture='amd64')
        for path in ('/etc/passwd','pool/../../bad/file.deb'):
            with self.assertRaises(ValueError):
                archive_path(package,path)


if __name__=='__main__':
    unittest.main()
