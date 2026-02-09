#!/usr/bin/env python3
"""
Local version update for this repo. Use this when the GitHub "Update Versions"
workflow has "nothing to commit": aepsdk-commons only matches x.y.z (no prerelease),
so it never changes 3.0.0-beta.1 -> 3.0.0. This script matches full version
(including -beta.1, -rc.1, etc.) and replaces with the new version.

Run from repo root:
  python3 scripts/local-version-update.py 3.0.0

Then: git add code/gradle.properties code/contentanalytics/.../ContentAnalyticsConstants.kt
      git commit -m "chore: bump version to 3.0.0"
      git push origin update-version-3.0.0
Python 3.6+ compatible.
"""
import re
import sys
import os

def update_file(path, pattern_prefix, version_regex, new_version):
    """Replace first match of (prefix)(version_regex) with prefix + new_version. Same logic as aepsdk-commons versions.py."""
    with open(path, 'r') as f:
        lines = f.readlines()
    pattern = re.compile(r'(' + pattern_prefix + r')(' + version_regex + r')')
    new_lines = []
    updated = False
    for line in lines:
        match = pattern.match(line)
        if match and not updated:
            new_line = pattern.sub(r'\g<1>' + new_version, line)
            new_lines.append(new_line)
            updated = True
            print(f"Updated {path}: {match.group(2)!r} -> {new_version!r}")
        else:
            new_lines.append(line)
    if updated:
        with open(path, 'w') as f:
            f.writelines(new_lines)
        return True
    return False

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 scripts/local-version-update.py <new_version>")
        print("Example: python3 scripts/local-version-update.py 3.0.0")
        sys.exit(1)
    new_version = sys.argv[1]
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(root)

    # Match full version including optional prerelease (-beta.1, -rc.1, etc.) so we replace the whole thing.
    # aepsdk-commons only uses x.y.z so it never changes "3.0.0-beta.1" -> "3.0.0"; this script does.
    version_regex = r'[0-9]+\.[0-9]+\.[0-9]+(?:\-[a-zA-Z0-9.]+)?'

    updated = False
    # gradle.properties: moduleVersion=3.0.0-beta.1 -> moduleVersion=3.0.0
    gp = os.path.join(root, 'code', 'gradle.properties')
    if os.path.isfile(gp):
        updated |= update_file(gp, r'^[\s\S]*moduleVersion\s*=\s*', version_regex, new_version)

    # ContentAnalyticsConstants.kt: const val VERSION = "3.0.0-beta.1" -> "3.0.0"
    kt = os.path.join(root, 'code', 'contentanalytics', 'src', 'main', 'kotlin', 'com', 'adobe', 'marketing', 'mobile', 'contentanalytics', 'ContentAnalyticsConstants.kt')
    if os.path.isfile(kt):
        updated |= update_file(kt, r'^[\s\S]*const val VERSION\s*=\s*"', version_regex, new_version)

    if updated:
        print("Done. Run: git diff")
    else:
        print("No files were updated.")
        sys.exit(1)

if __name__ == '__main__':
    main()
