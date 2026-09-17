"""Minimal Minecraft NBT reader/writer (pure stdlib).

Written for this task (captures-recreate2) so the world toolkit does not
depend on any other agent's file.  Handles the tag set that appears in
level.dat and in 1.18+ Anvil chunk data.
"""

from __future__ import annotations

import gzip
import struct

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


class Tag:
    """A typed NBT value (needed to round-trip ints/ longs without widening)."""

    __slots__ = ("id", "value")

    def __init__(self, id_: int, value):
        self.id = id_
        self.value = value

    def __repr__(self):
        return f"Tag({self.id},{self.value!r})"


def _read_payload(buf, pos, tag):
    if tag == TAG_BYTE:
        return buf[pos], pos + 1
    if tag == TAG_SHORT:
        return struct.unpack_from(">h", buf, pos)[0], pos + 2
    if tag == TAG_INT:
        return struct.unpack_from(">i", buf, pos)[0], pos + 4
    if tag == TAG_LONG:
        return struct.unpack_from(">q", buf, pos)[0], pos + 8
    if tag == TAG_FLOAT:
        return struct.unpack_from(">f", buf, pos)[0], pos + 4
    if tag == TAG_DOUBLE:
        return struct.unpack_from(">d", buf, pos)[0], pos + 8
    if tag == TAG_BYTE_ARRAY:
        n = struct.unpack_from(">i", buf, pos)[0]
        return list(buf[pos + 4:pos + 4 + n]), pos + 4 + n
    if tag == TAG_STRING:
        n = struct.unpack_from(">H", buf, pos)[0]
        return buf[pos + 2:pos + 2 + n].decode("utf-8", "replace"), pos + 2 + n
    if tag == TAG_LIST:
        item = buf[pos]
        n = struct.unpack_from(">i", buf, pos + 1)[0]
        pos += 5
        out = []
        for _ in range(n):
            v, pos = _read_payload(buf, pos, item)
            out.append(v)
        return Tag(TAG_LIST, (item, out)), pos
    if tag == TAG_COMPOUND:
        out = {}
        while True:
            sub = buf[pos]
            pos += 1
            if sub == TAG_END:
                return out, pos
            n = struct.unpack_from(">H", buf, pos)[0]
            name = buf[pos + 2:pos + 2 + n].decode("utf-8", "replace")
            pos += 2 + n
            v, pos = _read_payload(buf, pos, sub)
            out[name] = v if isinstance(v, Tag) else Tag(sub, v)
    if tag == TAG_INT_ARRAY:
        n = struct.unpack_from(">i", buf, pos)[0]
        pos += 4
        out = list(struct.unpack_from(">%di" % n, buf, pos))
        return out, pos + 4 * n
    if tag == TAG_LONG_ARRAY:
        n = struct.unpack_from(">i", buf, pos)[0]
        pos += 4
        out = list(struct.unpack_from(">%dq" % n, buf, pos))
        return out, pos + 4 * n
    raise ValueError(f"unsupported NBT tag {tag}")


def parse(data: bytes):
    pos = 0
    tag = data[pos]
    pos += 1
    n = struct.unpack_from(">H", data, pos)[0]
    pos += 2 + n
    root, pos = _read_payload(data, pos, tag)
    return Tag(tag, root)


def load(path):
    raw = open(path, "rb").read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return parse(raw)


def _write_payload(name: str, tag: Tag) -> bytes:
    out = bytearray()
    out.append(tag.id)
    if tag.id == TAG_END:
        return bytes(out)
    nb = name.encode("utf-8")
    out += struct.pack(">H", len(nb)) + nb
    out += _body(tag)
    return bytes(out)


def _body(tag: Tag) -> bytes:
    v = tag.value
    if tag.id == TAG_BYTE:
        return struct.pack(">b", v)
    if tag.id == TAG_SHORT:
        return struct.pack(">h", v)
    if tag.id == TAG_INT:
        return struct.pack(">i", v)
    if tag.id == TAG_LONG:
        return struct.pack(">q", v)
    if tag.id == TAG_FLOAT:
        return struct.pack(">f", v)
    if tag.id == TAG_DOUBLE:
        return struct.pack(">d", v)
    if tag.id == TAG_BYTE_ARRAY:
        return struct.pack(">i", len(v)) + bytes(v)
    if tag.id == TAG_STRING:
        b = v.encode("utf-8")
        return struct.pack(">H", len(b)) + b
    if tag.id == TAG_LIST:
        item, items = v
        out = bytearray()
        out.append(item)
        out += struct.pack(">i", len(items))
        for it in items:
            if item == TAG_COMPOUND:
                for k, sub in it.items():
                    out += _write_payload(k, sub)
                out.append(TAG_END)
            else:
                out += _body(Tag(item, it))
        return bytes(out)
    if tag.id == TAG_COMPOUND:
        out = bytearray()
        for k, sub in v.items():
            out += _write_payload(k, sub)
        out.append(TAG_END)
        return bytes(out)
    if tag.id == TAG_INT_ARRAY:
        return struct.pack(">i", len(v)) + struct.pack(">%di" % len(v), *v)
    if tag.id == TAG_LONG_ARRAY:
        return struct.pack(">i", len(v)) + struct.pack(">%dq" % len(v), *v)
    raise ValueError(f"cannot write tag {tag.id}")


def dump(path, root: Tag, root_name: str = ""):
    payload = _write_payload(root_name, root)
    with open(path, "wb") as fh:
        with gzip.GzipFile(fileobj=fh, mode="wb", mtime=0) as gz:
            gz.write(payload)


# ---- convenience accessors -------------------------------------------------

def unwrap(node):
    """Drop Tag wrappers, returning plain python values (lists of dicts too)."""
    if isinstance(node, dict):
        return {k: unwrap(v) for k, v in node.items()}
    if isinstance(node, list):
        return [unwrap(v) for v in node]
    if isinstance(node, Tag):
        if node.id == TAG_COMPOUND:
            return unwrap(node.value)
        if node.id == TAG_LIST:
            item, items = node.value
            if item == TAG_COMPOUND:
                return [unwrap(dict(i)) for i in items]
            if item == TAG_LIST:
                return [unwrap(i) for i in items]
            return list(items)
        return node.value
    return node
