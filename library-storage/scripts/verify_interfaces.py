#!/usr/bin/env python3
"""Verify that all interfaces in common-interfaces have corresponding implementations."""

import os, re, sys

INTERFACES_DIR = "common-interfaces/src/main/kotlin/com/watermelon/common/repository"
CONTROLLER_DIR = "common-interfaces/src/main/kotlin/com/watermelon/common/controller"
IMPL_DIRS = ["library-storage", "subtitle-engine", "playback-engine"]

def find_interfaces():
    interfaces = {}
    for d in [INTERFACES_DIR, CONTROLLER_DIR]:
        for f in os.listdir(d):
            if f.endswith(".kt"):
                name = f.replace(".kt", "")
                interfaces[name] = os.path.join(d, f)
    return interfaces

def find_implementations():
    impls = set()
    for d in IMPL_DIRS:
        for root, _, files in os.walk(d):
            for f in files:
                if f.endswith("Impl.kt") or f.endswith("ControllerImpl.kt"):
                    impls.add(f.replace(".kt", "").replace("Impl", ""))
    return impls

def main():
    interfaces = find_interfaces()
    impls = find_implementations()
    missing = set(interfaces.keys()) - impls
    if missing:
        print(f"MISSING IMPLEMENTATIONS: {missing}")
        sys.exit(1)
    print("All interfaces have implementations.")
    sys.exit(0)

if __name__ == "__main__":
    main()
