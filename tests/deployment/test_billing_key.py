import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[2] / 'scripts/configure-billing-encryption.py'
@unittest.skipUnless(hasattr(os, 'geteuid') and os.geteuid() == 0, 'Root-only configuration helper')
class BillingKeyTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.config = self.root / 'compose.json'
        self.data = {'services': {'backend': {'container_name': 'nsecvpn-backend', 'environment': {'KEEP_SETTING': 'preserve'}, 'networks': ['existing']}, 'postgres': {'image': 'postgres:16-alpine'}}}
        self.config.write_text(json.dumps(self.data)); self.config.chmod(0o600)
        stub = self.root / 'docker'
        stub.write_text('#!/usr/bin/env python3\nimport json,os,sys\nif sys.argv[1]=="inspect":print(json.dumps([{"Config":{"Env":json.loads(os.environ.get("TEST_CURRENT_ENV","[]"))}}]))\nelse:print(os.environ.get("TEST_CIPHER_COUNT","0"))\n')
        stub.chmod(0o700)
        self.env = dict(os.environ, PATH=str(self.root)+os.pathsep+os.environ['PATH'])
    def tearDown(self): self.temp.cleanup()
    def run_script(self): return subprocess.run([sys.executable,str(SCRIPT),str(self.config)],capture_output=True,text=True,env=self.env)
    def test_creates_key_privately_and_preserves_network_and_other_settings(self):
        result=self.run_script(); self.assertEqual(result.returncode,0)
        saved=json.loads(self.config.read_text());key=saved['services']['backend']['environment']['PLAY_TOKEN_ENCRYPTION_KEY']
        self.assertEqual(len(key),64);self.assertNotIn(key,result.stdout+result.stderr)
        self.assertEqual(saved['services']['backend']['networks'],['existing']);self.assertEqual(saved['services']['postgres'],self.data['services']['postgres'])
        self.assertEqual(self.config.stat().st_mode & 0o777,0o600)
        self.assertEqual(self.run_script().returncode,0)
        self.assertTrue(json.loads(self.config.read_text())['services']['backend']['environment']['PLAY_TOKEN_ENCRYPTION_KEY']==key)
    def test_reuses_running_key_instead_of_losing_encrypted_receipts(self):
        key=secrets.token_hex(32);self.env['TEST_CURRENT_ENV']=json.dumps(['PLAY_TOKEN_ENCRYPTION_KEY='+key])
        result=self.run_script();self.assertEqual(result.returncode,0)
        self.assertTrue(json.loads(self.config.read_text())['services']['backend']['environment']['PLAY_TOKEN_ENCRYPTION_KEY']==key)
        self.assertNotIn(key,result.stdout+result.stderr)
    def test_refuses_unknown_key_when_encrypted_receipts_exist(self):
        self.env['TEST_CIPHER_COUNT']='1';before=self.config.read_bytes();self.assertNotEqual(self.run_script().returncode,0);self.assertEqual(self.config.read_bytes(),before)
    def test_refuses_public_configuration_file(self):
        self.config.chmod(0o644);before=self.config.read_bytes();self.assertNotEqual(self.run_script().returncode,0);self.assertEqual(self.config.read_bytes(),before)
if __name__=='__main__':unittest.main()
