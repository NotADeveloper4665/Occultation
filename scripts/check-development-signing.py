#!/usr/bin/env python3
"""Verify every published APK has one signer and matches the existing public pin."""
import argparse
from pathlib import Path
import re
import subprocess


def verify_signers(apks, apksigner, expected=None):
    signers = set()
    for apk in apks:
        result = subprocess.run([str(apksigner), 'verify', '--print-certs', str(apk)],
                                check=True, text=True, capture_output=True)
        certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', result.stdout, re.M)
        if len(certificates) != 1:
            raise ValueError(f'{apk.name}: expected exactly one APK signer')
        signers.add(certificates[0].lower())
    if len(signers) != 1:
        raise ValueError('All architecture APKs must have the same signing certificate')
    signer = signers.pop()
    if expected is not None and signer != expected.strip().lower():
        raise ValueError('Signing certificate changed. Restore the retained development keystore; do not silently rotate keys.')
    return signer


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apks', type=Path, required=True)
    parser.add_argument('--apksigner', type=Path, required=True)
    parser.add_argument('--expected', type=Path)
    args = parser.parse_args()
    apks = sorted(args.apks.glob('*.apk'))
    if not apks:
        raise SystemExit('No APKs to verify')
    expected = args.expected.read_text() if args.expected and args.expected.exists() else None
    signer = verify_signers(apks, args.apksigner, expected)
    (args.apks / 'development-signer.sha256').write_text(signer + '\n')
    print('All APK signing certificates verified' + (' against the published signer' if expected else ' for initial development-key retention'))


if __name__ == '__main__':
    main()
