#!/usr/bin/env python3
"""Download locked Ubuntu packages; indexes locate files, the reviewed SHA256 authorizes bytes."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import lzma
from pathlib import Path, PurePosixPath
import urllib.parse
import urllib.request

BASE = 'https://archive.ubuntu.com/ubuntu/'


def locations(open_url=urllib.request.urlopen):
    result = {}
    for suite in ('noble', 'noble-updates'):
        url = BASE + 'dists/' + suite + '/main/binary-amd64/Packages.xz'
        with open_url(url, timeout=30) as response:
            # Compressed indexes have a fixed size ceiling; never trust Content-Length.
            body = response.read((32 << 20) + 1)
        if len(body) > 32 << 20:
            raise ValueError('package index too large')
        import io
        with lzma.open(io.BytesIO(body), 'rt') as stream:
            fields = {}
            for line in stream:
                if line == '\n':
                    if 'Package' in fields and 'Filename' in fields:
                        result[fields['Package']] = fields['Filename']
                    fields = {}
                elif not line[0].isspace():
                    key, _, value = line.partition(': ')
                    fields[key] = value.strip()
    return result


def archive_path(package, location):
    # apt's local filename retains an escaped epoch; pool filenames omit it.
    version = package['version'].split(':', 1)[-1]
    name = package['package'] + '_' + version + '_' + package['architecture'] + '.deb'
    path = PurePosixPath(location).parent / name
    if path.parts[0] != 'pool' or '..' in path.parts or PurePosixPath(name).name != name:
        raise ValueError('unsafe archive location')
    return path


def download(lock, output, resume=False, address_failover=False):
    open_url = urllib.request.urlopen
    if address_failover:
        from transport import source_urlopen
        open_url = source_urlopen(BASE)
    packages = json.loads(lock.read_text())['packages']
    paths = locations(open_url)
    output.mkdir(mode=0o755, exist_ok=resume)
    if output.is_symlink():
        raise ValueError("output must not be a symlink")

    def fetch(package):
        filename = package['file']
        if PurePosixPath(filename).name != filename:
            raise ValueError('unsafe package name')
        path = archive_path(package, paths[package['package']])
        if path.parts[0] != 'pool' or '..' in path.parts:
            raise ValueError('unsafe archive location')
        url = BASE + urllib.parse.quote(str(path))
        target = output / filename
        if target.exists() or target.is_symlink():
            if not resume or target.is_symlink() or not target.is_file():
                raise ValueError('refusing existing output: '+filename)
            with target.open('rb') as existing:
                if hashlib.file_digest(existing,'sha256').hexdigest() != package['sha256']:
                    raise ValueError('existing package differs: '+filename)
            return filename
        h = hashlib.sha256()
        total = 0
        with open_url(url, timeout=30) as response, target.open('xb') as file:
            while chunk := response.read(64 << 10):
                total += len(chunk)
                if total > 100 << 20:
                    raise ValueError('package exceeds download limit')
                h.update(chunk)
                file.write(chunk)
        if h.hexdigest() != package['sha256']:
            raise ValueError('locked package SHA256 mismatch: ' + filename)
        return filename

    with ThreadPoolExecutor(max_workers=4) as workers:
        for filename in workers.map(fetch, packages):
            print('verified ' + filename, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--lock', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--resume', action='store_true', help='reuse only already hash-verified files')
    parser.add_argument('--address-failover', action='store_true', help='try same-origin DNS peers within the connection deadline (direct HTTPS only)')
    args = parser.parse_args()
    download(args.lock, args.output, args.resume, args.address_failover)
