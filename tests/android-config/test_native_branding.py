import unittest
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

    def test_extra_page_and_its_delay_are_removed(self):
        self.assertFalse((ROOT / 'app/src/main/java/com/nsec/vpn/ui/components/StartupScreen.kt').exists())
        source = (ROOT / 'app/src/main/java/com/nsec/vpn/MainActivity.kt').read_text()
        self.assertNotIn('showStartup', source)
        self.assertNotIn('Accept & Continue', source)
        self.assertIn('installSplashScreen()', source)

if __name__ == '__main__':
    unittest.main()
