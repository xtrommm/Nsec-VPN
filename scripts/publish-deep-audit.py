#!/usr/bin/env python3
"""Import reviewed commits into an isolated checkout; never modify the supplied project."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

REPOSITORY = 'https://github.com/xtrommm/Nsec-VPN.git'
BRANCH = 'fix/deep-audit-security-core'
TITLE = 'security: repair rewarded VIP and VPN peer lifecycle'


def run(args, capture=False):
    result = subprocess.run(args, check=True, text=True, stdout=subprocess.PIPE if capture else None)
    return result.stdout.strip() if capture else ''


def git(repo, *args, capture=False):
    return run(['git', '-C', str(repo), *args], capture=capture)


def prepare(project, package):
    project = project.resolve()
    if git(project, 'rev-parse', '--show-toplevel', capture=True).replace('\\', '/').lower() != str(project).replace('\\', '/').lower():
        raise SystemExit('Use the Git project root, not a folder inside it.')
    manifest = json.loads((package / 'repair-manifest.json').read_text(encoding='utf-8'))
    if manifest.get('branch') != BRANCH or not all(re.fullmatch(r'[a-f0-9]{40}', manifest.get(field, '')) for field in ('base', 'head')):
        raise SystemExit('Invalid repair manifest; no project changes made.')
    bundle = package / 'security-core.bundle'
    if hashlib.sha256(bundle.read_bytes()).hexdigest() != manifest.get('bundleSha256'):
        raise SystemExit('Repair bundle failed its integrity check; download it again.')
    destination = project.parent / 'Nsec-VPN-deep-audit-repair'
    if destination.exists():
        try:
            matches = git(destination, 'rev-parse', 'HEAD', capture=True) == manifest['head'] and git(destination, 'branch', '--show-current', capture=True) == BRANCH
        except subprocess.CalledProcessError:
            matches = False
        if not matches:
            destination = project.parent / ('Nsec-VPN-deep-audit-repair-' + manifest['head'][:8])
            if destination.exists():
                raise SystemExit('Repair destination already exists. Keep it and review it; nothing was overwritten.')
    if not destination.exists():
        run(['git', 'clone', '--no-hardlinks', str(project), str(destination)])
        git(destination, 'remote', 'set-url', 'origin', REPOSITORY)
        git(destination, 'fetch', 'origin', 'main')
        git(destination, 'cat-file', '-e', manifest['base'] + '^{commit}')
        git(destination, 'bundle', 'verify', str(bundle))
        git(destination, 'fetch', str(bundle), BRANCH)
        git(destination, 'switch', '-c', BRANCH, manifest['head'])
    if git(destination, 'rev-parse', 'HEAD', capture=True) != manifest['head']:
        raise SystemExit('Unexpected checkout; stop and review. No push was attempted.')
    print('Reviewed repair checkout: ' + str(destination))
    print('Original project and uncommitted Android edits were not changed.')
    return destination, manifest


def publish(destination, manifest):
    # Push exactly the imported commits, never stage local files or replace an existing remote branch.
    git(destination, 'remote', 'set-url', 'origin', REPOSITORY)
    git(destination, 'fetch', 'origin', 'main')
    current_main = git(destination, 'rev-parse', 'FETCH_HEAD', capture=True)
    if current_main != manifest['base']:
        raise SystemExit('Main changed after this audit. The repair is prepared locally; review the newer main before publishing.')
    git(destination, 'push', '-u', 'origin', 'HEAD:refs/heads/' + BRANCH)
    url = 'https://github.com/xtrommm/Nsec-VPN/compare/main...' + BRANCH + '?expand=1'
    gh = shutil.which('gh')
    if gh:
        found = subprocess.run([gh, 'pr', 'view', BRANCH, '--repo', 'xtrommm/Nsec-VPN', '--json', 'url'], capture_output=True, text=True)
        if found.returncode == 0:
            print('Existing PR: ' + json.loads(found.stdout)['url'])
        else:
            result = subprocess.run([gh, 'pr', 'create', '--repo', 'xtrommm/Nsec-VPN', '--draft', '--base', 'main', '--head', BRANCH,
                                     '--title', TITLE, '--body-file', str(destination / 'docs/audit/PR-BODY.md')])
            if result.returncode:
                print('Branch pushed. Open the PR here: ' + url)
    else:
        print('Branch pushed. Click Create pull request here: ' + url)
    print('Main was not changed. No containers or servers were deployed. Wait for full CI and migration preparation before merging.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('project', type=Path)
    parser.add_argument('--publish', action='store_true')
    args = parser.parse_args()
    destination, manifest = prepare(args.project, Path(__file__).resolve().parent)
    if args.publish:
        publish(destination, manifest)
    else:
        print('No push performed. Review docs/audit/REPAIR-CHECKPOINT.md in the new checkout.')


if __name__ == '__main__':
    try:
        main()
    except (subprocess.CalledProcessError, OSError, ValueError, KeyError) as failure:
        raise SystemExit('Stopped: ' + type(failure).__name__ + '. Existing project files were preserved; inspect the preceding Git message.') from None
