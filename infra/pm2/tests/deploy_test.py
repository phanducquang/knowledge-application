"""Offline deployment lifecycle checks: real Bash/Git/Node, fake PM2/build/HTTP.

Run: python3 infra/pm2/tests/deploy_test.py
No VPS, database, RustFS, OAuth or Gemini connection is made.
"""

import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import unittest


SCRIPTS = Path(__file__).resolve().parents[1]

FAKE_COMMAND = r'''#!/usr/bin/env python3
import json, os, pathlib, subprocess, sys
name=pathlib.Path(sys.argv[0]).name
args=sys.argv[1:]
root=pathlib.Path(os.environ['KNOWLEDGE_DEPLOY_ROOT'])
state_path=pathlib.Path(os.environ['PROBE_PM2_STATE'])
events_path=pathlib.Path(os.environ['PROBE_EVENTS'])
def event(action):
    with events_path.open('a') as f: f.write(action+'\n')
def state():
    return json.loads(state_path.read_text()) if state_path.exists() else []
def store(apps): state_path.write_text(json.dumps(apps))
if name=='id':
    print(os.environ.get('PROBE_UID','1000')); sys.exit(0)
if name=='java':
    print('openjdk version "17.0.0"',file=sys.stderr); sys.exit(0)
if name=='npm':
    assert not os.environ.get('DB_PASSWORD'), 'App secret leaked into build'
    assert (root/'shared/.deploy-lock').is_dir(), 'Deployment lock lost during build'
    event('npm '+' '.join(args))
    if args==['run','build']:
        if os.environ.get('PROBE_FAIL_BUILD')=='1': sys.exit(23)
        pathlib.Path('.next').mkdir(exist_ok=True)
        pathlib.Path('.next/BUILD_ID').write_text('test-build')
    sys.exit(0)
if name=='pm2':
    action=args[0]
    if action=='ping': sys.exit(0)
    if action=='jlist': print(json.dumps(state())); sys.exit(0)
    if action=='save': event('save'); sys.exit(0)
    apps=state()
    app_name=args[args.index('--only')+1] if '--only' in args else args[1]
    found=next((a for a in apps if a['name']==app_name),None)
    if action=='start':
        assert found is None, 'Duplicate process'
        found={'name':app_name,'pm2_env':{'pm_cwd':str(root),'pm_exec_path':str(root/'current/infra/pm2'/('start-api.sh' if app_name.endswith('api') else 'start-web.sh'))}}
        apps.append(found)
    elif found is None:
        sys.exit(1)
    found['pm2_env']['status']='stopped' if action=='stop' else 'online'
    store(apps); event(action+' '+app_name); sys.exit(0)
if name=='curl':
    url=args[-1]
    apps=state()
    if url.endswith('/actuator/health'):
        assert url==os.environ.get('PROBE_API_URL','http://127.0.0.1:8080/actuator/health')
        event('health api')
        if os.environ.get('PROBE_API_DOWN')=='1': sys.exit(22)
        assert any(a['name']=='knowledge-api' and a['pm2_env']['status']=='online' for a in apps)
        print('{"status":"UP"}'); sys.exit(0)
    event('health web')
    assert url==os.environ.get('PROBE_WEB_URL','http://127.0.0.1:3000/login')
    assert any(a['name']=='knowledge-web' and a['pm2_env']['status']=='online' for a in apps)
    print('503' if os.environ.get('PROBE_WEB_DOWN')=='1' else '200'); sys.exit(0)
sys.exit(99)
'''


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="knowledge deploy ")
        self.work = Path(self.temp.name)
        self.root = self.work / "deployment root"
        self.origin = self.work / "origin"
        self.fake_bin = self.work / "bin"
        self.fake_bin.mkdir()
        self.events = self.work / "events"
        self.state = self.work / "processes.json"
        self.env = {
            "PATH": str(self.fake_bin) + os.pathsep + os.environ["PATH"],
            "KNOWLEDGE_DEPLOY_ROOT": str(self.root),
            "KNOWLEDGE_REPOSITORY_URL": str(self.origin),
            "KNOWLEDGE_HEALTH_ATTEMPTS": "2",
            "KNOWLEDGE_HEALTH_INTERVAL": "0",
            "PROBE_PM2_STATE": str(self.state),
            "PROBE_EVENTS": str(self.events),
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
        }
        for command in ("id", "java", "npm", "pm2", "curl"):
            file = self.fake_bin / command
            file.write_text(FAKE_COMMAND)
            file.chmod(0o700)
        self.origin.mkdir()
        self.git("init", "-b", "main")
        (self.origin / "infra").mkdir()
        shutil.copytree(SCRIPTS, self.origin / "infra/pm2", ignore=shutil.ignore_patterns("tests", "__pycache__"))
        api = self.origin / "apps/api"
        api.mkdir(parents=True)
        gradle = api / "gradlew"
        gradle.write_text("""#!/usr/bin/env bash
set -eu
test -z "${DB_PASSWORD:-}"
test -d "$KNOWLEDGE_DEPLOY_ROOT/shared/.deploy-lock"
mkdir -p build/libs
printf fixture > build/libs/knowledge-api-version-independent.jar
printf plain > build/libs/knowledge-api-plain.jar
""")
        gradle.chmod(0o700)
        (self.origin / "apps/web").mkdir()
        (self.origin / "apps/web/package.json").write_text("{}")
        migration = api / "src/main/resources/db/migration"
        migration.mkdir(parents=True)
        (migration / "V1__fixture.sql").write_text("-- fixture\n")
        self.commit("Initial fixture")
        self.run_script("setup.sh")
        api_env = {
            "KNOWLEDGE_JAVA_BIN": str(self.fake_bin / "java"),
            "DB_HOST": "postgres.external.test", "DB_PORT": "5432",
            "DB_NAME": "knowledge_app", "DB_USERNAME": "app", "DB_PASSWORD": "runtime-test-secret",
            "GOOGLE_CLIENT_ID": "client", "GOOGLE_CLIENT_SECRET": "oauth-test-secret",
            "AUTH_ALLOWED_EMAIL": "owner@private.test", "APP_OWNER_ID": "owner-id",
            "WEB_BASE_URL": "https://knowledge.private.test", "SESSION_COOKIE_SECURE": "true",
            "OBJECT_STORAGE_ENDPOINT": "https://rustfs.external.test", "OBJECT_STORAGE_REGION": "us-east-1",
            "OBJECT_STORAGE_BUCKET": "notes", "OBJECT_STORAGE_ACCESS_KEY": "test-access", "OBJECT_STORAGE_SECRET_KEY": "storage-test-secret",
        }
        web_env = {"KNOWLEDGE_API_BASE_URL": "http://127.0.0.1:8080", "KNOWLEDGE_API_BROWSER_BASE_URL": "https://knowledge.private.test"}
        for name, values in (("api", api_env), ("web", web_env)):
            file = self.root / f"shared/{name}.env"
            file.write_text("".join(f"{key}={shlex.quote(value)}\n" for key, value in values.items()))
            file.chmod(0o600)

    def tearDown(self):
        self.temp.cleanup()

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.origin), *args], env=self.env, text=True, stderr=subprocess.DEVNULL).strip()

    def commit(self, message):
        self.git("add", ".")
        self.git("-c", "user.name=Deployment QA", "-c", "user.email=qa@example.test", "commit", "-m", message)

    def run_script(self, name, *args, success=True, extra=None):
        result = subprocess.run(["bash", str(SCRIPTS / name), *args], env={**self.env, **(extra or {})}, capture_output=True, text=True)
        for secret in ("runtime-test-secret", "oauth-test-secret", "storage-test-secret"):
            self.assertNotIn(secret, result.stdout + result.stderr)
        if success:
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse((self.root / "shared/.deploy-lock").exists())
        return result

    def current(self):
        return (self.root / "current").resolve()

    def apps(self):
        return json.loads(self.state.read_text()) if self.state.exists() else []

    def test_first_then_update_builds_both_and_preserves_env(self):
        self.run_script("deploy-first.sh")
        before = self.current()
        env_before = (self.root / "shared/api.env").read_bytes()
        (self.origin / "apps/api/src/main/resources/db/migration/V2__new.sql").write_text("-- new fixture\n")
        self.commit("New main")
        result = self.run_script("deploy-update.sh")
        self.assertNotEqual(before, self.current())
        self.assertEqual((self.root / "shared/previous-release").read_text().strip(), str(before))
        self.assertEqual((self.root / "shared/api.env").read_bytes(), env_before)
        self.assertIn("V2__new.sql", result.stdout)
        self.assertEqual(len(self.apps()), 2)
        self.assertTrue(all(a["pm2_env"]["status"] == "online" for a in self.apps()))
        events = self.events.read_text().splitlines()
        self.assertLess(events.index("health api"), events.index("start knowledge-web"))
        self.assertIn("npm ci --include=dev", events)
        self.assertIn("npm test", events)
        self.assertIn("npm run lint", events)
        self.assertIn("npm run build", events)

    def test_build_failure_does_not_stop_or_switch_old_apps(self):
        self.run_script("deploy-first.sh")
        before, events_before = self.current(), self.events.read_text()
        self.run_script("deploy-update.sh", success=False, extra={"PROBE_FAIL_BUILD": "1"})
        self.assertEqual(before, self.current())
        self.assertNotIn("stop knowledge-", self.events.read_text()[len(events_before):])
        self.assertTrue(all(a["pm2_env"]["status"] == "online" for a in self.apps()))

    def test_failed_api_health_stops_apps_without_automatic_rollback(self):
        self.run_script("deploy-first.sh")
        before = self.current()
        events_before = self.events.read_text()
        self.run_script("deploy-update.sh", success=False, extra={"PROBE_API_DOWN": "1"})
        self.assertNotEqual(before, self.current())
        self.assertTrue(all(a["pm2_env"]["status"] == "stopped" for a in self.apps()))
        self.assertNotIn("restart knowledge-web", self.events.read_text()[len(events_before):])
        self.run_script("restart-current.sh")
        self.assertTrue(all(a["pm2_env"]["status"] == "online" for a in self.apps()))

    def test_partial_first_failure_can_resume_without_cloning(self):
        self.run_script("deploy-first.sh", success=False, extra={"PROBE_API_DOWN": "1"})
        before = self.current()
        self.assertEqual(len(self.apps()), 1)
        self.run_script("restart-current.sh")
        self.assertEqual(before, self.current())
        self.assertEqual(len(self.apps()), 2)

    def test_rollback_requires_explicit_schema_acknowledgement(self):
        self.run_script("deploy-first.sh")
        original = self.current()
        self.run_script("deploy-update.sh")
        new = self.current()
        self.run_script("rollback.sh", success=False)
        self.assertEqual(new, self.current())
        self.run_script("rollback.sh", "--schema-compatible")
        self.assertEqual(original, self.current())
        self.assertEqual((self.root / "shared/previous-release").read_text().strip(), str(new))

    def test_setup_preserves_config_and_help_has_no_deploy_side_effect(self):
        before = (self.root / "shared/api.env").read_bytes()
        self.run_script("setup.sh")
        self.assertEqual((self.root / "shared/api.env").read_bytes(), before)
        self.run_script("deploy-first.sh", "--help")
        self.assertFalse((self.root / "current").exists())
        self.assertEqual(self.apps(), [])

    def test_invalid_env_fails_before_clone_or_app_stop(self):
        env_file = self.root / "shared/api.env"
        env_file.write_text(env_file.read_text().replace("runtime-test-secret", "CHANGE_ME_PASSWORD"))
        self.run_script("deploy-first.sh", success=False)
        self.assertEqual(list((self.root / "releases").iterdir()), [])
        self.assertEqual(self.apps(), [])

    def test_unrelated_named_pm2_process_is_not_stopped(self):
        self.run_script("deploy-first.sh")
        apps = self.apps()
        apps[0]["pm2_env"]["pm_cwd"] = "/some/other/application"
        self.state.write_text(json.dumps(apps))
        before = self.events.read_text()
        self.run_script("deploy-update.sh", success=False)
        self.assertNotIn("stop knowledge-", self.events.read_text()[len(before):])

    def test_failed_web_health_stops_both_apps(self):
        self.run_script("deploy-first.sh")
        old = self.current()
        self.run_script("deploy-update.sh", success=False, extra={"PROBE_WEB_DOWN": "1"})
        self.assertNotEqual(old, self.current())
        self.assertTrue(all(a["pm2_env"]["status"] == "stopped" for a in self.apps()))

    def test_an_existing_lock_is_not_removed(self):
        lock = self.root / "shared/.deploy-lock"
        lock.mkdir()
        (lock / "pid").write_text("another-process")
        result = subprocess.run(["bash", str(SCRIPTS / "deploy-first.sh")], env=self.env, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual((lock / "pid").read_text(), "another-process")
        self.assertEqual(self.apps(), [])

    def test_configured_ports_are_used_for_health_checks(self):
        api = self.root / "shared/api.env"
        api.write_text(api.read_text()+"SERVER_PORT=18080\n")
        web = self.root / "shared/web.env"
        web.write_text(web.read_text().replace("http://127.0.0.1:8080", "http://127.0.0.1:18080")+"KNOWLEDGE_WEB_PORT=13000\n")
        self.run_script("deploy-first.sh", extra={"PROBE_API_URL":"http://127.0.0.1:18080/actuator/health", "PROBE_WEB_URL":"http://127.0.0.1:13000/login"})

    def test_mismatched_public_origins_fail_preflight(self):
        web = self.root / "shared/web.env"
        web.write_text(web.read_text().replace("https://knowledge.private.test", "https://another.private.test"))
        self.run_script("deploy-first.sh", success=False)
        self.assertEqual(list((self.root / "releases").iterdir()), [])
        self.assertEqual(self.apps(), [])

    def test_missing_deployment_scripts_on_main_fail_before_build(self):
        (self.origin / "infra/pm2/deploy-update.sh").unlink()
        self.commit("Incomplete deployment tools")
        self.run_script("deploy-first.sh", success=False)
        self.assertFalse((self.root / "current").exists())
        self.assertEqual(self.apps(), [])
        self.assertFalse(self.events.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
