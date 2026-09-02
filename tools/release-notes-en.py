#!/usr/bin/env python3
"""One-shot maintenance: rewrite the titles and notes of already published
GitHub releases in English (the project went international).

Bodies are regenerated from the same templates release.yml and map-pack.yml
use, so old and new releases read identically. Everything is derived from
what the old body already states (APK file name, bbox, size, SHA-256) and
GitHub's own "Full Changelog" line is kept verbatim. Tags, assets and dates
are untouched — only title/notes metadata changes. Idempotent: releases that
are already in English are skipped.

Usage:
  python3 tools/release-notes-en.py               # edit via gh
  python3 tools/release-notes-en.py --dry-run     # print, change nothing
  python3 tools/release-notes-en.py --from-json releases.json --dry-run
"""

import json
import os
import re
import subprocess
import sys
import tempfile

APK = re.compile(
    r"sha256sum -c (norda-(\d+\.\d+\.\d+)-(\d+)-release(-imzasiz|-unsigned)?-[0-9a-f]+\.apk)\.sha256"
)
MAP_BBOX = re.compile(r"(?:Kapsam|Coverage): `([^`]+)` · zoom (\S+)")
MAP_SIZE = re.compile(r"(?:Boyut|Size): (\d+) (?:bayt|bytes)")
MAP_SHA = re.compile(r"SHA-256: `([0-9a-f]+)`")


def app_release(title, body):
    m = APK.search(body)
    if not m:
        return None
    apk, version, code, unsigned = m.group(1), m.group(2), m.group(3), m.group(4)
    lines = [f"Release **{version} ({code})**.", ""]
    if unsigned:
        lines += [
            "> **Warning:** this APK is unsigned and cannot be installed on a phone.",
            "> The signing secrets are not configured in the repository (see the header of release.yml).",
        ]
    else:
        lines.append("The APK is signed and can be installed directly on the phone.")
    lines += [
        "",
        "To verify the file after downloading:",
        "",
        "```",
        f"sha256sum -c {apk}.sha256",
        "```",
        "",
        "Scope and roadmap: docs/MVP.md · Changes: CHANGELOG.md",
    ]
    full = [l for l in body.splitlines() if l.startswith("**Full Changelog**")]
    if full:
        lines += ["", "", full[0]]
    return f"Norda {version}", "\n".join(lines) + "\n"


def map_release(tag, title, body):
    b, s, h = MAP_BBOX.search(body), MAP_SIZE.search(body), MAP_SHA.search(body)
    if not (b and s and h):
        return None
    pkg_id, vn = tag.split("/", 1)[1].rsplit("-v", 1)
    name = re.sub(r"^(Harita paketi|Map pack):\s*", "", title)
    name = re.sub(rf"\s+v{re.escape(vn)}$", "", name)
    lines = [
        f"Map pack **{name}** (`{pkg_id}`, v{vn}).",
        "",
        f"- Coverage: `{b.group(1)}` · zoom {b.group(2)}",
        f"- Size: {s.group(1)} bytes",
        f"- SHA-256: `{h.group(1)}`",
        "",
        "The app downloads this pack from the list on the Maps screen;",
        'for manual installation, download the file and use "Import package".',
    ]
    return f"Map pack: {name} v{vn}", "\n".join(lines) + "\n"


def rewrite(release):
    tag, title, body = release["tag_name"], release["name"] or "", release["body"] or ""
    if body.startswith("Release **") or body.startswith("Map pack **"):
        return None                               # already English
    if tag.startswith("v"):
        return app_release(title, body)
    if tag.startswith("maps/"):
        return map_release(tag, title, body)
    return None


def load_releases(path):
    if path:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    repo = os.environ.get("GITHUB_REPOSITORY") or subprocess.check_output(
        ["gh", "repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner"],
        text=True).strip()
    out = subprocess.check_output(
        ["gh", "api", f"repos/{repo}/releases?per_page=100"], text=True)
    return json.loads(out)


def main():
    dry = "--dry-run" in sys.argv
    src = None
    if "--from-json" in sys.argv:
        src = sys.argv[sys.argv.index("--from-json") + 1]
    changed = skipped = 0
    for rel in load_releases(src):
        result = rewrite(rel)
        if result is None:
            skipped += 1
            continue
        title, body = result
        changed += 1
        if dry:
            print(f"--- {rel['tag_name']} → {title}\n{body}")
            continue
        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False,
                                         encoding="utf-8") as f:
            f.write(body)
            notes = f.name
        subprocess.run(["gh", "release", "edit", rel["tag_name"],
                        "--title", title, "--notes-file", notes], check=True)
        os.unlink(notes)
        print(f"edited {rel['tag_name']} → {title}")
    print(f"{changed} release(s) rewritten, {skipped} skipped")


if __name__ == "__main__":
    main()
