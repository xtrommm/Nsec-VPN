#!/usr/bin/env python3
"""Metadata-only static/history scan; never output a matched secret or source line."""
import argparse, json, re, subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--history',action='store_true');p.add_argument('--output',required=True);a=p.parse_args()
root=Path(__file__).resolve().parents[1]
def git(*args):return subprocess.check_output(['git','-C',str(root),*args])
def eligible(path):return path.startswith(('backend/','deployment/','scripts/','.github/workflows/')) and not ('/__tests__/' in path or '/integration/' in path or '/tests/' in path or path.endswith('.env.example') or path.startswith('.github/') or path == 'scripts/scan-backend-secrets.py')
patterns=[('private-key-material',re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----')),
 ('credential-literal-or-fallback',re.compile(r'(?:JWT_SECRET|ADMIN_PASSWORD|AGENT_SECRET|PRIVATE_KEY)[^\n]*?(?:\|\||=)\s*[\'\"][^\'\"\n]{8,}[\'\"]',re.I))]
report=[];scanned=0
files=git('ls-files').decode().splitlines()
for name in files:
 if not eligible(name):continue
 path=root/name
 if not path.is_file():continue
 scanned+=1
 for n,line in enumerate(path.read_text(errors='replace').splitlines(),1):
  for kind,pattern in patterns:
   if pattern.search(line) and not ('$(openssl rand ' in line or 'secrets.token_hex(' in line):report.append({'scope':'current','path':name,'line':n,'kind':kind})
objects=0
if a.history:
 for entry in git('rev-list','--objects','--all').decode().splitlines():
  pair=entry.split(' ',1)
  if len(pair)!=2 or not eligible(pair[1]):continue
  oid,name=pair
  if git('cat-file','-t',oid).strip()!=b'blob':continue
  objects+=1
  for n,line in enumerate(git('cat-file','blob',oid).decode(errors='replace').splitlines(),1):
   for kind,pattern in patterns:
    if pattern.search(line) and not ('$(openssl rand ' in line or 'secrets.token_hex(' in line):report.append({'scope':'history','path':name,'line':n,'blob':oid,'kind':kind})
result={'currentFilesScanned':scanned,'historyBlobsScanned':objects,'findings':report,'limits':'Pattern scan excludes test fixtures/CI ephemeral credentials; provider/runtime secrets and private server history require operator review.'}
Path(a.output).write_text(json.dumps(result,indent=2))
print(json.dumps({'currentFilesScanned':scanned,'historyBlobsScanned':objects,'currentFindings':sum(r['scope']=='current' for r in report),'historicalFindings':sum(r['scope']=='history' for r in report)}))
for item in report:
 if item['scope']=='current':print(item)
