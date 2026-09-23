"""Development report relay. Run behind HTTPS; SMTP credentials stay on this host."""
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, HTTPServer
from email.message import EmailMessage
import json
import os
import re
import smtplib
import sqlite3
import ssl
import time

RECIPIENT = 'riv0trill224@icloud.com'
MAX_BODY = 6 * 1024 * 1024


def validate(body):
    if body.get('schema') != 1 or not re.fullmatch(r'[a-f0-9-]{36}', body.get('reportId', '')):
        raise ValueError('Invalid schema/reportId')
    log = body.get('log')
    if not isinstance(log, str) or len(log.encode('utf-8')) > 2 * 1024 * 1024:
        raise ValueError('Invalid log size')
    for key in ('version', 'session'):
        if not isinstance(body.get(key), str) or len(body[key]) > 180 or '\r' in body[key] or '\n' in body[key]:
            raise ValueError('Invalid metadata')
    return body


def deliver(body):
    message = EmailMessage()
    message['From'] = os.environ['SMTP_FROM']
    message['To'] = RECIPIENT
    message['Subject'] = f"[RGDS][{body['version']}][{body['reportId']}] Development log"
    message.set_content('Reporte automático de desarrollo. Sesión: ' + body['session'])
    message.add_attachment(body['log'].encode('utf-8'), maintype='text', subtype='plain',
                           filename='rgds-' + body['reportId'] + '.log')
    with smtplib.SMTP_SSL(os.environ['SMTP_HOST'], int(os.environ.get('SMTP_PORT', '465')),
                          timeout=15, context=ssl.create_default_context()) as smtp:
        smtp.login(os.environ['SMTP_USER'], os.environ['SMTP_PASSWORD'])
        refused = smtp.send_message(message)
        if refused:
            raise RuntimeError('SMTP refused recipient')


def make_handler(token, database, sender=deliver):
    class Handler(BaseHTTPRequestHandler):
        def setup(self):
            super().setup()
            self.connection.settimeout(20)

        def reply(self, status, payload):
            encoded = json.dumps(payload).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(encoded)))
            self.end_headers()
            self.wfile.write(encoded)

        def do_POST(self):
            if self.path != '/reports':
                self.reply(404, {'error': 'not_found'}); return
            if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + token):
                self.reply(401, {'error': 'unauthorized'}); return
            try:
                size = int(self.headers.get('Content-Length', '0'))
                if size <= 0 or size > MAX_BODY:
                    self.reply(413, {'error': 'size_limit'}); return
                body = validate(json.loads(self.rfile.read(size)))
            except (ValueError, TypeError, AttributeError, TimeoutError):
                self.reply(400, {'error': 'invalid_report'}); return
            report_id = body['reportId']
            digest = hashlib.sha256(json.dumps(body, sort_keys=True).encode()).hexdigest()
            with sqlite3.connect(database) as db:
                db.execute('CREATE TABLE IF NOT EXISTS receipts (id TEXT PRIMARY KEY, digest TEXT, sent INTEGER, updated REAL)')
                previous = db.execute('SELECT digest, sent FROM receipts WHERE id=?', (report_id,)).fetchone()
                if previous and previous[0] != digest:
                    self.reply(409, {'error': 'id_conflict'}); return
                if previous and previous[1]:
                    self.reply(200, {'reportId': report_id, 'status': 'smtp_accepted'}); return
                now = time.time()
                recent = db.execute('SELECT COUNT(*) FROM receipts WHERE updated>?', (now - 60,)).fetchone()[0]
                if recent >= 12:
                    self.reply(429, {'error': 'rate_limit'}); return
                db.execute('INSERT OR REPLACE INTO receipts VALUES (?, ?, 0, ?)', (report_id, digest, now))
                db.commit()
                try:
                    sender(body)
                except Exception:
                    # Never log credentials or report contents.
                    self.reply(502, {'error': 'smtp_failed'}); return
                db.execute('UPDATE receipts SET sent=1 WHERE id=?', (report_id,))
                db.commit()
            self.reply(200, {'reportId': report_id, 'status': 'smtp_accepted'})

        def log_message(self, *_):
            pass
    return Handler


if __name__ == '__main__':
    required = ('RELAY_TOKEN', 'SMTP_HOST', 'SMTP_USER', 'SMTP_PASSWORD', 'SMTP_FROM')
    missing = [name for name in required if not os.environ.get(name)]
    if missing or len(os.environ.get('RELAY_TOKEN', '')) < 32:
        raise SystemExit('Configure required environment variables and a RELAY_TOKEN of at least 32 characters.')
    server = HTTPServer(('127.0.0.1', int(os.environ.get('PORT', '8080'))),
                        make_handler(os.environ['RELAY_TOKEN'], os.environ.get('RECEIPTS_DB', 'receipts.sqlite3')))
    server.serve_forever()
