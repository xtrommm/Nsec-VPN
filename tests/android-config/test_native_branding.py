import unittest
import re
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class NativeBrandingTest(unittest.TestCase):
    def test_branding_is_on_original_day_and_night_splash(self):
        for folder in ('values-v31', 'values-night-v31'):
            tree = ET.parse(ROOT / 'app/src/main/res' / folder / 'themes.xml')
            items = {i.attrib['name']: i.text for i in tree.findall("./style[@name='Theme.App.Starting']/item")}
            self.assertEqual('@drawable/splash_europe_branding', items['android:windowSplashScreenBrandingImage'])

    def test_vector_has_platform_branding_dimensions_and_theme_contrast(self):
        vector = ET.parse(ROOT / 'app/src/main/res/drawable/splash_europe_branding.xml').getroot()
        self.assertEqual('200dp', vector.attrib[ANDROID + 'width'])
        self.assertEqual('80dp', vector.attrib[ANDROID + 'height'])
        self.assertIn('@color/splash_branding_text', [p.attrib[ANDROID + 'fillColor'] for p in vector])

    def test_flag_follows_text_without_changing_baseline_or_footprint(self):
        vector = ET.parse(ROOT / 'app/src/main/res/drawable/splash_europe_branding.xml').getroot()
        flag = next(p for p in vector if p.attrib[ANDROID + 'fillColor'] == '#003399')
        flag_path = flag.attrib[ANDROID + 'pathData']
        flag_left = float(re.search(r'M\s*([0-9.]+)', flag_path)[1])
        text_paths = [p.attrib[ANDROID + 'pathData'] for p in vector if p.attrib[ANDROID + 'fillColor'].startswith('@')]
        text_x = [float(m[1]) for path in text_paths for m in re.finditer(r'[MLH]\s*([0-9.]+)', path)]
        self.assertGreater(flag_left, max(text_x))
        self.assertAlmostEqual(22.0, min(text_x), places=2)
        self.assertIn('34.586', flag_path)
        self.assertIn('50.586', flag_path)

    def test_extra_page_and_its_delay_are_removed(self):
        self.assertFalse((ROOT / 'app/src/main/java/com/nsec/vpn/ui/components/StartupScreen.kt').exists())
        source = (ROOT / 'app/src/main/java/com/nsec/vpn/MainActivity.kt').read_text()
        self.assertNotIn('showStartup', source)
        self.assertNotIn('Accept & Continue', source)
        self.assertIn('installSplashScreen()', source)

    def test_branding_paths_stay_below_lint_complexity_limit(self):
        vector = ET.parse(ROOT / 'app/src/main/res/drawable/splash_europe_branding.xml').getroot()
        for path in vector.findall('path'):
            self.assertLessEqual(len(path.attrib[ANDROID + 'pathData']), 3000)

if __name__ == '__main__':
    unittest.main()
