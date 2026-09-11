"""Missing, truncated or interleaved observations must not imply completion."""
from concurrent.futures import ThreadPoolExecutor
from contextlib import redirect_stdout
import io
import json
import ssl
import threading
import unittest
import urllib.error
from unittest.mock import Mock, patch

import diagnostics
import transport


class DiagnosticTests(unittest.TestCase):
    def test_progress_is_rate_and_count_bounded_but_final_bytes_are_preserved(self):
        now = [0]
        with patch.object(diagnostics.time, 'monotonic_ns', side_effect=lambda: now[0]), redirect_stdout(io.StringIO()) as log:
            with diagnostics.Diagnostics(True).request(3, 'cpp.deb') as request:
                request.phase('body')
                for index in range(1000):
                    now[0] += 1_000_000_000
                    request.progress(index + 1)
                request.phase('verified')
        events = [json.loads(line) for line in log.getvalue().splitlines()]
        progress = [event for event in events if event['stage'] == 'body' and event['readBytes']]
        self.assertEqual(len(progress), 16)
        self.assertTrue(all(b['elapsedNs'] - a['elapsedNs'] >= 5_000_000_000 for a, b in zip(progress, progress[1:])))
        self.assertEqual(events[-1]['readBytes'], 1000)
        self.assertEqual(events[-1]['stage'], 'verified')

    def test_shared_byte_budget_is_bounded_and_explicitly_truncated_under_concurrency(self):
        sink = diagnostics.Diagnostics(True)
        def produce(number):
            with sink.request(number, 'x' * 192) as request:
                for _ in range(1000): request.phase('body')
        with redirect_stdout(io.StringIO()) as log, ThreadPoolExecutor(max_workers=4) as workers:
            list(workers.map(produce, range(3, 7)))
        encoded = log.getvalue()
        self.assertLessEqual(len(encoded.encode()), diagnostics.MAX_BYTES)
        events = [json.loads(line) for line in encoded.splitlines()]
        self.assertEqual(events[-1], {'event': 'download-diagnostics-truncated'})
        self.assertEqual(sum(event['event'] == 'download-diagnostics-truncated' for event in events), 1)

    def test_error_classification_does_not_render_exception_headers_or_invalid_names(self):
        errors = ((urllib.error.HTTPError('private-url', 503, 'secret-message', {'Secret': 'value'}, None), 'HTTP_ERROR'),
                  (urllib.error.URLError(TimeoutError('secret-message')), 'TIMEOUT'),
                  (ssl.SSLCertVerificationError('secret-message'), 'CERTIFICATE_REJECTED'),
                  (ValueError('secret-message'), 'REJECTED'))
        for error, outcome in errors:
            with self.subTest(outcome=outcome), redirect_stdout(io.StringIO()) as log:
                with self.assertRaises(type(error)), diagnostics.Diagnostics(True).request(3, 'private-name\n' * 100) as request:
                    request.phase('open')
                    raise error
                text = log.getvalue()
                self.assertNotIn('private', text)
                self.assertNotIn('secret', text)
                event = json.loads(text.splitlines()[-1])
                self.assertEqual(event['outcome'], outcome)
                self.assertEqual(event['stage'], 'open')
                if outcome == 'HTTP_ERROR': self.assertEqual(event['httpStatus'], 503)

    def test_parallel_opener_context_is_correlated_and_reset_even_on_error(self):
        barrier = threading.Barrier(2, timeout=5)
        sink = diagnostics.Diagnostics(True)
        def opening(_url, **_options):
            observer = transport.OBSERVER.get()
            barrier.wait()
            self.assertIs(transport.OBSERVER.get(), observer)
            observer.connection(host='archive.ubuntu.com', peer='192.0.2.1', attempt=1,
                                stage='tls', outcome='CONNECTED', elapsedNs=1)
            if observer.number == 4: raise TimeoutError('secret')
            return io.BytesIO(b'')
        with patch.object(transport.urllib.request, 'build_opener', return_value=Mock(open=opening)):
            open_url = transport.source_urlopen('https://archive.ubuntu.com/')
        def execute(number):
            try:
                with sink.request(number, 'pkg-' + str(number)) as request:
                    with request.open(open_url, 'https://archive.ubuntu.com/'): pass
            except TimeoutError:
                self.assertEqual(number, 4)
            self.assertIsNone(transport.OBSERVER.get())
        with redirect_stdout(io.StringIO()) as log, ThreadPoolExecutor(max_workers=2) as workers:
            list(workers.map(execute, (3, 4)))
        events = [json.loads(line) for line in log.getvalue().splitlines()]
        for number in (3, 4):
            group = [event for event in events if event['requestId'] == number]
            self.assertEqual({event['name'] for event in group}, {'pkg-' + str(number)})
            self.assertEqual(sum(event['event'] == 'https-connect' for event in group), 1)
        failed = [event for event in events if event.get('outcome') == 'TIMEOUT']
        self.assertEqual([(event['requestId'], event['stage']) for event in failed], [(4, 'response_headers')])


if __name__ == '__main__':
    unittest.main()
