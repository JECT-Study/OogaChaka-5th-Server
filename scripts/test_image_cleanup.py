#!/usr/bin/env python3
"""가짜 Docker 명령으로 삭제 범위와 보존 조건을 검증한다. 실제 이미지는 삭제하지 않는다."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / 'cleanup-app-images.sh'


class ImageCleanupTest(unittest.TestCase):
    """현재·직전·최근 이미지 보호와 삭제 실패 시 계속 동작하는지 검사한다."""

    # 동일 저장소의 오래된 이미지만 삭제하고 현재·직전·최근 이미지는 보존한다.
    def test_preserves_current_previous_and_recent_images(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            docker = root / 'docker'
            docker.write_text('''#!/usr/bin/env python3
import os, sys
from pathlib import Path
args = sys.argv[1:]
if args[:2] == ['image', 'inspect']:
    if args[3] == '{{.Id}}': print('sha256:current')
    else: print('recent' if args[4] == 'sha256:recent' else 'old')
elif args[:2] == ['image', 'ls']:
    assert args[-1] == 'reference=123.dkr.ecr.ap-northeast-2.amazonaws.com/oogachaka_app'
    print('sha256:current\\nsha256:previous\\nsha256:recent\\nsha256:old\\nsha256:busy')
elif args[:2] == ['image', 'rm']:
    assert len(args) == 3
    with open(os.environ['REMOVED'], 'a') as out: out.write(args[2] + '\\n')
    if args[2] == 'sha256:busy': sys.exit(1)
else: sys.exit(2)
''')
            date = root / 'date'
            date.write_text('''#!/usr/bin/env python3
import sys
print(1000000 if sys.argv[1] == '+%s' else 999999 if sys.argv[2] == 'recent' else 1)
''')
            for executable in (docker, date): executable.chmod(0o700)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ['PATH'], REMOVED=str(root / 'removed'))
            image = '123.dkr.ecr.ap-northeast-2.amazonaws.com/oogachaka_app@sha256:current'
            subprocess.run(['bash', str(SCRIPT), image, 'sha256:previous'], env=env, check=True, capture_output=True)
            self.assertEqual(set((root / 'removed').read_text().splitlines()), {'sha256:old', 'sha256:busy'})
            (root / 'removed').unlink()
            subprocess.run(['bash', str(SCRIPT), image, ''], env=env, check=True, capture_output=True)
            self.assertFalse((root / 'removed').exists())




if __name__ == '__main__': unittest.main()
