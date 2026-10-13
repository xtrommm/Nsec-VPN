import re
import subprocess
import tempfile
import unittest
from pathlib import Path


class NodeRuntimeTest(unittest.TestCase):
    def test_installer_upgrades_eol_node20_but_retains_supported_22_and_24(self):
        source = (Path(__file__).resolve().parents[2] / 'deployment/install-wireguard-node.sh').read_text()
        match = re.search(r'^if ! command -v node.*then$', source, re.MULTILINE)
        self.assertIsNotNone(match)
        with tempfile.TemporaryDirectory() as folder:
            node = Path(folder) / 'node'
            for major, expected in ((20, 0), (22, 1), (24, 1)):
                node.write_text('#!/bin/sh\nprintf "' + str(major) + '\\n"\n')
                node.chmod(0o700)
                script = 'PATH=' + folder + ':"$PATH"\n' + match.group() + '\nexit 0\nelse\nexit 1\nfi'
                self.assertEqual(subprocess.run(['bash', '-c', script], check=False).returncode, expected)
