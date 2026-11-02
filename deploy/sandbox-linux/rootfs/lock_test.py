import unittest

import lock


def stanza(name, version, depends='', pre='', provides='', arch='amd64'):
    fields = {'Package': name, 'Version': version, 'Architecture': arch,
              'Filename': f'pool/main/{name[0]}/{name}/{name}_{version}_{arch}.deb', 'SHA256': '0' * 64}
    if depends:
        fields['Depends'] = depends
    if pre:
        fields['Pre-Depends'] = pre
    if provides:
        fields['Provides'] = provides
    return fields


class VersionTest(unittest.TestCase):
    def test_dpkg_ordering(self):
        # 来自 dpkg 的规则：epoch 优先；~ 排在一切之前；数字段按数值比较；字母在符号之前。
        cases = [('1.0', '1.0', 0), ('1:1.0', '2.0', 1), ('1.0~rc1', '1.0', -1), ('1.10', '1.9', 1),
                 ('13.3.0-6ubuntu2~24.04.1', '13.3.0-6ubuntu2', -1), ('2.42-4ubuntu2.10', '2.42-4ubuntu2.9', 1),
                 ('1.0a', '1.0+', -1), ('4:13.2.0-7ubuntu1', '13.3.0-6', 1)]
        for a, b, want in cases:
            self.assertEqual(lock.compare_versions(a, b), want, (a, b))
            self.assertEqual(lock.compare_versions(b, a), -want, (b, a))

    def test_constraints(self):
        self.assertTrue(lock.satisfies('2.39-0ubuntu8.8', '>=', '2.38'))
        self.assertFalse(lock.satisfies('2.39', '<<', '2.39'))
        self.assertTrue(lock.satisfies('anything', None, None))


class RelationTest(unittest.TestCase):
    def test_parses_alternatives_versions_and_qualifiers(self):
        groups = lock.parse_relations('libc6 (>= 2.34), gcc:any | clang [amd64], python3:any <!nocheck>')
        self.assertEqual(groups, [[('libc6', '>=', '2.34')], [('gcc', None, None), ('clang', None, None)],
                                  [('python3', None, None)]])

    def test_rejects_garbage(self):
        with self.assertRaises(ValueError):
            lock.parse_relations('Bad Name')


class ResolveTest(unittest.TestCase):
    def index(self, *stanzas):
        return lock.Index(list(stanzas))

    def test_closure_follows_depends_and_pre_depends(self):
        index = self.index(stanza('g++', '1', depends='libstdc++-dev'), stanza('libstdc++-dev', '1', pre='libc6'),
                           stanza('libc6', '2'), stanza('unrelated', '1'))
        chosen, notes = lock.resolve(index, ['g++'])
        self.assertEqual(set(chosen), {'g++', 'libstdc++-dev', 'libc6'})
        self.assertEqual(notes, [])

    def test_highest_version_wins_and_arch_is_filtered(self):
        index = self.index(stanza('libc6', '2.39-0ubuntu8.4'), stanza('libc6', '2.39-0ubuntu8.8'),
                           stanza('libc6', '9', arch='i386'))
        chosen, _ = lock.resolve(index, ['libc6'])
        self.assertEqual(chosen['libc6']['Version'], '2.39-0ubuntu8.8')

    def test_alternatives_take_the_first_and_are_reported(self):
        index = self.index(stanza('tool', '1', depends='awk-a | awk-b'), stanza('awk-a', '1'), stanza('awk-b', '1'))
        chosen, notes = lock.resolve(index, ['tool'])
        self.assertIn('awk-a', chosen)
        self.assertNotIn('awk-b', chosen)
        self.assertEqual(len(notes), 1)

    def test_an_already_chosen_alternative_is_reused(self):
        index = self.index(stanza('tool', '1', depends='awk-a | awk-b'), stanza('awk-a', '1'), stanza('awk-b', '1'))
        chosen, _ = lock.resolve(index, ['awk-b', 'tool'])
        self.assertNotIn('awk-a', chosen)

    def test_virtual_packages_resolve_to_a_sorted_provider(self):
        index = self.index(stanza('tool', '1', depends='c-compiler'), stanza('zcc', '1', provides='c-compiler'),
                           stanza('acc', '1', provides='c-compiler'))
        chosen, notes = lock.resolve(index, ['tool'])
        self.assertIn('acc', chosen)
        self.assertEqual(len(notes), 1)

    def test_unsatisfiable_versions_fail_loudly(self):
        index = self.index(stanza('tool', '1', depends='libc6 (>= 3)'), stanza('libc6', '2'))
        with self.assertRaises(ValueError):
            lock.resolve(index, ['tool'])
        with self.assertRaises(ValueError):
            lock.resolve(index, ['missing'])

    def test_lock_document_matches_the_existing_lock_conventions(self):
        index = self.index(stanza('cpp', '4:13.2.0-7ubuntu1'))
        chosen, _ = lock.resolve(index, ['cpp'])
        document = lock.lock_document('cpp', ['cpp'], chosen, 'https://snapshot.example/', ('main',))
        self.assertEqual(document['packages'][0]['file'], 'cpp_4%3a13.2.0-7ubuntu1_amd64.deb')
        self.assertEqual((document['version'], document['architecture'], document['layout']), (1, 'amd64', 'usr-merged'))
        self.assertEqual(document['language'], 'cpp')


if __name__ == '__main__':
    unittest.main()
