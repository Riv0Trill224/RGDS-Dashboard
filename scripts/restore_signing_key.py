"""Restore the CI keystore without logging secret values."""
import base64
import binascii
import os
from pathlib import Path


def main():
    required = (
        'RGDS_KEYSTORE_BASE64', 'RGDS_KEYSTORE_PASSWORD',
        'RGDS_KEY_ALIAS', 'RGDS_KEY_PASSWORD',
    )
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise SystemExit('Missing GitHub Actions secrets: ' + ', '.join(missing))
    # certutil produces line-wrapped Base64, potentially with Windows CRLF.
    encoded = ''.join(os.environ['RGDS_KEYSTORE_BASE64'].split())
    try:
        data = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error):
        raise SystemExit('Invalid keystore Base64. Copy only the body, without BEGIN/END lines.')
    if not data:
        raise SystemExit('The decoded keystore is empty.')
    destination = Path(os.environ['RUNNER_TEMP']) / 'rgds-release.jks'
    with open(destination, 'xb', opener=lambda path, flags: os.open(path, flags, 0o600)) as output:
        output.write(data)


if __name__ == '__main__':
    main()
