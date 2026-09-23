import http.client
import json
from pathlib import Path
import tempfile
import threading
import unittest
from http.server import HTTPServer
from server import make_handler, validate


class RelayTest(unittest.TestCase):
    def test_authentication_deduplication_and_conflicting_id(self):
        delivered = []
        with tempfile.TemporaryDirectory() as temp:
            server = HTTPServer(('127.0.0.1', 0), make_handler('test-token', str(Path(temp) / 'db'), delivered.append))
            thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
            body = dict(schema=1, reportId='12345678-1234-1234-1234-123456789abc', version='test', session='session.log', log='fixture')
            def post(token, content):
                client = http.client.HTTPConnection('127.0.0.1', server.server_port)
                client.request('POST', '/reports', json.dumps(content), {'Authorization': 'Bearer ' + token})
                response = client.getresponse(); status = response.status; response.read(); client.close()
                return status
            try:
                self.assertEqual(post('wrong', body), 401)
                self.assertEqual(post('test-token', body), 200)
                self.assertEqual(post('test-token', body), 200)
                self.assertEqual(len(delivered), 1)
                body['log'] = 'different'
                self.assertEqual(post('test-token', body), 409)
            finally:
                server.shutdown(); server.server_close(); thread.join()

    def test_rejects_header_injection_and_oversize(self):
        body = dict(schema=1, reportId='12345678-1234-1234-1234-123456789abc', version='x\r\nBcc: other', session='s', log='x')
        with self.assertRaises(ValueError): validate(body)
        body['version'] = 'test'; body['log'] = 'x' * (2 * 1024 * 1024 + 1)
        with self.assertRaises(ValueError): validate(body)
