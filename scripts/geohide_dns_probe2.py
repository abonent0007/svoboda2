import socket, struct, sys, time

RESOLVERS = ['159.194.200.33', '193.233.112.67']
CONTROL = '1.1.1.1'
DOMAINS = ['youtube.com', 'chatgpt.com', 'instagram.com', 'x.com', 'facebook.com', 'rutracker.org', 'meduza.io', 'www.googlevideo.com']

def build_query(name):
    tid = 0x1234
    header = struct.pack('>HHHHHH', tid, 0x0100, 1, 0, 0, 0)
    qname = b''.join(bytes([len(p)]) + p.encode() for p in name.split('.')) + b'\x00'
    return header + qname + struct.pack('>HH', 1, 1)

def parse_first_a(data):
    qd, an = struct.unpack('>HH', data[4:8])
    if an == 0:
        return []
    i = 12
    for _ in range(qd):
        while data[i] != 0:
            i += 1 + data[i]
        i += 5
    out = []
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
            out.append('.'.join(str(b) for b in data[i:i+4]))
        i += rdlen
    return out

for domain in DOMAINS:
    row = {}
    for server in RESOLVERS + [CONTROL]:
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.settimeout(5)
            s.sendto(build_query(domain), (server, 53))
            data, _ = s.recvfrom(512)
            row[server] = parse_first_a(data)
            s.close()
        except Exception as e:
            row[server] = ['FAIL:%s' % e]
    same = row[RESOLVERS[0]] == row[RESOLVERS[1]] == row[CONTROL]
    print(('SAME ' if same else 'DIFF ') + domain)
    for server, ips in row.items():
        print('    %s -> %s' % (server, ', '.join(ips)))
