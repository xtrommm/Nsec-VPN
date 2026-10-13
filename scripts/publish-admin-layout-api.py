#!/usr/bin/env python3
"""Prepare an isolated Git checkout; never overwrite extracted archives or existing work."""
import argparse,hashlib,json,re,subprocess,shutil
from pathlib import Path
REPO='https://github.com/xtrommm/Nsec-VPN.git'
BRANCH='fix/admin-layout-api-performance'
def git(root,*args,capture=False):
 p=subprocess.run(['git','-C',str(root),*args],check=True,text=True,stdout=subprocess.PIPE if capture else None)
 return p.stdout.strip() if capture else ''
def main():
 p=argparse.ArgumentParser();p.add_argument('--publish',action='store_true');args=p.parse_args()
 package=Path(__file__).resolve().parent
 m=json.loads((package/'repair-manifest.json').read_text())
 if m.get('branch')!=BRANCH or any(not re.fullmatch('[a-f0-9]{40}',m.get(k,'')) for k in ('base','head')):raise SystemExit('STOP: invalid manifest')
 bundle=package/'admin-layout-api.bundle'
 if hashlib.sha256(bundle.read_bytes()).hexdigest()!=m['bundleSha256']:raise SystemExit('STOP: archive integrity check failed')
 destination=Path.home()/'Downloads'/('Nsec-VPN-admin-layout-api-repair-'+m['head'][:8])
 if destination.exists():
  if git(destination,'rev-parse','HEAD',capture=True)!=m['head'] or git(destination,'branch','--show-current',capture=True)!=BRANCH or git(destination,'status','--porcelain',capture=True):raise SystemExit('STOP: keep existing checkout and review it; nothing overwritten')
 else:
  subprocess.run(['git','clone','--no-checkout','--single-branch','--branch','main',REPO,str(destination)],check=True)
  git(destination,'bundle','verify',str(bundle));git(destination,'fetch',str(bundle),BRANCH);git(destination,'switch','-c',BRANCH,m['head'])
 print('Git checkout: '+str(destination))
 if not args.publish:print('Prepared only. Run again with --publish to push.');return
 git(destination,'fetch','origin','main')
 if git(destination,'rev-parse','FETCH_HEAD',capture=True)!=m['base']:raise SystemExit('STOP: main changed since review. Checkout preserved; no push attempted')
 git(destination,'push','-u','origin','HEAD:refs/heads/'+BRANCH)
 url='https://github.com/xtrommm/Nsec-VPN/compare/main...'+BRANCH+'?expand=1'
 gh=shutil.which('gh')
 if gh:
  existing=subprocess.run([gh,'pr','view',BRANCH,'--repo','xtrommm/Nsec-VPN','--json','url'],capture_output=True,text=True)
  if existing.returncode==0:print('PR: '+json.loads(existing.stdout)['url'])
  else:subprocess.run([gh,'pr','create','--repo','xtrommm/Nsec-VPN','--base','main','--head',BRANCH,'--title','fix: align admin layout and reduce API work','--body-file',str(destination/'docs/audit/ADMIN-LAYOUT-API-PR.md')],check=False)
 print('Open/create PR: '+url)
 print('Main was not changed. No servers deployed. Wait for CI before merging. No migration or Android rebuild required.')
if __name__=='__main__':
 try:main()
 except (subprocess.CalledProcessError,OSError,KeyError,ValueError) as e:raise SystemExit('Stopped: '+type(e).__name__+'. Existing projects were not modified; inspect the preceding Git message.') from None
