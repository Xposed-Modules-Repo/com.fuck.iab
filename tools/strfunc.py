#!/usr/bin/env python3
"""Generate an obfuscated Kotlin string function from a plain string.

Usage:
    python tools/strfunc.py "some literal string"
    python tools/strfunc.py "string one" "string two"     # one function per argument
    python tools/strfunc.py --name myFunc "some string"   # force the function name
    python tools/strfunc.py --key 42 "some string"        # fixed xor key (default: random)

Prints a Kotlin function in the same style as str.kt for every input string.
The function name is derived from the string itself (word-cased camel/pascal,
kotlin keywords and invalid identifiers are made safe, very long or purely
symbolic strings get a short deterministic hash suffix/prefix).

This tool ONLY turns a string into a function. It never reads, writes or
searches source files - finding and replacing the literals is up to you.
"""

import argparse
import hashlib
import re
import secrets
import sys

# Kotlin hard keywords - a function cannot be named exactly like one.
KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
    "if", "in", "interface", "is", "null", "object", "package", "return",
    "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
    "var", "when", "while", "catch", "finally",
}

# Readable names for strings that carry no words at all.
SPECIALS = {
    " ": "space",
    "\n": "newline",
    "\r": "carriageReturn",
    "\t": "tab",
    "-": "dash",
    ".": "dot",
    "?": "question",
}

MAX_NAME = 60


def derive_name(s: str) -> str:
    """Derive a readable, valid Kotlin function name from a string."""
    if s in SPECIALS:
        return SPECIALS[s]

    toks = [t.lower() for t in re.split(r"[^0-9A-Za-z]+", s) if t]
    if not toks:
        # no words at all ("// ", " · ", ...): fall back to a stable hash
        return "s" + hashlib.md5(s.encode("utf-8")).hexdigest()[:6]

    pascal = s[:1].isalpha() and s[:1].isupper()
    parts = [t.capitalize() if (pascal or i > 0) else t for i, t in enumerate(toks)]
    name = "".join(parts)

    if name[:1].isdigit():
        name = "_" + name
    if len(name) > MAX_NAME:
        name = name[:MAX_NAME].rstrip("_") + hashlib.md5(s.encode("utf-8")).hexdigest()[:6]
    if name in KEYWORDS:
        name += "_"

    # keep edge underscores that were part of the original string ("disabled_", "_scripts")
    lead = len(s) - len(s.lstrip("_"))
    trail = len(s) - len(s.rstrip("_"))
    if lead:
        name = "_" * min(lead, 2) + name
    if trail:
        name = name + "_" * min(trail, 2)
    return name


def utf16_units(s: str) -> list:
    """Kotlin iterates UTF-16 code units, so xor them the same way."""
    return list(s.encode("utf-16-be")[i] << 8 | s.encode("utf-16-be")[i + 1]
                for i in range(0, len(s.encode("utf-16-be")), 2))


def generate(s: str, name: str = None, key: int = None) -> str:
    if key is None:
        key = secrets.randbelow(255) + 1  # 1..255, 0 would store the chars raw
    units = utf16_units(s)
    encoded = ", ".join(str(u ^ key) for u in units)
    fname = name or derive_name(s)
    return (
        f"fun {fname}(): String {{\n"
        f"    val sb = StringBuilder()\n"
        f"    val arr = intArrayOf({encoded})\n"
        f"    for (num in arr) {{\n"
        f"        sb.append((num.xor({key})).toChar())\n"
        f"    }}\n"
        f"    return sb.toString()\n"
        f"}}"
    )


def main(argv) -> int:
    ap = argparse.ArgumentParser(
        description="Turn a string into an obfuscated Kotlin function (str.kt style).")
    ap.add_argument("strings", nargs="+", help="the string(s) to turn into function(s)")
    ap.add_argument("--name", help="force the generated function name (single string only)")
    ap.add_argument("--key", type=int, choices=range(0, 256), metavar="0..255",
                    help="fixed xor key instead of a random one")
    args = ap.parse_args(argv)

    if args.name and len(args.strings) > 1:
        ap.error("--name only works with a single string")

    print("\n".join(generate(s, args.name, args.key) for s in args.strings))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
