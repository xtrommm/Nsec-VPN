#!/usr/bin/env bash
set -uo pipefail
[[ $EUID == 0 ]] || { echo 'Run as root'; exit 1; }
iface=${WG_INTERFACE:-wg0}
failed=0
check() { if "$@" >/dev/null 2>&1; then echo "PASS: $*"; else echo "FAIL: $*"; failed=1; fi; }
check ip link show dev "$iface"
check wg show "$iface" public-key
check test "$(sysctl -n net.ipv4.ip_forward)" = 1
check systemctl is-active "wg-quick@$iface"
check systemctl is-enabled "wg-quick@$iface"
check systemctl is-active nsec-agent
check test -s "/etc/wireguard/$iface.conf"
check test "$(stat -c '%a' "/etc/wireguard/$iface.conf")" = 600
check test "$(wg show "$iface" listen-port)" = "${WG_PORT:-51820}"
check curl -4fsS --max-time 5 https://ifconfig.me/ip
uplink=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="dev") {print $(i+1);exit}}')
subnet=$(ip -4 -o addr show dev "$iface" | awk '{print $4;exit}')
subnet=$(python3 - "$subnet" <<'PY'
import ipaddress,sys
print(ipaddress.ip_network(sys.argv[1],strict=False))
PY
)
check iptables -t nat -C POSTROUTING -s "$subnet" -o "$uplink" -j MASQUERADE
check iptables -C FORWARD -i "$iface" -o "$uplink" -j ACCEPT
dns_probe() {
  dig +time=3 +tries=1 +short @1.1.1.1 example.org A | python3 -c 'import sys,ipaddress; answers=sys.stdin.read().split(); valid=[a for a in answers if a.count(".")==3 and not a.endswith(".")]; sys.exit(0 if valid and all(ipaddress.IPv4Address(a).is_global for a in valid) else 1)'
}
if dns_probe >/dev/null 2>&1; then echo 'PASS: public DNS resolution'; else echo 'FAIL: public DNS resolution'; failed=1; fi
if python3 - "$iface" <<'PYHEALTH'
import urllib.request,hashlib,hmac,json,time,subprocess,sys
values={}
with open('/etc/nsec-agent.env') as stream:
    for line in stream:
        key,sep,value=line.strip().partition('=')
        if sep: values[key]=value
secret=values.get('AGENT_SECRET','').encode()
if len(secret)<32:raise SystemExit(1)
ts=str(int(time.time()*1000));path='/api/v1/agent/health'
sign=hmac.new(secret,f'{ts}.GET.{path}.'.encode(),hashlib.sha256).hexdigest()
request=urllib.request.Request('http://127.0.0.1:'+values.get('AGENT_PORT','51821')+path,headers={'X-Agent-Timestamp':ts,'X-Agent-Signature':sign,'X-Agent-Version':'2'})
try:
    with urllib.request.urlopen(request,timeout=5) as response:
        body=response.read();signature=response.headers.get('X-Agent-Response-Signature','')
    expected=hmac.new(secret,f'response.{ts}.GET.{path}.'.encode()+body,hashlib.sha256).hexdigest()
    if not hmac.compare_digest(signature,expected):raise ValueError('signature')
    health=json.loads(body)
    key=subprocess.check_output(['wg','show',sys.argv[1],'public-key'],text=True).strip()
    if not health.get('online') or health.get('publicKey')!=key:raise ValueError('health')
except Exception:raise SystemExit(1)
PYHEALTH
then echo 'PASS: authenticated agent health matches kernel key'; else echo 'FAIL: authenticated agent health'; failed=1; fi
check iptables -C INPUT -p tcp --dport "${AGENT_PORT:-51821}" -j NSEC_AGENT
check iptables -C NSEC_AGENT -j DROP
# Counts only: no peer endpoint IPs or traffic content.
handshakes=$(wg show "$iface" latest-handshakes | awk '$2 > 0 {n++} END {print n+0}')
printf 'Observed peer handshakes: %s (zero is valid when nobody is connected)\n' "$handshakes"
echo 'Cloud Security Group and phone exit IP require separate verification.'
exit "$failed"
