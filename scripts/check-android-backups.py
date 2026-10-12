#!/usr/bin/env python3
"""Fail CI when an Android application can export installation-specific state."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
DOMAINS = {'root', 'file', 'database', 'sharedpref', 'external',
           'device_root', 'device_file', 'device_database', 'device_sharedpref'}


def exclusions(section, label):
    if section is None:
        raise ValueError(label + ' section missing; Android defaults are not a deny policy')
    if section.findall('include'):
        raise ValueError(label + ' includes data despite the no-backup policy')
    covered = {entry.get('domain') for entry in section.findall('exclude')
               if entry.get('path') in ('.', './')}
    if not DOMAINS <= covered:
        raise ValueError(label + ' does not exclude complete storage domains: ' +
                         ', '.join(sorted(DOMAINS - covered)))


def resource(module, application, attribute):
    value = application.get(ANDROID + attribute, '')
    if not value.startswith('@xml/') or not value[5:].replace('_', '').isalnum():
        raise ValueError(attribute + ' must reference explicit XML rules')
    return ET.parse(module / 'src/main/res/xml' / (value[5:] + '.xml')).getroot()


def check_module(module):
    application = ET.parse(module / 'src/main/AndroidManifest.xml').getroot().find('application')
    if application is None:
        return False
    if application.get(ANDROID + 'allowBackup') != 'false':
        raise ValueError('allowBackup must remain false')
    old = resource(module, application, 'fullBackupContent')
    if old.tag != 'full-backup-content':
        raise ValueError('Invalid legacy backup rule root')
    exclusions(old, 'Android 11 and lower')
    modern = resource(module, application, 'dataExtractionRules')
    if modern.tag != 'data-extraction-rules':
        raise ValueError('Invalid modern backup rule root')
    for mode in ('cloud-backup', 'device-transfer'):
        exclusions(modern.find(mode), mode)
    for section in modern.findall('cross-platform-transfer'):
        exclusions(section, 'cross-platform-transfer')
    return True


def main():
    root = Path(__file__).resolve().parents[1]
    checked = 0
    for module in sorted(root.iterdir()):
        if not (module / 'src/main/AndroidManifest.xml').is_file():
            continue
        try:
            if check_module(module):
                checked += 1
                print('PASS: ' + module.name + ' cloud/device/legacy backup policy')
        except (ValueError, ET.ParseError, OSError) as error:
            print('FAIL: ' + module.name + ': ' + str(error), file=sys.stderr)
            return 1
    if not checked:
        print('FAIL: no Android applications checked', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
