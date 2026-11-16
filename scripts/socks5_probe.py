import socket, struct, sys

HOST = '127.0.0.1'
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 1080
TARGETS = [
    ('149.154.167.50', 443, 'Telegram DC (MTProto)'),
    ('91.108.56.130', 443, 'Telegram DC alt'),
    ('web.telegram.org', 443, 'Telegram web'),
    ('core.telegram.org', 443, 'Telegram core'),
    ('www.youtube.com', 443, 'YouTube'),
]

def socks5_connect(thost, tport, timeout=8):
    s = socket.create_connection((HOST, PORT), timeout=timeout)
    s.settimeout(timeout)
    # handshake: ver=5, 1 method (no auth)
    s.sendall(b'\x05\x01\x00')
    resp = s.recv(2)
    if resp != b'\x05\x00':
        return None, 'auth refusal'
    # request
    try:
        packed = socket.inet_aton(thost)
        req = b'\x05\x01\x00\x01' + packed + struct.pack('>H', tport)
    except OSError:
        hb = thost.encode()
        req = b'\x05\x01\x00\x03' + bytes([len(hb)]) + hb + struct.pack('>H', tport)
    s.sendall(req)
    resp = s.recv(10)
    if len(resp) < 2:
        return None, 'truncated reply'
    if resp[1] == 0:
        return s, 'OK'
    return None, 'replied code %d' % resp[1]

for host, port, label in TARGETS:
    try:
        s, status = socks5_connect(host, port)
        print(f'{label:35} {host}:{port} -> {status}')
        if s:
            s.close()
    except Exception as e:
        print(f'{label:35} {host}:{port} -> FAIL {e}')
