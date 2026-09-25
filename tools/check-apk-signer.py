"""Print the SHA-256 of the certificate in an APK's v2/v3 signature block.

Usage: python3 tools/check-apk-signer.py path/to/app.apk [expected-sha256]
Updates install over the phone's app only when this matches the installed signer.
"""
import hashlib, struct, sys

IDS = {0x7109871a: 'v2', 0xf05368c0: 'v3', 0x1b93ad61: 'v3.1'}

def prefixed(buf, off):
    n = struct.unpack_from('<I', buf, off)[0]
    return buf[off + 4: off + 4 + n], off + 4 + n

def signing_block(data):
    eocd = data.rfind(b'PK\x05\x06', max(0, len(data) - 65557))
    if eocd < 0: raise ValueError('not a ZIP/APK')
    cd_offset = struct.unpack_from('<I', data, eocd + 16)[0]
    if data[cd_offset - 16: cd_offset] != b'APK Sig Block 42': raise ValueError('no APK signing block (v2/v3)')
    size = struct.unpack_from('<Q', data, cd_offset - 24)[0]
    start = cd_offset - size - 8
    if struct.unpack_from('<Q', data, start)[0] != size: raise ValueError('corrupt signing block')
    return data[start + 8: cd_offset - 24]

def signers(block):
    off, out = 0, []
    while off + 12 <= len(block):
        length, pair_id = struct.unpack_from('<QI', block, off)
        value = block[off + 12: off + 8 + length]
        if pair_id in IDS:
            seq, _ = prefixed(value, 0); p = 0
            while p < len(seq):
                signer, p = prefixed(seq, p)
                signed_data, _ = prefixed(signer, 0)
                _, q = prefixed(signed_data, 0)          # digests
                certs, _ = prefixed(signed_data, q)      # certificates
                cert, _ = prefixed(certs, 0)
                out.append((IDS[pair_id], hashlib.sha256(cert).hexdigest()))
        off += 8 + length
    return out

if __name__ == '__main__':
    found = signers(signing_block(open(sys.argv[1], 'rb').read()))
    for scheme, digest in found: print(f'{scheme}: {digest}')
    if len(sys.argv) > 2:
        ok = bool(found) and all(d == sys.argv[2].lower().replace(':', '') for _, d in found)
        print('SIGNER MATCHES' if ok else 'SIGNER DOES NOT MATCH'); sys.exit(0 if ok else 1)
