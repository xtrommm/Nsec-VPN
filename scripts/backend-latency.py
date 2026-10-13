#!/usr/bin/env python3
"""Read-only latency measurements. Never record bodies, identities, headers or credentials."""
import argparse, json, math, os, statistics, time, urllib.request, urllib.error
p=argparse.ArgumentParser();p.add_argument('base_url');p.add_argument('--samples',type=int,default=20);p.add_argument('--output',required=True)
a=p.parse_args()
if not a.base_url.startswith('https://') or not 5 <= a.samples <= 100: raise SystemExit('Use an HTTPS origin and 5–100 samples')
from urllib.parse import urlsplit
origin=urlsplit(a.base_url)
if origin.username or origin.password or origin.query or origin.fragment or origin.path not in ('','/'): raise SystemExit('Use only an HTTPS origin')
checks=[('/health',None),('/ready',None),('/api/v1/config',None),('/api/v1/legal/privacy',None),('/api/v1/legal/terms',None)]
if os.getenv('NSEC_DEVICE_TOKEN'):
 checks += [(path,os.environ['NSEC_DEVICE_TOKEN']) for path in ['/api/v1/auth/me','/api/v1/servers','/api/v1/auth/recovery/status']]
if os.getenv('NSEC_ADMIN_TOKEN'):
 checks += [(path,os.environ['NSEC_ADMIN_TOKEN']) for path in ['/api/v1/admin/me','/api/v1/admin/servers','/api/v1/admin/stats','/api/v1/admin/devices','/api/v1/admin/promos','/api/v1/admin/server-requests','/api/v1/admin/config','/api/v1/admin/audit-logs','/api/v1/admin/mfa/status']]
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,req,fp,code,msg,headers,newurl): return None
opener=urllib.request.build_opener(NoRedirect())
report=[]
for path,token in checks:
 durations=[];statuses={}
 for _ in range(a.samples):
  request=urllib.request.Request(a.base_url.rstrip('/')+path,headers={'Authorization':'Bearer '+token} if token else {})
  start=time.perf_counter()
  try:
   with opener.open(request,timeout=15) as r:r.read();status=str(r.status)
  except urllib.error.HTTPError as e:status=str(e.code);e.close()
  except Exception:status='NETWORK_ERROR'
  durations.append((time.perf_counter()-start)*1000);statuses[status]=statuses.get(status,0)+1
  time.sleep(.6)
 values=sorted(durations);row={'endpoint':path,'samples':a.samples,'p50_ms':round(statistics.median(values),2),'p95_ms':round(values[max(0,math.ceil(.95*len(values))-1)],2),'statuses':statuses};report.append(row)
 print(path,row['p50_ms'],row['p95_ms'],statuses)
with open(a.output,'w') as stream:json.dump(report,stream,indent=2)
print('Only endpoint names, timings and aggregate status counts saved; no response bodies or credentials')
