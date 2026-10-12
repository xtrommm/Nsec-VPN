import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

class FirewallTests(unittest.TestCase):
    def test_idempotent_up_and_down_after_interface_is_removed(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            source = (ROOT / 'deployment/install-wireguard-node.sh').read_text()
            helper = source.split("<<'FIREWALL'\n", 1)[1].split('\nFIREWALL', 1)[0]
            script = directory / 'helper.sh'
            script.write_text(helper)
            for fixture in ['ip', 'iptables']:
                target = directory / fixture
                target.write_text((ROOT / 'tests/deployment' / fixture).read_text())
                target.chmod(0o700)
            environment = dict(os.environ, PATH=str(directory)+':'+os.environ['PATH'],
                               NSEC_WG_FIREWALL_STATE_DIR=str(directory/'state'),
                               FIREWALL_TEST_STATE=str(directory/'rules.json'))
            subprocess.run(['bash',str(script),'up','wg0'],check=True,env=environment)
            subprocess.run(['bash',str(script),'up','wg0'],check=True,env=environment)
            self.assertEqual(len(json.loads((directory/'rules.json').read_text())),3)
            environment['FIREWALL_TEST_INTERFACE_GONE']='1'
            subprocess.run(['bash',str(script),'down','wg0'],check=True,env=environment)
            self.assertEqual(json.loads((directory/'rules.json').read_text()),[])
            self.assertFalse((directory/'state/wg0.state').exists())

if __name__ == '__main__':
    unittest.main()
