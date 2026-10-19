#!/usr/bin/env python3
"""Copy the client's English magic book into src/main/resources/bot/magic-descriptions.properties.

The jev bot reads each magic's card text from that file (MagicDescriptions). The client's
MagicBook_en string table is the source; run this after its text changes.

usage: scripts/sync-magic-descriptions.py <client checkout>
"""
import pathlib
import re
import sys

import yaml

client = pathlib.Path(sys.argv[1])
tables = client / "Assets/Localization"
shared = (tables / "MagicBook Shared Data.asset").read_text(encoding="utf-8")
keys = dict(re.findall(r"m_Id: (\d+)\n\s+m_Key: (\S+)", shared))
english = (tables / "MagicBook_en.asset").read_text(encoding="utf-8")
rows = yaml.safe_load(english[english.index("MonoBehaviour:"):])["MonoBehaviour"]["m_TableData"]

lines = []
for row in rows:
    key = keys.get(str(row["m_Id"]))
    if key and "." not in key and row["m_Localized"]:
        lines.append(f"{key}={' '.join(row['m_Localized'].split())}")

out = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/bot/magic-descriptions.properties"
out.write_text(
    "# What each magic does, copied from the client's English magic book\n"
    "# (client Assets/Localization/MagicBook_en.asset, keyed by magic name) by\n"
    "# scripts/sync-magic-descriptions.py. Edit the client table, not this file.\n"
    + "\n".join(sorted(lines)) + "\n",
    encoding="utf-8")
print(f"{len(lines)} descriptions -> {out}")
