import socket, struct, sys

HOST = '127.0.0.1'
PORT = 1080


def socks5_connect(host, port, timeout=10, use_domain=False):
    s = socket.create_connection((HOST, PORT), timeout=timeout)
    s.settimeout(timeout)
    s.sendall(b'\x05\x01\x00')
    resp = s.recv(2)
    if resp != b'\x05\x00':
        raise RuntimeError('auth refusal %r' % resp)
    if use_domain:
        hb = host.encode()
        req = b'\x05\x01\x00\x03' + bytes([len(hb)]) + hb + struct.pack('>H', port)
    else:
        req = b'\x05\x01\x00\x01' + socket.inet_aton(host) + struct.pack('>H', port)
    s.sendall(req)
    head = s.recv(4)
    if len(head) < 4:
        raise RuntimeError('short reply')
    atyp = head[3]
    if atyp == 1:
        s.recv(4)
    elif atyp == 3:
        n = s.recv(1)[0]
        s.recv(n)
    elif atyp == 4:
        s.recv(16)
    s.recv(2)
    if head[1] != 0:
        raise RuntimeError('socks code %d' % head[1])
    return s


def dns_query(sock, name):
    tid = 0x4242
    header = struct.pack('>HHHHHH', tid, 0x0100, 1, 0, 0, 0)
    qname = b''.join(bytes([len(p)]) + p.encode() for p in name.split('.')) + b'\x00'
    sock.sendall(header + qname + struct.pack('>HH', 1, 1))
    data = b''
    while len(data) < 12:
        chunk = sock.recv(4096)
        if not chunk:
            break
        data += chunk
        if len(data) >= 12:
            qd, an = struct.unpack('>HH', data[4:8])
            # read question + answers fully
            need = 12
            i = 12
            for _ in range(qd):
                while data[i] != 0:
                    i += 1 + data[i]
                i += 5
            need = i
            for _ in range(an):
                if i >= len(data):
                    break
                if data[i] & 0xC0 == 0xC0:
                    i += 2
                else:
                    while data[i] != 0:
                        i += 1 + data[i]
                    i += 1
                rtype, rcls, ttl, rdlen = struct.unpack('>HHIH', data[i:i+10])
                i += 10 + rdlen
                need = i
            if len(data) >= need:
                break
    qd, an = struct.unpack('>HH', data[4:8])
    i = 12
    for _ in range(qd):
        while data[i] != 0:
            i += 1 + data[i]
        i += 5
    ips = []
    for _ in range(an):
        if data[i] & 0xC0 == 0xC0:
            i += 2
        else:
            while data[i] != 0:
                i += 1 + data[i]
            i += 1
        rtype, rcls, ttl, rdlen = struct.unpack('>HHIH', data[i:i+10])
        i += 10
        if rtype == 1 and rdlen == 4:
            ips.append('.'.join(str(b) for b in data[i:i+4]))
        i += rdlen
    return ips


print('=== DNS over TCP via ciadpi SOCKS5 (tunnel DNS 1.1.1.1:53) ===')
try:
    s = socks5_connect('1.1.1.1', 53)
    print('  SOCKS CONNECT 1.1.1.1:53 -> OK')
    print('  dns youtube.com ->', dns_query(s, 'youtube.com'))
    print('  dns chatgpt.com ->', dns_query(s, 'chatgpt.com'))
    s.close()
except Exception as e:
    print('  FAIL:', e)

print('=== DNS over TCP via ciadpi to 8.8.8.8:53 ===')
try:
    s = socks5_connect('8.8.8.8', 53)
    print('  SOCKS CONNECT 8.8.8.8:53 -> OK')
    print('  dns google.com ->', dns_query(s, 'google.com'))
    s.close()
except Exception as e:
    print('  FAIL:', e)

print('=== domain-connect (ciadpi resolves) ===')
for d in ('www.youtube.com', 'api.telegram.org'):
    try:
        s = socks5_connect(d, 443, use_domain=True)
        print(f'  CONNECT {d}:443 -> OK')
        s.close()
    except Exception as e:
        print(f'  CONNECT {d}:443 -> FAIL {e}')
