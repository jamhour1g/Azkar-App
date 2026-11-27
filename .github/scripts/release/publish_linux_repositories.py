"""Build and sign the rolling APT and RPM repositories and stage the Pages site."""

import gzip
import hashlib
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

from merge_nucleus_release import VERSION
from release_support import cli, empty_output, require


def channel_packages(channel, inputs):
    debs = [path for path in inputs.glob("*.deb") if path.is_file()]
    rpms = [path for path in inputs.glob("*.rpm") if path.is_file()]
    require(len(debs) == len(rpms) == 1, f"Expected one DEB and one RPM for {channel}")
    deb, rpm = debs[0], rpms[0]
    version = deb.name.removeprefix("azkar-").removesuffix("-linux-x64.deb")
    require(VERSION.fullmatch(version) and deb.name == f"azkar-{version}-linux-x64.deb"
            and rpm.name == f"azkar-{version}-linux-x64.rpm", "Invalid or mismatched package versions")
    require(("-beta." in version) == (channel == "dev"), f"Wrong channel for {version}")
    rows = (inputs / "SHA256SUMS").read_text(encoding="utf-8").splitlines()
    for path in (deb, rpm):
        hashes = [row.partition("  ")[0] for row in rows if row.partition("  ")[2] == path.name]
        require(len(hashes) == 1 and re.fullmatch(r"[0-9a-f]{64}", hashes[0]), f"Invalid checksum entry: {path.name}")
        with path.open("rb") as source:
            actual = hashlib.file_digest(source, "sha256").hexdigest()
        require(actual == hashes[0], f"Checksum mismatch: {path.name}")
    return version, deb, rpm


def copy_website(website, pages):
    for name in ("download.html", "css/download.css", "js/theme.js", "js/download.js", "install.sh", "install.ps1"):
        require((website / name).is_file(), f"Website asset missing: {name}. Build the site first.")
    for name in ("css", "js", "images"):
        require((website / name).is_dir(), f"Missing website directory: {name}")
        shutil.copytree(website / name, pages / name)
    for name in ("download.html", "install.sh", "install.ps1"):
        shutil.copyfile(website / name, pages / name)
    shutil.copyfile(website / "download.html", pages / "index.html")


def sign(key_id, passphrase, *arguments):
    subprocess.run(
        ["gpg", "--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase-fd", "0",
         "--local-user", key_id, *arguments],
        input=f"{passphrase}\n", text=True, check=True,
    )


def build_channel(channel, packages, pages, key_id, passphrase):
    version, deb, rpm = packages
    apt = pages / "apt"
    pool = apt / "pool" / channel / "main/a/azkar"
    distribution = apt / "dists" / channel
    binary = distribution / "main/binary-amd64"
    rpm_repo = pages / "rpm" / channel
    for directory in (pool, binary, rpm_repo):
        directory.mkdir(parents=True)
    shutil.copyfile(deb, pool / deb.name)
    shutil.copyfile(rpm, rpm_repo / rpm.name)
    packages_index = binary / "Packages"
    with packages_index.open("wb") as output:
        subprocess.run(["apt-ftparchive", "packages", f"pool/{channel}"], cwd=apt, stdout=output, check=True)
    with packages_index.open("rb") as source, gzip.open(binary / "Packages.gz", "wb", compresslevel=9) as output:
        shutil.copyfileobj(source, output)
    release = distribution / "Release"
    # Generate the index before creating the file it describes.
    index = subprocess.check_output([
        "apt-ftparchive", "-o", "APT::FTPArchive::Release::Origin=Azkar",
        "-o", "APT::FTPArchive::Release::Label=Azkar", "-o", f"APT::FTPArchive::Release::Suite={channel}",
        "-o", f"APT::FTPArchive::Release::Codename={channel}", "-o", "APT::FTPArchive::Release::Architectures=amd64",
        "-o", "APT::FTPArchive::Release::Components=main", "release", f"dists/{channel}",
    ], cwd=apt)
    release.write_bytes(index)
    in_release = distribution / "InRelease"
    detached = distribution / "Release.gpg"
    sign(key_id, passphrase, "--clearsign", "--output", in_release, release)
    sign(key_id, passphrase, "--armor", "--detach-sign", "--output", detached, release)
    subprocess.run(["createrepo_c", "."], cwd=rpm_repo, check=True)
    rpm_metadata = rpm_repo / "repodata/repomd.xml"
    rpm_signature = rpm_metadata.with_suffix(".xml.asc")
    sign(key_id, passphrase, "--armor", "--detach-sign", "--output", rpm_signature, rpm_metadata)
    for arguments in ((in_release,), (detached, release), (rpm_signature, rpm_metadata)):
        subprocess.run(["gpg", "--verify", *arguments], check=True)
    (pages / f"{channel}-version.txt").write_text(f"{version}\n", encoding="utf-8")


def publish(inputs, pages, public_key, key_id, passphrase):
    require(inputs.is_dir(), f"Missing repository inputs: {inputs}")
    empty_output(pages)
    require(public_key.is_file(), "Missing exported repository public key")
    require(re.fullmatch(r"[A-Fa-f0-9]{16,}", key_id), "Invalid repository signing key fingerprint")
    packages = {
        channel: channel_packages(channel, inputs / channel)
        for channel in ("stable", "dev") if (inputs / channel).is_dir()
    }
    require(packages, "No repository packages")
    copy_website(Path(os.environ["GITHUB_WORKSPACE"]) / "site/build", pages)
    keys = pages / "keys"
    keys.mkdir()
    shutil.copyfile(public_key, keys / "azkar-repo.asc")
    for channel, files in packages.items():
        build_channel(channel, files, pages, key_id, passphrase)
    (pages / ".nojekyll").write_bytes(b"")
    size = sum(path.stat().st_size for path in pages.rglob("*") if path.is_file())
    require(size < 900 * 1024 * 1024, f"Repository site approaches the Pages 1 GB limit: {size} bytes")
    print(f"Built signed rolling repositories ({size} bytes)")


def main():
    require(len(sys.argv) == 5, f"Usage: {sys.argv[0]} INPUTS SITE PUBLIC_KEY FINGERPRINT")
    publish(*map(Path, sys.argv[1:4]), sys.argv[4], os.environ["REPO_PASSPHRASE"])


if __name__ == "__main__":
    cli(main)
