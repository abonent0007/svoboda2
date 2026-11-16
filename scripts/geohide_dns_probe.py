import socket, struct, sys, time

RESOLVERS = ['159.194.200.33', '193.233.112.67', '37.230.192.51']
CONTROL = ['1.1.1.1']
QUERY = 'youtube.com'

def build_query(name):
    tid = int.from_bytes(socket.getentropy(2), 'big') if hasattr(socket, 'getentropy') else 0x1234
    flags = 0x0100  # RD
    header = struct.pack('>HHHHHH', tid, flags, 1, 0, 0, 0)
    qname = b''.join(bytes([len(p)]) + p.encode() for p in name.split('.')) + b'\x00'
    question = qname + struct.pack('>HH', 1, 1)
    return header + question

def parse_answers(data):
    ids = struct.unpack('>H', data[:2])[0]
    qd, an = struct.unpack('>HH', data[4:8])
    print(f'  answers={an}')
    # skip header + question
    i = 12
    for _ in range(qd):
        while data[i] != 0:
            i += 1 + data[i]
        i += 5
    out = []
    for _ in range(an):
        # skip possible name compression
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

for server in RESOLVERS + CONTROL:
    t0 = time.time()
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(6)
        q = build_query(QUERY)
        s.sendto(q, (server, 53))
        data, _ = s.recvfrom(512)
        el = (time.time() - t0) * 1000
        print(f'{server}: replies in {el:.0f} ms')
        print('   ips:', parse_answers(data))
        s.close()
    except Exception as e:
        print(f'{server}: FAIL - {e}')
