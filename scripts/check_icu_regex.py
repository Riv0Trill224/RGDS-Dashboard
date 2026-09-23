"""Compile the actual Java process regex with native ICU (Android engine family).
Requires the system libicui18n, available on GitHub Ubuntu runners.
This supplements JVM tests; it is not a physical Android test.
"""
import ctypes
import ctypes.util
import json
import re
from pathlib import Path

name = ctypes.util.find_library("icui18n")
if not name:
    raise RuntimeError("Native ICU required for Android regex regression")
lib = ctypes.CDLL(name)
version = re.search(r"\.so\.(\d+)", name)
suffix = "_" + version[1] if version else ""
open_regex = getattr(lib, "uregex_open" + suffix)
open_regex.argtypes = [ctypes.c_void_p, ctypes.c_int32, ctypes.c_uint32,
                       ctypes.c_void_p, ctypes.POINTER(ctypes.c_int32)]
open_regex.restype = ctypes.c_void_p
close_regex = getattr(lib, "uregex_close" + suffix)
close_regex.argtypes = [ctypes.c_void_p]

def compile_status(pattern):
    encoded = pattern.encode("utf-16-le")
    buffer = ctypes.create_string_buffer(encoded)
    status = ctypes.c_int32(0)
    handle = open_regex(buffer, len(encoded) // 2, 0, None, ctypes.byref(status))
    if handle:
        close_regex(handle)
    return status.value

source = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/rgds/dashboard/DiagnosticAnalysis.java").read_text()
line = next(line for line in source.splitlines() if "Pattern process =" in line)
literals = re.findall(r'"(?:\\.|[^"\\])*"', line)
assert len(literals) == 2, "Update source extraction when process pattern changes"
prefix, tail = map(json.loads, literals)
for package in ["com.mojang.minecraftpe", "org.example.game"]:
    pattern = prefix + r"\Q" + package + r"\E" + tail
    assert compile_status(pattern) <= 0, f"ICU rejects production pattern: {pattern}"
# Ensure this engine catches the exact reported defect, unlike desktop Java.
old = r"\b(\d+):\Qcom.mojang.minecraftpe\E(?:/|\s|})"
assert compile_status(old) > 0, "Regression control unexpectedly accepted"
print("ICU: production process patterns pass; reported old pattern rejected")
