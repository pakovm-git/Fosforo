#!/usr/bin/env python3
"""Convert the pinned Mattias Gustavsson CRT frame to PNG using only Python's standard library."""
import argparse
import binascii
import hashlib
import json
from pathlib import Path
import re
import struct
import zlib

WIDTH = HEIGHT = 1024
PIXELS = WIDTH * HEIGHT
SOURCE_SHA256 = 'cd4cd08fa6c4af1965e312a7a789d87905f029fc0f4690ecf54b56f20ed1fd16'


def plane(source, name):
    match = re.search(r'\b' + re.escape(name) + r'\[\]\s*=\s*\{(.*?)\};', source, re.S)
    if match is None:
        raise ValueError('Missing original frame array: ' + name)
    words = [int(value, 16) for value in re.findall(r'0x([0-9a-fA-F]+)ULL', match[1])]
    if not words:
        raise ValueError('Empty frame array: ' + name)
    byte_count = words[0]
    data = b''.join(struct.pack('<Q', value) for value in words[1:])
    if byte_count > len(data) or byte_count % 2:
        raise ValueError('Invalid original RLE byte count: ' + name)
    result = bytearray()
    for offset in range(0, byte_count, 2):
        count, value = data[offset:offset + 2]
        # The author's C implementation exits as soon as the 1024 x 1024 frame is filled.
        result.extend(bytes([value]) * min(count, PIXELS - len(result)))
        if len(result) == PIXELS:
            return bytes(result)
    raise ValueError('Original RLE array does not fill the frame: ' + name)


def chunk(kind, data):
    return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', binascii.crc32(kind + data) & 0xffffffff)


def convert(source_path, output_path):
    original = source_path.read_bytes()
    if hashlib.sha256(original).hexdigest() != SOURCE_SHA256:
        raise ValueError('Source hash differs from the checked licensed source')
    source = original.decode('utf-8')
    color, alpha = plane(source, 'frame_color'), plane(source, 'frame_alpha')
    pixels = bytes(component for pair in zip(color, alpha) for component in pair)
    stride = WIDTH * 2
    rows = b''.join(b'\0' + pixels[offset:offset + stride] for offset in range(0, len(pixels), stride))
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', WIDTH, HEIGHT, 8, 4, 0, 0, 0))
    png += chunk(b'tEXt', b'Copyright\0Copyright (c) 2016 Mattias Gustavsson; MIT OR Unlicense; see LICENSE-Mattias-Gustavsson.txt')
    png += chunk(b'tEXt', b'Source\0mattiasgustavsson/crtview 4fe66d98bfbb2d343239eb435cc749589b75a907 source/crt_frame.h')
    png += chunk(b'IDAT', zlib.compress(rows, 9))
    png += chunk(b'IEND', b'')
    output_path.write_bytes(png)
    return {'width': WIDTH, 'height': HEIGHT, 'pixel_format': '8-bit grayscale plus alpha',
            'source_sha256': SOURCE_SHA256, 'png_sha256': hashlib.sha256(png).hexdigest(),
            'unfiltered_la_pixels_sha256': hashlib.sha256(pixels).hexdigest()}


if __name__ == '__main__':
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, default=root / 'crt_frame.h')
    parser.add_argument('--output', type=Path, default=root / 'crtframe.png')
    args = parser.parse_args()
    print(json.dumps(convert(args.source, args.output), indent=2))
