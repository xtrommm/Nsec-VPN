import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import stat
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'deployment' / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


deploy = module('website_deploy', 'deploy-website.py')
entry = module('website_entry', 'website-deploy-entry.py')
installer = module('website_install', 'install-website-deployer.py')


def model():
    return {'name': 'deployment', 'services': {
        'backend': {'image': 'backend:old', 'environment': {
            'JWT_SECRET': 'test-only-existing-secret',
            'NSEC_AGENT_VIRGINIA_SECRET': 'test-only-agent-secret',
            'DATABASE_URL': 'test-only-database-url',
            'TEST_LITERAL': 'literal$with$characters'},
            'ports': [{'host_ip': '127.0.0.1', 'published': '3001', 'target': 4000}],
            'networks': {'nsec_net': None}},
        'admin-panel': {'image': 'admin:old', 'ports': [
            {'host_ip': '127.0.0.1', 'published': '3002', 'target': 80}]},
        'postgres': {'image': 'postgres:16-alpine', 'volumes': ['existing-volume:/var/lib/postgresql/data']}},
        'networks': {'nsec_net': {'name': 'deployment_nsec_net'}}}


class WebsiteDeploymentTest(unittest.TestCase):
    def test_only_images_change_and_original_is_unchanged(self):
        before = model()
        original = copy.deepcopy(before)
        after = deploy.proposed_config(before, 'backend@sha256:' + 'a' * 64,
                                        'admin@sha256:' + 'b' * 64)
        deploy.assert_preserved(before, after)
        self.assertEqual(before, original)
        self.assertEqual(after['services']['backend']['environment'],
                         before['services']['backend']['environment'])
        self.assertEqual(after['services']['postgres'], before['services']['postgres'])
        self.assertEqual(after['networks'], before['networks'])

    def test_credential_volume_and_network_changes_are_rejected(self):
        for mutate in (
            lambda m: m['services']['backend']['environment'].update(JWT_SECRET='changed'),
            lambda m: m['services']['postgres'].update(volumes=['replacement-volume:/data']),
            lambda m: m['networks']['nsec_net'].update(name='another-network'),
        ):
            changed = model()
            mutate(changed)
            with self.assertRaises(deploy.DeployError):
                deploy.assert_preserved(model(), changed)

    def test_ssh_entry_rejects_shell_and_legacy_commands(self):
        sha = 'a' * 40
        self.assertEqual(entry.parse_command('deploy-v2 ' + sha), sha)
        for value in ('deploy ' + sha, 'deploy-v2 ' + sha + '; id',
                      'deploy-v2 ' + sha + ' extra', 'bash', 'deploy-v2 ../file'):
            with self.assertRaises(ValueError):
                entry.parse_command(value)

    def test_release_rejects_non_sha_values(self):
        for value in ('main', '-option', 'a' * 39, 'A' * 40, 'a' * 40 + ';id'):
            with self.assertRaises(deploy.DeployError):
                deploy.release_sha(value)

    def test_images_require_exact_provenance_and_digest(self):
        sha = 'a' * 40
        image = deploy.REGISTRY + '-backend:sha-' + sha
        digest = deploy.REGISTRY + '-backend@sha256:' + 'b' * 64
        metadata = {'Config': {'Labels': {'org.opencontainers.image.source': deploy.SOURCE,
            'org.opencontainers.image.revision': sha}}, 'RepoDigests': [digest]}
        self.assertEqual(deploy.verified_digest(metadata, image, sha), digest)
        metadata['Config']['Labels']['org.opencontainers.image.revision'] = 'c' * 40
        with self.assertRaises(deploy.DeployError):
            deploy.verified_digest(metadata, image, sha)

    def test_config_permissions_and_symlinks_are_rejected(self):
        path = SimpleNamespace(lstat=lambda: SimpleNamespace(st_uid=0, st_mode=stat.S_IFREG | 0o600))
        deploy.private_file(path)
        for mode, uid in ((stat.S_IFREG | 0o644, 0), (stat.S_IFREG | 0o600, 1000),
                          (stat.S_IFLNK | 0o600, 0)):
            path = SimpleNamespace(lstat=lambda: SimpleNamespace(st_uid=uid, st_mode=mode))
            with self.assertRaises(deploy.DeployError):
                deploy.private_file(path)

    def test_command_failure_never_exposes_environment_values(self):
        result = subprocess.CompletedProcess([], 1, stdout=b'', stderr=b'test-only-secret')
        with patch.object(deploy.subprocess, 'run', return_value=result):
            with self.assertRaisesRegex(deploy.DeployError, '^SAFE_FAILURE$'):
                deploy.command(['docker', 'compose'], 'SAFE_FAILURE')

    def test_loopback_binding_is_required(self):
        self.assertEqual(deploy.loopback_port(model(), 'backend', 4000), 3001)
        changed = model()
        changed['services']['backend']['ports'][0]['host_ip'] = '0.0.0.0'
        with self.assertRaises(deploy.DeployError):
            deploy.loopback_port(changed, 'backend', 4000)

    def test_installer_preserves_windows_and_agent_keys(self):
        normal = 'ssh-ed25519 TEST_ONLY_WINDOWS_KEY windows'
        agent = 'restrict,command="/usr/local/sbin/nsec-agent-channel-command" ssh-ed25519 TEST_ONLY_AGENT_KEY agent'
        legacy = 'from="203.0.113.10",command="sudo -n /usr/local/sbin/legacy-deploy" ssh-ed25519 TEST_ONLY_DEPLOY_KEY deployment'
        output = installer.replace_deployment_key('\n'.join((normal, agent, legacy)),
                                                   '/usr/local/sbin/legacy-deploy')
        self.assertIn(normal, output)
        self.assertIn(agent, output)
        self.assertIn('from="203.0.113.10"', output)
        self.assertIn('command="' + installer.ENTRY + '"', output)
        self.assertEqual(installer.replace_deployment_key(output, '/usr/local/sbin/legacy-deploy'), output)
        with self.assertRaises(ValueError):
            installer.replace_deployment_key(normal, '/missing')

    def test_atomic_config_remains_private(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'config.json'
            deploy.atomic_json(path, model())
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(json.loads(path.read_text()), model())

    def test_schema_change_stops_before_up(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = root / 'compose.json'
            config.write_text(json.dumps(model()))
            (root / 'website-schema.sha256').write_text('a' * 64)
            def fake_command(args, code, **kwargs):
                if args[:3] == ['docker', 'exec', 'nsecvpn-backend']:
                    return b'different schema'
                return b''
            with patch.object(deploy, 'CONFIG', config), patch.object(deploy, 'STATE', root), \
                 patch.object(deploy, 'private_file'), patch.object(deploy, 'resolved_config', return_value=model()), \
                 patch.object(deploy, 'command', side_effect=fake_command) as commands:
                with self.assertRaisesRegex(deploy.DeployError, 'CURRENT_SCHEMA_BASELINE_MISMATCH'):
                    deploy.deploy('a' * 40)
                self.assertFalse(any('up' in call.args[0] for call in commands.call_args_list))

    def test_failed_release_restores_previous_website(self):
        sha = 'a' * 40
        schema = b'test-only-schema'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            config = root / 'compose.json'
            config.write_text(json.dumps(model()))
            (root / 'website-schema.sha256').write_text(hashlib.sha256(schema).hexdigest())
            calls = []
            def fake_command(args, code, **kwargs):
                calls.append(args)
                if args[:3] == ['docker', 'exec', 'nsecvpn-backend']:
                    return schema
                if args[:3] == ['docker', 'image', 'inspect']:
                    image = args[-1]
                    digest = image.split(':sha-')[0] + '@sha256:' + 'b' * 64
                    return json.dumps([{'Config': {'Labels': {
                        'org.opencontainers.image.source': deploy.SOURCE,
                        'org.opencontainers.image.revision': sha}},
                        'RepoDigests': [digest]}]).encode()
                if args[:2] == ['docker', 'run']:
                    return schema
                if kwargs.get('output'):
                    kwargs['output'].write(b'test-only-pg-backup')
                return b''
            with patch.object(deploy, 'CONFIG', config), patch.object(deploy, 'STATE', root), \
                 patch.object(deploy, 'BACKUPS', root), patch.object(deploy, 'private_file'), \
                 patch.object(deploy, 'resolved_config', side_effect=lambda p: json.loads(p.read_text())), \
                 patch.object(deploy, 'command', side_effect=fake_command), \
                 patch.object(deploy.subprocess, 'run', return_value=SimpleNamespace(returncode=0)), \
                 patch.object(deploy, 'wait_ready', side_effect=[deploy.DeployError('TIMEOUT'), None]):
                with self.assertRaisesRegex(deploy.DeployError, 'PREVIOUS_WEBSITE_RESTORED'):
                    deploy.deploy(sha)
            starts = [call for call in calls if 'up' in call]
            self.assertEqual(len(starts), 2)
            self.assertIn('compose.next.json', starts[0][starts[0].index('-f') + 1])
            self.assertIn('compose.rollback.json', starts[1][starts[1].index('-f') + 1])
            self.assertTrue(all(call[-2:] == list(deploy.SERVICES) for call in starts))
            self.assertEqual(json.loads(config.read_text()), model())
            self.assertFalse((root / 'website-release.json').exists())


if __name__ == '__main__':
    unittest.main()
