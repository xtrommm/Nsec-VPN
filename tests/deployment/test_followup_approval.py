import contextlib
import hashlib
import io
import json
from pathlib import Path
import runpy
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'scripts/approve-audit-followup-schema.py'
SHA = 'a' * 40

class FollowupApprovalTests(unittest.TestCase):
    def run_script(self, folder, migrated=True, revision=SHA, schema_match=True):
        real_path = Path
        schema = (ROOT / 'backend/prisma/schema.prisma').read_bytes()
        def path(value):
            return real_path(folder) / 'state' if str(value) == '/var/lib/nsecvpn-deploy' else real_path(value)
        def command(args, **_kwargs):
            if args[:3] == ['docker', 'image', 'inspect']:
                stdout = json.dumps([{'Config': {'Labels': {'org.opencontainers.image.source': 'https://github.com/xtrommm/Nsec-VPN', 'org.opencontainers.image.revision': revision}}}]).encode()
            elif args[:2] == ['docker', 'run']:
                stdout = schema if schema_match else b'wrong schema'
            elif args[:2] == ['docker', 'exec']:
                stdout = b'MIGRATED\n' if migrated else b'MISSING\n'
            else:
                stdout = b''
            return SimpleNamespace(returncode=0, stdout=stdout)
        with patch('os.geteuid', return_value=0, create=True), patch('sys.argv', [str(SCRIPT), SHA]), patch('subprocess.run', side_effect=command), patch('pathlib.Path', side_effect=path), contextlib.redirect_stdout(io.StringIO()):
            runpy.run_path(str(SCRIPT), run_name='__main__')
        return schema

    def test_approval_requires_exact_image_schema_and_migration(self):
        with tempfile.TemporaryDirectory() as folder:
            schema = self.run_script(folder)
            result = json.loads((Path(folder) / 'state/approved-next-schema.json').read_text())
            self.assertEqual(result, {'sha': SHA, 'schemaHash': hashlib.sha256(schema).hexdigest()})

    def test_failure_cannot_write_an_approval(self):
        for options in ({'migrated': False}, {'revision': 'b' * 40}, {'schema_match': False}):
            with tempfile.TemporaryDirectory() as folder:
                with self.assertRaises(SystemExit): self.run_script(folder, **options)
                self.assertFalse((Path(folder) / 'state/approved-next-schema.json').exists())

if __name__ == '__main__': unittest.main()
