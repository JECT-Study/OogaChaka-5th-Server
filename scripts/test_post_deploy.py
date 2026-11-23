#!/usr/bin/env python3
"""배포 후 정리를 가짜 Docker·flock으로 검사한다. 실제 서버·이미지에는 접근하지 않는다."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
A, B, C, P = ['sha256:' + digit * 64 for digit in 'abcf']
IMAGE = '123.dkr.ecr.ap-northeast-2.amazonaws.com/oogachaka_app@'


class PostDeployTest(unittest.TestCase):
    """이력 저장 실패·재배포·배포 간 경합에서 정상 이미지가 삭제되지 않는지 검증한다."""

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.dest = self.root / 'app'
        self.dest.mkdir()
        self.context = self.root / 'previous-image-id'
        self.context.write_text(A + '\n')
        self.state = self.dest / '.successful-images.json'
        self.state.write_text(json.dumps({'current': A, 'previous': P}))
        source = (ROOT / 'post-deploy.sh').read_text().replace('DEST=/opt/ooga/app', 'DEST=' + str(self.dest))
        (self.root / 'post-deploy.sh').write_text(source)
        (self.root / 'cleanup-app-images.sh').write_text((ROOT / 'cleanup-app-images.sh').read_text())
        self.env = dict(os.environ, PATH=str(self.root) + os.pathsep + os.environ['PATH'],
                        CURRENT_ID=B, RUNNING_ID=B, REMOVED=str(self.root / 'removed'),
                        IMAGES='\n'.join([A, B, C, P]))
        self.executable('docker', '''#!/usr/bin/env python3
import os,sys
args=sys.argv[1:]
if args[:2]==['image','inspect']:
    print(os.environ['CURRENT_ID'] if args[3]=='{{.Id}}' else 'old')
elif args[:2]==['image','ls']:
    print(os.environ['IMAGES'])
elif args[:2]==['image','rm']:
    assert len(args)==3
    with open(os.environ['REMOVED'],'a') as f: f.write(args[2]+'\\n')
elif args[0]=='inspect':
    print(os.environ['RUNNING_ID'] if args[2]=='{{.Image}}' else 'true')
else: sys.exit(2)
''')
        self.executable('date', '#!/bin/sh\nif [ "$1" = +%s ]; then echo 1000000; else echo 1; fi\n')
        self.executable('flock', '#!/bin/sh\nexit "${BUSY_LOCK:-0}"\n')
        self.executable('mv', '#!/bin/sh\n[ "${RENAME_FAIL:-0}" = 0 ] || exit 1\nexec /bin/mv "$@"\n')

    def executable(self, name, source):
        path = self.root / name
        path.write_text(source)
        path.chmod(0o700)

    def run_post(self):
        return subprocess.run(['bash', str(self.root / 'post-deploy.sh'), IMAGE + self.env['CURRENT_ID'], str(self.context)],
                              env=self.env, capture_output=True, text=True)

    def removed(self):
        path = self.root / 'removed'
        return set(path.read_text().splitlines()) if path.exists() else set()

    # B 적용 후 이력 교체가 실패해도 기존 파일을 보존하고, 다음 C 배포에서는 실제 직전 B를 보호한다.
    def test_failed_state_save_then_next_deployment_preserves_actual_previous_image(self):
        original = self.state.read_bytes()
        self.env['RENAME_FAIL'] = '1'
        self.assertNotEqual(self.run_post().returncode, 0)
        self.assertEqual(self.state.read_bytes(), original)
        self.assertFalse(self.removed())
        self.assertEqual(list(self.dest.glob('.successful-images.*')), [self.state])
        self.env.update(RENAME_FAIL='0', CURRENT_ID=C, RUNNING_ID=C)
        self.context.write_text(B + '\n')
        self.assertEqual(self.run_post().returncode, 0)
        self.assertEqual(json.loads(self.state.read_text()), {'current': C, 'previous': B})
        self.assertNotIn(B, self.removed())
        self.assertNotIn(C, self.removed())
        self.assertIn(A, self.removed())
        self.assertEqual(self.state.stat().st_mode & 0o777, 0o600)

    # 같은 B를 재배포해도 기존 정상 A를 보존한다. 이력이 맞지 않으면 삭제를 건너뛴다.
    def test_same_image_redeployment_retains_previous_distinct_version(self):
        self.context.write_text(B + '\n')
        self.state.write_text(json.dumps({'current': B, 'previous': A}))
        self.assertEqual(self.run_post().returncode, 0)
        self.assertNotIn(A, self.removed())
        (self.root / 'removed').unlink()
        self.state.write_text(json.dumps({'current': A, 'previous': P}))
        self.assertEqual(self.run_post().returncode, 0)
        self.assertFalse(self.removed())

    # 최초 배포는 이력만 기록하고, 앞선 PR의 두 이력 파일을 읽어 삭제 대상을 정하지 않는다.
    def test_first_deployment_records_state_without_removing_images(self):
        self.state.unlink()
        self.context.write_text('\n')
        self.assertEqual(self.run_post().returncode, 0)
        self.assertFalse(self.removed())
        self.assertEqual(json.loads(self.state.read_text()), {'current': B, 'previous': ''})

    # 다른 배포가 잠금을 보유하거나 더 최신 컨테이너가 실행 중이면 이전 배포 기준으로 삭제하지 않는다.
    def test_concurrent_or_newer_deployment_skips_cleanup(self):
        for settings in ({'BUSY_LOCK': '1'}, {'BUSY_LOCK': '0', 'RUNNING_ID': C}):
            self.env.update(settings)
            self.assertEqual(self.run_post().returncode, 0)
            self.assertFalse(self.removed())

    # 손상된 컨텍스트는 이력 저장과 삭제를 중단한다.
    def test_invalid_context_does_not_delete_images(self):
        self.context.write_text('invalid image\n')
        self.assertNotEqual(self.run_post().returncode, 0)
        self.assertFalse(self.removed())

    # 실제 워크플로의 원격 명령을 실행해 정리 timeout(124/137)은 성공, 앱 실패는 실패로 남는지 확인한다.
    def test_workflow_separates_deploy_failure_from_cleanup_timeout(self):
        workflow = (ROOT / '.github/workflows/deploy.yml').read_text()
        remote = textwrap.dedent(workflow.split("cat > remote.sh <<'REMOTE'\n", 1)[1].split('          REMOTE', 1)[0])
        stage = self.root / 'stage'
        stage.mkdir()
        self.executable('mktemp', '#!/bin/sh\nprintf "%s\\n" "$TEST_STAGE"\n')
        self.executable('aws', '#!/bin/sh\nexit 0\n')
        self.executable('tar', '#!/bin/sh\nexit 0\n')
        self.executable('timeout', '''#!/usr/bin/env python3
import os,sys,subprocess
args=sys.argv[1:]
assert args[0].startswith('--kill-after=')
duration=args[1]
if duration=='30s' and args[2]=='bash':
    with open(os.environ['CLEANUP_CALLED'],'w') as f: f.write('called')
    sys.exit(int(os.environ['CLEANUP_STATUS']))
if duration=='480s': sys.exit(subprocess.call(args[2:]))
sys.exit(0)
''')
        for deploy_status, cleanup_status in [(0, 124), (0, 137), (1, 0)]:
            if not stage.exists(): stage.mkdir()
            (stage / 'deploy.sh').write_text(f'#!/bin/sh\nexit {deploy_status}\n')
            called = self.root / 'cleanup-called'
            called.unlink(missing_ok=True)
            env = dict(self.env, TEST_STAGE=str(stage), CLEANUP_CALLED=str(called), CLEANUP_STATUS=str(cleanup_status))
            result = subprocess.run(['bash', '-c', remote], env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, deploy_status)
            self.assertEqual(called.exists(), deploy_status == 0)
            if deploy_status == 0: self.assertIn('배포는 성공했습니다', result.stderr)


if __name__ == '__main__': unittest.main()
