#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Rebuild app/src/main/assets/warp_verified_seeds.json from .conf files.

Usage:
  python tools/rebuild_warp_seeds.py "C:/Users/<user>/Documents/Nova-main/profiles/AWG Cloudflare"

- Reads *.conf sorted by filename (WARPgen_01..24, WARPv1_*, WARPv2_*, WARPv3_*).
- seed_order = 0..N-1 (strategy #11 (1-based) == seed_order 10 == WARPgen_11.conf).
- Rewrites "MTU = 1280" -> "MTU = 1240" in raw_config.
- Copies sources into tools/seeds/awg-cloudflare/ and warp_identity.json to assets.
"""
import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "warp_verified_seeds.json")
IDENTITY_ASSET = os.path.join(ROOT, "app", "src", "main", "assets", "warp_identity.json")
SEED_COPY_DIR = os.path.join(ROOT, "tools", "seeds", "awg-cloudflare")

ENDPOINT_RE = re.compile(r"^\s*Endpoint\s*=\s*(.+?)\s*$", re.M)
MTU_RE = re.compile(r"(?m)^\s*MTU\s*=\s*\d+\s*$")

def parse_endpoint(text):
    m = ENDPOINT_RE.search(text)
    if not m:
        return None, None
    raw = m.group(1).strip()
    host, _, port = raw.rpartition(":")
    host = host.strip().strip("[]")
    try:
        port = int(port)
    except ValueError:
        return None, None
    if not host or not (1 <= port <= 65535):
        return None, None
    return host, port

def main():
    src_dir = sys.argv[1] if len(sys.argv) > 1 else None
    if not src_dir or not os.path.isdir(src_dir):
        print("usage: rebuild_warp_seeds.py <dir-with-conf-files>", file=sys.stderr)
        sys.exit(2)

    files = sorted(f for f in os.listdir(src_dir) if f.endswith(".conf"))
    entries = []
    skipped = []
    for idx, name in enumerate(files):
        path = os.path.join(src_dir, name)
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        host, port = parse_endpoint(text)
        if not host:
            skipped.append(name)
            continue
        text = MTU_RE.sub("MTU = 1240", text, count=1)
        entries.append({
            "engine": "wireguard",
            "mode": "warp-awg-exact",
            "endpoint_source": "bundled-seed",
            "success_count": 1,
            "scope": "default",
            "seed_order": len(entries),
            "preferred_sni": "",
            "source_file": f"{len(entries):03d}_{name}",
            "host": host,
            "port": port,
            "raw_config": text.strip() + "\n",
            "last_verified_at": 1700000000000,
        })

    if not entries:
        print("no usable configs found", file=sys.stderr)
        sys.exit(3)

    os.makedirs(os.path.dirname(ASSET), exist_ok=True)
    with open(ASSET, "w", encoding="utf-8") as fh:
        json.dump(entries, fh, ensure_ascii=False, indent=2)

    os.makedirs(SEED_COPY_DIR, exist_ok=True)
    for f in os.listdir(SEED_COPY_DIR):
        os.remove(os.path.join(SEED_COPY_DIR, f))
    for name in files:
        shutil.copy2(os.path.join(src_dir, name), os.path.join(SEED_COPY_DIR, name))
    identity = os.path.join(src_dir, "warp_identity.json")
    if os.path.exists(identity):
        shutil.copy2(identity, IDENTITY_ASSET)

    print(f"entries={len(entries)} skipped={skipped}")
    for i in range(min(3, len(entries))):
        e = entries[i]
        print(f"  #{i + 1:02d} seed_order={e['seed_order']} {e['source_file']} {e['host']}:{e['port']}")
    print(f"  #11 (1-based) seed_order={entries[10]['seed_order']} {entries[10]['source_file']} {entries[10]['host']}:{entries[10]['port']}")
    print(f"wrote {ASSET}")

if __name__ == "__main__":
    main()
