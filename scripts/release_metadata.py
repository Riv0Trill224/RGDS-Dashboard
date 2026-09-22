"""Describe the exact signed artifact; no keys or credentials in metadata."""
import hashlib
import json
import os
from pathlib import Path
import re

gradle = Path('app/build.gradle').read_text()
version = re.search(r"versionName '([^']+)'", gradle).group(1)
code = int(re.search(r'versionCode (\d+)', gradle).group(1))
package = re.search(r"applicationId '([^']+)'", gradle).group(1)
repo = os.environ['GITHUB_REPOSITORY']
if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repo):
    raise SystemExit('Invalid repository')
apk = Path('dist/RGDS-Dashboard.apk')
metadata = dict(schema=1, versionName=version, versionCode=code, packageName=package,
                size=apk.stat().st_size, sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                url=f'https://github.com/{repo}/releases/download/v{version}/{apk.name}',
                commit=os.environ['GITHUB_SHA'], channel='test')
Path('dist/update.json').write_text(json.dumps(metadata, indent=2) + '\n')
