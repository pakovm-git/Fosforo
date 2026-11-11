"""Offline checks for the exact pinned frame conversion (standard library only)."""
import binascii
import hashlib
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

from convert import convert, plane

ROOT = Path(__file__).resolve().parent


class FrameConversionTest(unittest.TestCase):
    def test_pinned_pixels_png_and_notice(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'frame.png'
            result = convert(ROOT / 'crt_frame.h', output)
            self.assertEqual(result['unfiltered_la_pixels_sha256'],
                             '1440983cd7771b874fe55762b1be6a589ae2785177facb22960cf3a8d6586f1c')
            png = output.read_bytes()
            self.assertEqual(png[:8], b'\x89PNG\r\n\x1a\n')
            offset, compressed, copyright_text = 8, b'', b''
            while offset < len(png):
                size = struct.unpack('>I', png[offset:offset + 4])[0]
                kind = png[offset + 4:offset + 8]
                data = png[offset + 8:offset + 8 + size]
                crc = struct.unpack('>I', png[offset + 8 + size:offset + 12 + size])[0]
                self.assertEqual(crc, binascii.crc32(kind + data) & 0xffffffff)
                if kind == b'IDAT':
                    compressed += data
                if kind == b'tEXt' and data.startswith(b'Copyright\0'):
                    copyright_text = data
                offset += size + 12
            rows = zlib.decompress(compressed)
            self.assertEqual(len(rows), 1024 * (2048 + 1))
            self.assertTrue(all(rows[i] == 0 for i in range(0, len(rows), 2049)))
            pixels = b''.join(rows[i + 1:i + 2049] for i in range(0, len(rows), 2049))
            self.assertEqual(hashlib.sha256(pixels).hexdigest(), result['unfiltered_la_pixels_sha256'])
            self.assertIn(b'Mattias Gustavsson', copyright_text)
            self.assertIn(b'MIT OR Unlicense', copyright_text)

    def test_changed_source_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            source = Path(temporary) / 'changed.h'
            source.write_bytes((ROOT / 'crt_frame.h').read_bytes() + b'\n')
            with self.assertRaisesRegex(ValueError, 'Source hash differs'):
                convert(source, Path(temporary) / 'frame.png')

    def test_broken_rle_rejected(self):
        with self.assertRaisesRegex(ValueError, 'does not fill'):
            plane('frame_color[] = {0x0000000000000002ULL, 0x0000000000000501ULL};', 'frame_color')
        with self.assertRaisesRegex(ValueError, 'Invalid original RLE'):
            plane('frame_color[] = {0x0000000000000003ULL, 0x0000000000000501ULL};', 'frame_color')


if __name__ == '__main__':
    unittest.main()
