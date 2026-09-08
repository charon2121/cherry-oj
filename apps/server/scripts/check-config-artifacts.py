#!/usr/bin/env python3
"""Inspect final Spring Boot artifacts, including a dirty developer build, without reading secrets."""
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
services = ("gateway", "user", "problem", "submission", "judging")
for name in services:
    target = root / f"{name}-service" / "target"
    jars = list(target.glob("*.jar")) + list(target.glob("*.jar.original"))
    if not jars:
        raise SystemExit(f"Missing artifact for {name}; run Maven package first")
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
            leaked = [entry for entry in names if Path(entry).name.startswith("application-local.")]
            if leaked:
                raise SystemExit(f"Private configuration found in {jar.name}")
            if not any(entry.endswith("application.yaml") for entry in names):
                raise SystemExit(f"Base configuration missing from {jar.name}")
    print(f"PASS {name}: base configuration present; private files absent from {len(jars)} artifacts")
