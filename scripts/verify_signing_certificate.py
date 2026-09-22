"""Reject APKs signed with any certificate other than the project's stable key."""
from pathlib import Path
import re
import sys

EXPECTED_SHA256 = '1c09515ee923d5610dc41e85d75a49900c10158b18c4703463896e4fd1b10264'


def verify(report):
    digests = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)\s*$', report, re.MULTILINE)
    if len(digests) != 1 or digests[0].lower() != EXPECTED_SHA256:
        raise ValueError('APK signing certificate does not match the stable RGDS certificate.')


if __name__ == '__main__':
    try:
        verify(Path(sys.argv[1]).read_text(encoding='utf-8'))
    except ValueError as error:
        raise SystemExit(str(error))
    print('APK verified: stable RGDS signing certificate matches.')
