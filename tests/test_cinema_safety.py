"""Regression contract for the explicit-launch G-GUARD cinema variant."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
A = '{http://schemas.android.com/apk/res/android}'


class CinemaSafetyTest(unittest.TestCase):
    def setUp(self):
        self.root = ET.parse(ROOT / 'launcher/app/src/main/AndroidManifest.xml').getroot()

    def test_no_system_mutation_permissions_or_background_components(self):
        self.assertEqual([v.get(A + 'name') for v in self.root.findall('uses-permission')],
                         ['android.permission.ACCESS_NETWORK_STATE'])
        app = self.root.find('application')
        for kind in ('service', 'receiver', 'provider', 'activity-alias'):
            self.assertEqual(app.findall(kind), [], kind)
        activities = app.findall('activity')
        self.assertEqual([v.get(A + 'name') for v in activities], ['com.gathin.tvhome.CinemaActivity'])

    def test_independent_application_identity(self):
        gradle = (ROOT / 'launcher/app/build.gradle').read_text()
        self.assertIn("applicationId 'com.gathin.tvhome.cinema'", gradle)
        self.assertNotIn("getOrElse('com.gathin.tvhome')", gradle)
        # The Java namespace is intentionally distinct from the install identity.
        self.assertIn("namespace 'com.gathin.tvhome'", gradle)

    def test_explicit_launch_only(self):
        categories = {v.get(A + 'name') for v in self.root.iter('category')}
        self.assertNotIn('android.intent.category.HOME', categories)
        self.assertIn('android.intent.category.LEANBACK_LAUNCHER', categories)
        self.assertIn('android.intent.category.LAUNCHER', categories)
        self.assertFalse(any(v.get(A + 'name') == 'android.intent.action.BOOT_COMPLETED'
                             for v in self.root.iter('action')))


if __name__ == '__main__':
    unittest.main()
