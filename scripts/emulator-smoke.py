#!/usr/bin/env python3
"""Exercise public Markdown entries on an emulator, with optional real SSE rounds."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    args = argparse.ArgumentParser()
    args.add_argument('--serial', required=True)
    args.add_argument('--apk', type=Path, required=True)
    args.add_argument('--output', type=Path, required=True)
    args.add_argument('--api-rounds', type=int, default=0)
    args.add_argument('--skip-fixtures', action='store_true')
    args.add_argument('--modes', default='live,lazy,wrapped,snapshot,reuse')
    args.add_argument('--lifecycle', action='store_true')
    args.add_argument('--diagnostics', action='store_true')
    options = args.parse_args()
    options.output.mkdir(parents=True, exist_ok=True)
    package = 'com.kuikly.kuiklymarkdown'
    activity = package + '/.KuiklyRenderActivity'

    def adb(*parts, timeout=30):
        return subprocess.check_output(['adb', '-s', options.serial, *parts], timeout=timeout)

    assert adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Refusing a physical device'
    adb('install', '-r', str(options.apk.resolve()), timeout=90)

    def tree(name):
        remote = '/sdcard/markdown-smoke.xml'
        adb('shell', 'rm', '-f', remote)
        adb('shell', 'uiautomator', 'dump', remote)
        data = adb('shell', 'cat', remote)
        (options.output / (name + '.xml')).write_bytes(data)
        return ET.fromstring(data)

    def strings(ui):
        return [n.get('content-desc') or n.get('text') for n in ui.iter('node')
                if n.get('content-desc') or n.get('text')]

    def screenshot(name):
        (options.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))

    def logs(name):
        pid = adb('shell', 'pidof', package).decode().strip()
        assert pid, 'Application terminated'
        data = adb('logcat', '-d', '--pid=' + pid, '-v', 'threadtime').decode(errors='replace')
        (options.output / (name + '.log')).write_text(data)
        assert not re.search(r'FATAL EXCEPTION|SyntaxError|Invalid regular|sse error', data), name

    results = []
    for mode in (() if options.skip_fixtures else options.modes.split(',')):
        adb('shell', 'am', 'force-stop', package)
        adb('logcat', '-c')
        adb('shell', 'am', 'start', '-S', '-W', '-n', activity, '--es', 'pageName', 'markdown_regression', '--es', 'mode', mode)
        time.sleep(0.5)
        adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
        time.sleep(7)
        for _ in range(5):
            try:
                texts = strings(tree(mode))
            except subprocess.CalledProcessError:
                time.sleep(2)
                continue
            if 'Done ' + mode in texts:
                break
            time.sleep(2)
        assert 'Done ' + mode in texts, (mode, texts)
        if mode == 'snapshot':
            assert any('Captured document' in t for t in texts), texts
            assert not any('Replacement document' in t for t in texts), texts
        else:
            assert any(('Large code' if mode.startswith('large-code') else 'Section 0') in t for t in texts), texts
        screenshot(mode)
        (options.output / (mode + '-stream-gfxinfo.txt')).write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package))
        adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
        for _ in range(7):
            adb('shell', 'input', 'swipe', '540', '1700', '540', '550', '300')
        screenshot(mode + '-scroll')
        (options.output / (mode + '-gfxinfo.txt')).write_bytes(adb('shell', 'dumpsys', 'gfxinfo', package))
        logs(mode)
        results.append({'mode': mode, 'passed': True})
        print(json.dumps(results[-1]), flush=True)

    if options.api_rounds:
        # This is the dedicated demo package, not a user application.
        adb('shell', 'am', 'force-stop', package)
        adb('shell', 'pm', 'clear', package)
        adb('shell', 'am', 'start', '-n', activity, '--ez', 'aiChatDiagnostics', str(options.diagnostics).lower())
        time.sleep(3)
        for index in range(options.api_rounds):
            adb('logcat', '-c')
            ui = tree('api-ready-' + str(index))
            button = next(n for n in ui.iter('node') if n.get('content-desc') == 'Send')
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', button.get('bounds')))
            adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
            start = time.monotonic()
            done = False
            for poll in range(40):
                time.sleep(5)
                screenshot('api-%d-%d' % (index, poll))
                try:
                    texts = strings(tree('api-%d-%d' % (index, poll)))
                except subprocess.CalledProcessError:
                    # UIAutomator may be unable to obtain idle state during active rendering.
                    continue
                assert not any(t.startswith('Error') for t in texts), texts
                if 'Completed' in texts:
                    done = True
                    break
            assert done, 'Real SSE did not complete'
            screenshot('api-completed-' + str(index))
            logs('api-' + str(index))
            results.append({'api_round': index + 1, 'passed': True, 'seconds': round(time.monotonic() - start, 2)})
            print(json.dumps(results[-1]), flush=True)

        if options.lifecycle:
            accelerometer = adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation').decode().strip()
            rotation = adb('shell', 'settings', 'get', 'system', 'user_rotation').decode().strip()
            try:
                adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
                for position in ('1', '0'):
                    adb('shell', 'settings', 'put', 'system', 'user_rotation', position)
                    time.sleep(3)
                    texts = strings(tree('api-rotation-' + position))
                    assert any(t.startswith('Completed') for t in texts), texts
                    screenshot('api-rotation-' + position)
            finally:
                for key, value in (('accelerometer_rotation', accelerometer), ('user_rotation', rotation)):
                    if value == 'null':
                        adb('shell', 'settings', 'delete', 'system', key)
                    else:
                        adb('shell', 'settings', 'put', 'system', key, value)
            adb('shell', 'am', 'start', '-S', '-W', '-n', activity)
            time.sleep(3)
            texts = strings(tree('api-restored'))
            assert 'Completed (restored)' in texts, texts
            screenshot('api-restored')
            button = next(n for n in tree('api-before-stop').iter('node') if n.get('content-desc') == 'Send')
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', button.get('bounds')))
            adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(2)
            button = next(n for n in tree('api-stop').iter('node') if n.get('content-desc') == 'Stop')
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', button.get('bounds')))
            adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(3)
            texts = strings(tree('api-stopped'))
            assert 'Stopped' in texts and 'Send' in texts, texts
            screenshot('api-stopped')
            logs('api-lifecycle')
            results.append({'lifecycle': True, 'passed': True})
            print(json.dumps(results[-1]), flush=True)
    (options.output / 'results.json').write_text(json.dumps(results, indent=2))


if __name__ == '__main__':
    main()
