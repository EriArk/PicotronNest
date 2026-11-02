import json
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
NS='{http://schemas.android.com/apk/res/android}'


class CandidateContract(unittest.TestCase):
    def test_single_launcher_and_private_components(self):
        root=ET.parse(ROOT/'packaging/AndroidManifest.xml').getroot()
        app=root.find('application')
        self.assertEqual(app.get(NS+'debuggable'),'false')
        self.assertEqual(app.get(NS+'allowBackup'),'false')
        exported=[node for node in app if node.get(NS+'exported')=='true']
        self.assertEqual(len(exported),1)
        self.assertEqual(exported[0].get(NS+'name'),'art.picotronnest.executionlab.LauncherActivity')
        self.assertEqual(len(app.findall('.//intent-filter')),1)
        permissions={node.get(NS+'name') for node in root.findall('uses-permission')}
        self.assertEqual(permissions,{'android.permission.INTERNET','android.permission.FOREGROUND_SERVICE','android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK'})
        self.assertEqual(root.find('uses-sdk').get(NS+'targetSdkVersion'),'36')

    def test_version_agreement(self):
        config=json.loads((ROOT/'packaging/candidate.json').read_text())
        root=ET.parse(ROOT/'packaging/AndroidManifest.xml').getroot()
        self.assertEqual(root.get('package'),config['application_id'])
        self.assertEqual(root.get(NS+'versionName'),config['version_name'])
        self.assertEqual(int(root.get(NS+'versionCode')),config['version_code'])


if __name__=='__main__':
    unittest.main()
