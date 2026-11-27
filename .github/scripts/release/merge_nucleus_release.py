"""Validate Nucleus installers and combine their updater manifests."""

import hashlib
import re
import shutil
import sys
from base64 import b64encode
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from release_support import cli, empty_output, require

FORMATS = {
    "linux-x64": ("deb", "rpm", "AppImage"),
    "windows-x64": ("exe",),
    "macos-x64": ("dmg", "zip"),
    "macos-arm64": ("dmg", "zip"),
}
VERSION = re.compile(r"[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+(\.[0-9]+)*)?")
FIELDS = {"url", "sha512", "size", "blockMapSize"}


@dataclass(frozen=True)
class UpdateFile:
    name: str
    digest: str
    size: int
    block_map_size: int | None


def installer_name(version, platform, format_name):
    suffix = "-nsis" if format_name == "exe" and platform == "windows-x64" else ""
    return f"azkar-{version}-{platform}{suffix}.{format_name}"


def manifest_name(channel, platform):
    suffix = {"linux": "-linux", "windows": "", "macos": "-mac"}[platform.split("-")[0]]
    return f"{channel}{suffix}.yml"


def scalar(value):
    value = value.strip()
    if len(value) > 1 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def read_manifest(path):
    """Read the scalar fields and flat file list emitted by Nucleus."""
    version = None
    dates = []
    entries = []
    in_files = False
    seen_files = False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line == "files:":
            require(not seen_files, f"Duplicate files section in {path}")
            seen_files = in_files = True
            continue
        if in_files and line.startswith(("  - ", "    ")) and line.strip():
            if line.startswith("  - "):
                entries.append({})
            field, separator, value = line[4:].partition(":")
            require(entries and separator and field in FIELDS and field not in entries[-1],
                    f"Invalid update field in {path}: {line}")
            entries[-1][field] = scalar(value)
            continue
        if line.strip():
            in_files = False
        if line.startswith("version:"):
            require(version is None, f"Duplicate version in {path}")
            version = scalar(line.split(":", 1)[1])
        elif line.startswith("releaseDate:"):
            dates.append(scalar(line.split(":", 1)[1]))
    require(version and entries and len(dates) <= 1, f"Invalid update manifest: {path}")
    date = dates[0] if dates else None
    if date is not None:
        require(re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{1,9})?Z", date),
                f"Invalid releaseDate in {path}")
        datetime.fromisoformat(date)
    files = []
    for entry in entries:
        require({"url", "sha512", "size"} <= entry.keys(), f"Incomplete update entry in {path}")
        numbers = [entry[key] for key in ("size", "blockMapSize") if key in entry]
        require(all(re.fullmatch(r"[+-]?[0-9]+", number) for number in numbers),
                f"Invalid size in {path}")
        files.append(UpdateFile(
            entry["url"], entry["sha512"], int(entry["size"]),
            int(entry["blockMapSize"]) if "blockMapSize" in entry else None,
        ))
    return version, files, date


def sha(path, algorithm):
    with path.open("rb") as file:
        return hashlib.file_digest(file, algorithm)


def merge_release(source, output, version):
    require(VERSION.fullmatch(version), f"Invalid release version: {version}")
    empty_output(output)
    require(source.is_dir(), f"Missing release inputs: {source}")
    channel = "beta" if "-beta." in version else "latest"
    native_version = version.split("-")[0]
    major, minor, patch = native_version.split(".")
    mac_version = f"{int(major) + 1}.{minor}.{patch}"
    expected = {
        installer_name(version, platform, format_name): platform
        for platform, formats in FORMATS.items() for format_name in formats
    }
    packages = {}
    block_maps = {}
    metadata = []
    for path in sorted(source.rglob("*")):
        require(not path.is_symlink(), f"Symbolic links are not valid release inputs: {path}")
        if path.is_dir():
            continue
        require(path.is_file(), f"Invalid release input: {path}")
        relative = path.relative_to(source)
        platform = relative.parts[0]
        require(platform in FORMATS, f"Unexpected platform: {path}")
        name = path.name
        if relative == Path(platform, "SHA256SUMS"):
            continue
        if name == manifest_name(channel, platform):
            metadata.append((path, platform))
        elif expected.get(name) == platform and name not in packages:
            packages[name] = path
        elif name.endswith(".blockmap") and expected.get(name.removesuffix(".blockmap")) == platform and name not in block_maps:
            block_maps[name] = path
        else:
            raise ValueError(f"Unexpected or duplicate release input: {path}")
    require(packages.keys() == expected.keys(), f"Missing installers: {sorted(expected.keys() - packages.keys())}")

    seen = set()
    manifests = {}
    dates = {}
    for path, platform in metadata:
        manifest = path.name
        versions = {version}
        if platform != "linux-x64":
            versions.add(native_version)
        if platform.startswith("macos-"):
            versions.add(mac_version)
        manifest_version, files, release_date = read_manifest(path)
        require(manifest_version in versions, f"Unexpected version {manifest_version} in {path}")
        for file in files:
            require(expected.get(file.name) == platform, f"Unknown or unsafe artifact URL in {path}: {file.name}")
            require(file.name not in seen, f"Duplicate update entry: {file.name}")
            artifact = packages[file.name]
            require(artifact.stat().st_size == file.size > 0, f"Size mismatch: {file.name}")
            require(b64encode(sha(artifact, "sha512").digest()).decode() == file.digest, f"SHA-512 mismatch: {file.name}")
            require(file.block_map_size is None or 1 <= file.block_map_size <= file.size,
                    f"Invalid blockMapSize: {file.name}")
            seen.add(file.name)
        manifests.setdefault(manifest, []).extend(files)
        if release_date:
            dates.setdefault(manifest, []).append(release_date)
    require(seen == packages.keys(), f"Missing update metadata for: {sorted(packages.keys() - seen)}")

    output.mkdir(parents=True, exist_ok=True)
    for name, path in (packages | block_maps).items():
        shutil.copy2(path, output / name)
    for name, files in manifests.items():
        lines = [f"version: {version}", "files:"]
        for file in sorted(files, key=lambda entry: entry.name):
            lines.extend((f"  - url: {file.name}", f"    sha512: {file.digest}", f"    size: {file.size}"))
            if file.block_map_size is not None:
                lines.append(f"    blockMapSize: {file.block_map_size}")
        if name in dates:
            lines.append(f"releaseDate: '{max(dates[name])}'")
        (output / name).write_text("\n".join(lines) + "\n", encoding="utf-8")
    hashes = "".join(f"{sha(path, 'sha256').hexdigest()}  {path.name}\n" for path in sorted(output.iterdir()))
    (output / "SHA256SUMS").write_text(hashes, encoding="utf-8")
    print(f"Validated {len(packages)} installers and {len(manifests)} update manifests in {output}")


def main():
    require(len(sys.argv) == 4, f"Usage: {sys.argv[0]} SOURCE_DIR OUTPUT_DIR RELEASE_VERSION")
    merge_release(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3])


if __name__ == "__main__":
    cli(main)
