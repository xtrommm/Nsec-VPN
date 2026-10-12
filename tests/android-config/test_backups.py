import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('backup_guard', ROOT / 'scripts/check-android-backups.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)


class BackupPolicyTests(unittest.TestCase):
    def test_real_applications_deny_export(self):
        for name in ('app', 'speedtest-helper'):
            self.assertTrue(guard.check_module(ROOT / name))

    def test_missing_manifest_reference_is_detected(self):
        with tempfile.TemporaryDirectory() as directory:
            module = Path(directory)
            path = module / 'src/main/AndroidManifest.xml'
            path.parent.mkdir(parents=True)
            path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:allowBackup="false" /></manifest>')
            with self.assertRaisesRegex(ValueError, 'fullBackupContent'):
                guard.check_module(module)

    def test_empty_rules_do_not_count_as_disabling_backups(self):
        with self.assertRaises(ValueError):
            guard.exclusions(ET.fromstring('<cloud-backup/>'), 'cloud')

    def test_missing_device_transfer_is_detected(self):
        with self.assertRaisesRegex(ValueError, 'section missing'):
            guard.exclusions(None, 'device-transfer')

    def test_partial_exclusions_do_not_export_credentials(self):
        with self.assertRaisesRegex(ValueError, 'sharedpref'):
            guard.exclusions(ET.fromstring('<cloud-backup><exclude domain="root" path="."/></cloud-backup>'), 'cloud')

    def test_include_cannot_reenable_data_export(self):
        section = ET.fromstring('<device-transfer><include domain="sharedpref" path="credentials.xml"/></device-transfer>')
        with self.assertRaisesRegex(ValueError, 'includes data'):
            guard.exclusions(section, 'device-transfer')


if __name__ == '__main__':
    unittest.main()
