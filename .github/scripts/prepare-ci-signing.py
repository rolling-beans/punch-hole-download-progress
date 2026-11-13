#!/usr/bin/env python3
"""Load an encrypted GitHub Actions secret into an ephemeral release keystore.

Never print the secret JSON, password or PKCS#12 contents. Refuse signing
with a different certificate to prevent accidental key rotation.
"""
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def error(reason: str) -> None:
    sys.exit(f"CI signing setup: {reason}")


raw = os.getenv("CI_SIGNING_BUNDLE", "")
if not raw:
    error("signing bundle secret is missing; configure the repository's Actions secret")

try:
    bundle = json.loads(raw)
    encoded = bundle["keystore"]
    password = bundle["password"]
    alias = bundle["alias"]
    if not isinstance(encoded, str) or not isinstance(password, str) or not isinstance(alias, str):
        error("invalid signing bundle fields")
    if not password or "\n" in password or "\r" in password:
        error("invalid password")
    if not re.fullmatch(r"[a-zA-Z0-9_.-]{1,64}", alias):
        error("invalid key alias")
    data = base64.b64decode(encoded, validate=True)
    if not 1000 <= len(data) <= 65536:
        error("invalid keystore size")
except (ValueError, KeyError, TypeError) as exc:
    error(f"invalid signing bundle format ({type(exc).__name__})")

expected = os.getenv("EXPECTED_CERT_SHA256", "").lower().replace(":", "")
if not re.fullmatch(r"[0-9a-f]{64}", expected):
    error("expected certificate fingerprint not set")

os.umask(0o077)
runner_temp = Path(os.environ.get("RUNNER_TEMP", "/tmp"))
keystore = runner_temp / "ci-release-signing.p12"
keystore.write_bytes(data)
keystore.chmod(0o600)

# A bundle is one masked secret, but extracted fields must also be masked.
print("::add-mask::" + password, flush=True)
keytool_env = dict(os.environ, RELEASE_STORE_PASSWORD=password)
command = [
    "keytool", "-list", "-v", "-alias", alias,
    "-keystore", str(keystore), "-storepass:env", "RELEASE_STORE_PASSWORD",
]
result = subprocess.run(command, text=True, capture_output=True, env=keytool_env, check=False)
if result.returncode:
    error("keystore cannot be opened with the configured password and alias")
match = re.search(r"SHA256:\s*([a-fA-F0-9:]+)", result.stdout)
if not match:
    error("certificate fingerprint not found in keystore")
actual = match.group(1).replace(":", "").lower()
if actual != expected:
    error("certificate fingerprint mismatch; refusing unexpected signing key")

github_env = os.getenv("GITHUB_ENV")
if not github_env:
    error("GITHUB_ENV is missing")
with open(github_env, "a", encoding="utf-8") as out:
    out.write(f"RELEASE_STORE_FILE={keystore}\n")
    out.write(f"RELEASE_STORE_PASSWORD={password}\n")
    out.write(f"RELEASE_KEY_ALIAS={alias}\n")
    out.write(f"RELEASE_KEY_PASSWORD={password}\n")
    out.write("RELEASE_STORE_TYPE=PKCS12\n")
print("CI release signing certificate verified: SHA256 " + actual)
