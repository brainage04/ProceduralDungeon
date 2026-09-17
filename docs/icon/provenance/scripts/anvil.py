"""Minimal read-only Minecraft Anvil region reader (pure stdlib).

Supports the modern (1.18+) chunk layout: `sections` -> `block_states` with
`palette` + packed `data` longs, plus the `Name`/`Properties` palette entries.
Used to measure dungeon geometry straight from saved region files.
"""

from __future__ import annotations

import gzip
import io
import struct
from dataclasses import dataclass

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class _Reader:
    __slots__ = ("buf", "pos")

    def __init__(self, buf: bytes):
        self.buf = buf
        self.pos = 0

    def u(self, n: int) -> bytes:
        out = self.buf[self.pos:self.pos + n]
        if len(out) != n:
            raise EOFError("truncated NBT")
        self.pos += n
        return out

    def i(self, fmt: str):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.u(size))[0]

    def string(self) -> str:
        length = self.i(">H")
        return self.u(length).decode("utf-8", "replace")


def _payload(r: _Reader, tag: int):
    if tag == TAG_BYTE:
        return r.i(">b")
    if tag == TAG_SHORT:
        return r.i(">h")
    if tag == TAG_INT:
        return r.i(">i")
    if tag == TAG_LONG:
        return r.i(">q")
    if tag == TAG_FLOAT:
        return r.i(">f")
    if tag == TAG_DOUBLE:
        return r.i(">d")
    if tag == TAG_BYTE_ARRAY:
        return r.u(r.i(">i"))
    if tag == TAG_STRING:
        return r.string()
    if tag == TAG_LIST:
        item = r.i(">b")
        count = r.i(">i")
        return [_payload(r, item) for _ in range(count)]
    if tag == TAG_COMPOUND:
        out = {}
        while True:
            sub = r.i(">b")
            if sub == TAG_END:
                return out
            name = r.string()
            out[name] = _payload(r, sub)
    if tag == TAG_INT_ARRAY:
        return [_payload(r, TAG_INT) for _ in range(r.i(">i"))]
    if tag == TAG_LONG_ARRAY:
        return [_payload(r, TAG_LONG) for _ in range(r.i(">i"))]
    raise ValueError(f"unsupported NBT tag {tag}")


def parse_nbt(data: bytes) -> dict:
    r = _Reader(data)
    tag = r.i(">b")
    if tag != TAG_COMPOUND:
        raise ValueError("root tag is not a compound")
    r.string()
    return _payload(r, TAG_COMPOUND)


def read_nbt_file(path) -> dict:
    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    elif raw[:4] == b"\x00\x00\x00\x00":
        # external .mcc style header, not used here
        raise ValueError("region-external chunk")
    return parse_nbt(raw)


def _long_array_to_blocks(longs, bits: int, count: int):
    """Unpack the 1.16+ contiguous (non-spanning) bit packing."""
    if bits == 0:
        return None
    mask = (1 << bits) - 1
    per_long = 64 // bits
    out = []
    for value in longs:
        value &= 0xFFFFFFFFFFFFFFFF
        for i in range(per_long):
            out.append((value >> (i * bits)) & mask)
            if len(out) == count:
                return out
    return out


def _palette_key(entry: dict) -> str:
    name = entry.get("Name", "minecraft:air")
    props = entry.get("Properties")
    if not props:
        return name
    inner = ",".join(f"{k}={props[k]}" for k in sorted(props))
    return f"{name}[{inner}]"


@dataclass
class Chunk:
    x: int
    z: int
    sections: dict  # section-y -> list of (index, block key) for non-air blocks (16^3 order)
    palette_keys: set

    def blocks(self):
        for sy, entries in self.sections.items():
            for index, key in entries:
                bx = index & 15
                bz = (index >> 4) & 15
                by = index >> 8
                yield (self.x * 16 + bx, sy * 16 + by, self.z * 16 + bz, key)


def iter_chunk_blocks(chunk_nbt: dict):
    """Yield (x, y, z, block_key) for all non-air blocks of one chunk NBT."""
    cx = chunk_nbt.get("xPos", 0)
    cz = chunk_nbt.get("zPos", 0)
    for section in chunk_nbt.get("sections", []):
        sy = section.get("Y")
        if sy is None:
            continue
        block_states = section.get("block_states")
        if not block_states:
            continue
        palette = block_states.get("palette") or []
        keys = [_palette_key(entry) for entry in palette]
        data = block_states.get("data")
        if data is None:
            # single-block section
            if keys and keys[0] != "minecraft:air":
                for index in range(4096):
                    yield (cx * 16 + (index & 15), sy * 16 + (index >> 8), cz * 16 + ((index >> 4) & 15), keys[0])
            continue
        bits = max(4, (len(keys) - 1).bit_length())
        indices = _long_array_to_blocks(data, bits, 4096)
        for index, entry in enumerate(indices):
            key = keys[entry]
            if key == "minecraft:air" or key == "minecraft:cave_air" or key == "minecraft:void_air":
                continue
            yield (cx * 16 + (index & 15), sy * 16 + (index >> 8), cz * 16 + ((index >> 4) & 15), key)


def region_path(region_dir, rx: int, rz: int):
    from pathlib import Path

    return Path(region_dir) / f"r.{rx}.{rz}.mca"


def iter_region_chunks(path):
    """Yield (chunk_x, chunk_z, chunk_nbt) from a .mca file."""
    data = open(path, "rb").read()
    if len(data) < 8192:
        return
    for i in range(1024):
        offset = struct.unpack(">I", b"\x00" + data[i * 4:i * 4 + 3])[0]
        if offset == 0:
            continue
        size = struct.unpack(">I", data[i * 4 + 4096:i * 4 + 4100])[0]
        if size == 0:
            continue
        start = offset * 4096
        payload = data[start:start + size]
        if len(payload) < 5:
            continue
        length = struct.unpack(">I", payload[:4])[0]
        compression = payload[4]
        body = payload[5:4 + length]
        try:
            if compression == 1:
                body = gzip.decompress(body)
            elif compression == 2:
                body = io.BytesIO(body)
                import zlib

                body = zlib.decompress(body.read())
            elif compression == 3:
                body = body
            elif compression == 4:
                import lz4.block  # type: ignore  # pragma: no cover

                body = lz4.block.decompress(body)
            else:
                continue
            nbt = parse_nbt(body)
        except Exception:
            continue
        cx = nbt.get("xPos", (i % 32) + 0)
        cz = nbt.get("zPos", (i // 32) + 0)
        yield cx, cz, nbt
