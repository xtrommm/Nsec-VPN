#!/usr/bin/env python3
"""Capture real Android UI measurements; never read logcat, keys or traffic."""
import argparse
import csv
import json
import math
import re
import shutil
import subprocess
from pathlib import Path

PACKAGE = 'com.nsec.vpn'


def percentile(values, percentage):
    ordered = sorted(values)
    if not ordered:
        return None
    return ordered[max(0, math.ceil(len(ordered) * percentage / 100) - 1)]


def frames_from_dump(text):
    frames = []
    columns = None
    for line in text.splitlines():
        row = next(csv.reader([line.strip()]))
        if 'IntendedVsync' in row and 'FrameCompleted' in row:
            columns = row
            continue
        if columns is None or len(row) != len(columns):
            continue
        try:
            data = dict(zip(columns, map(int, row)))
        except ValueError:
            continue
        if data.get('Flags', 0) != 0:
            continue
        start, end = data['IntendedVsync'], data['FrameCompleted']
        if start <= 0 or end <= start or end >= 2**62:
            continue
        deadline = data.get('FrameDeadline', 0)
        frames.append({'timeMs': (end - start) / 1e6,
                       'deadlineMissed': end > deadline if deadline > start else None})
    times = [f['timeMs'] for f in frames]
    eligible = [f for f in frames if f['deadlineMissed'] is not None]
    return {'sampledFrames': len(frames), 'frameTimeP50Ms': percentile(times, 50),
            'frameTimeP95Ms': percentile(times, 95), 'frameTimeP99Ms': percentile(times, 99),
            'framesWithDeadline': len(eligible),
            'deadlineMissPercent': 100 * sum(f['deadlineMissed'] for f in eligible) / len(eligible) if eligible else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--label', required=True, help='before-light / after-light / before-dark / after-dark')
    parser.add_argument('--output', type=Path, default=Path('ui-profile'))
    parser.add_argument('--serial', help='Device serial only when multiple devices are attached')
    parser.add_argument('--apk', type=Path, help='Optional local APK to record its exact size')
    parser.add_argument('--cold-start', action='store_true', help='Explicitly force stop the app; disconnect VPN first')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', args.label):
        parser.error('Use a simple label with letters, digits, dashes or underscores.')
    adb = shutil.which('adb')
    if not adb:
        parser.error('adb not found. Add Android SDK platform-tools to PATH.')
    command = [adb] + (['-s', args.serial] if args.serial else [])

    def shell(*words):
        return subprocess.check_output(command + ['shell', *words], text=True, timeout=30)

    args.output.mkdir(parents=True, exist_ok=True)
    model = shell('getprop', 'ro.product.model').strip()
    android = shell('getprop', 'ro.build.version.release').strip()
    if args.cold_start:
        if input('Disconnect VPN first. Type DISCONNECTED to permit a cold start: ').strip() != 'DISCONNECTED':
            raise SystemExit('No force stop performed.')
        shell('am', 'force-stop', PACKAGE)
    startup = shell('am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    shell('dumpsys', 'gfxinfo', PACKAGE, 'reset')
    input('On the phone: scroll Servers, switch tabs, open DNS, connect/disconnect. Press Enter immediately after a janky action to capture recent frames: ')
    frame_dump = shell('dumpsys', 'gfxinfo', PACKAGE, 'framestats')
    memory = shell('dumpsys', 'meminfo', PACKAGE)
    summary = frames_from_dump(frame_dump)
    summary.update({'label': args.label, 'model': model, 'android': android,
                    'startupType': 'cold-requested' if args.cold_start else 'existing-process/warm-requested',
                    'apkBytes': args.apk.stat().st_size if args.apk else None,
                    'recompositionCounts': None,
                    'notes': 'Recent frame buffer only; frame completion latency is not presented FPS. Capture recompositions in Android Studio Layout Inspector.'})
    for field in ('TotalTime', 'WaitTime', 'ThisTime'):
        found = re.search(r'^' + field + r':\s*(\d+)', startup, re.M)
        summary[field + 'Ms'] = int(found[1]) if found else None
    found = re.search(r'TOTAL PSS:\s*(\d+)', memory)
    if found is None:
        found = re.search(r'^\s*TOTAL\s+(\d+)', memory, re.M)
    summary['totalPssKiB'] = int(found[1]) if found else None
    (args.output / (args.label + '-frames.txt')).write_text(frame_dump, encoding='utf-8')
    (args.output / (args.label + '.json')).write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary, indent=2))
    print('Saved actual device measurements. Repeat identical actions with the other build/theme; share only these summaries.')


if __name__ == '__main__':
    try:
        main()
    except (OSError, subprocess.SubprocessError) as error:
        raise SystemExit('Capture failed: ' + type(error).__name__ + '. Check USB debugging and the selected device.') from None
