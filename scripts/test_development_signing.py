import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('signing', Path(__file__).with_name('check-development-signing.py'))
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)

class SigningTests(unittest.TestCase):
    def output(self, fingerprint):
        return subprocess.CompletedProcess([], 0, f'Signer #1 certificate SHA-256 digest: {fingerprint}\n', '')
    def test_preserves_expected_signer_and_rejects_rotation(self):
        with patch.object(signing.subprocess, 'run', return_value=self.output('a'*64)):
            self.assertEqual('a'*64, signing.verify_signers([Path('arm64.apk')], 'apksigner', 'a'*64))
            with self.assertRaises(ValueError): signing.verify_signers([Path('arm64.apk')], 'apksigner', 'b'*64)
    def test_rejects_mixed_architecture_signers(self):
        with patch.object(signing.subprocess, 'run', side_effect=[self.output('a'*64), self.output('b'*64)]):
            with self.assertRaises(ValueError): signing.verify_signers([Path('arm64.apk'),Path('x86.apk')], 'apksigner')
    def test_rejects_invalid_apk_verification(self):
        with patch.object(signing.subprocess, 'run', side_effect=subprocess.CalledProcessError(1, [])):
            with self.assertRaises(subprocess.CalledProcessError): signing.verify_signers([Path('bad.apk')], 'apksigner')

if __name__ == '__main__': unittest.main()
