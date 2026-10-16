#!/usr/bin/env python3
"""Summarise a CI run into a folder that gets pushed to a ci/* branch.

GitHub's log storage isn't reachable from every development environment, so CI writes its own
summary (errors, test results, screenshots) where `git fetch` can read it.

usage: collect.py <job-name> <build-log> <out-dir> [extra-glob ...]
"""
import glob
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

job, log_path, out = sys.argv[1], sys.argv[2], sys.argv[3]
extra = sys.argv[4:]
os.makedirs(out, exist_ok=True)

log = open(log_path, errors="replace").read() if os.path.exists(log_path) else ""
lines = log.splitlines()

errors = []
for i, line in enumerate(lines):
    if re.match(r"^e: ", line) or "error:" in line and ("swift" in line or ".kt" in line or ".sq" in line or ".sqm" in line):
        errors.append(line.strip())
    if line.startswith("* What went wrong:"):
        block = []
        for l in lines[i + 1:i + 40]:
            if l.startswith("* Try:"):
                break
            block.append(l)
        errors.append("What went wrong:\n" + "\n".join(block).strip())
    if "** BUILD FAILED **" in line or "error: " in line and "xcodebuild" in log_path:
        errors.append(line.strip())

# JUnit XML from Gradle (jvm, android host, iOS simulator) tests.
suites = []
for path in glob.glob("**/build/test-results/**/*.xml", recursive=True):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for suite in ([root] if root.tag == "testsuite" else root.findall("testsuite")):
        task = path.split("/build/test-results/")[1].split("/")[0]
        module = path.split("/build/test-results/")[0]
        failures = []
        for case in suite.findall("testcase"):
            for f in list(case.findall("failure")) + list(case.findall("error")):
                failures.append((case.get("classname"), case.get("name"), (f.get("message") or "")[:500], (f.text or "")[:4000]))
        suites.append(dict(
            name=suite.get("name"), module=module, task=task,
            tests=int(suite.get("tests", 0)), failures=int(suite.get("failures", 0)) + int(suite.get("errors", 0)),
            skipped=int(suite.get("skipped", 0)), time=float(suite.get("time", 0) or 0), failed=failures,
        ))

total = sum(s["tests"] for s in suites)
failed = sum(s["failures"] for s in suites)
skipped = sum(s["skipped"] for s in suites)

with open(os.path.join(out, "summary.md"), "w") as f:
    f.write(f"# {job}\n\ncommit: {os.environ.get('GITHUB_SHA', '?')}\n\n")
    f.write(f"tests: {total}, failed: {failed}, skipped: {skipped}\n\n")
    by_task = {}
    for s in suites:
        key = f"{s['module']}:{s['task']}"
        t = by_task.setdefault(key, [0, 0, 0.0])
        t[0] += s["tests"]; t[1] += s["failures"]; t[2] += s["time"]
    for k, (n, fl, tm) in sorted(by_task.items()):
        f.write(f"- {k}: {n} tests, {fl} failed, {tm:.1f}s\n")
    if errors:
        f.write("\n## Build errors\n\n")
        seen = set()
        for e in errors:
            if e in seen:
                continue
            seen.add(e)
            f.write("```\n" + e[:3000] + "\n```\n")
    fails = [x for s in suites for x in s["failed"]]
    if fails:
        f.write("\n## Test failures\n\n")
        for cls, name, msg, text in fails[:60]:
            f.write(f"### {cls}.{name}\n{msg}\n```\n{text}\n```\n")

# Keep the tail of the log and a few extras (screenshots, reports).
with open(os.path.join(out, "log-tail.txt"), "w") as f:
    f.write("\n".join(lines[-600:]))
with open(os.path.join(out, "stdout-lines.txt"), "w") as f:
    # Lines printed by tests (e.g. timings, SQLite version) are prefixed by Gradle with nothing special;
    # keep anything that looks like our own diagnostics.
    for s in glob.glob("**/build/test-results/**/*.xml", recursive=True):
        try:
            root = ET.parse(s).getroot()
        except ET.ParseError:
            continue
        for so in root.iter("system-out"):
            if so.text and so.text.strip():
                f.write(f"== {s}\n{so.text.strip()[:4000]}\n")
for pattern in extra:
    for p in glob.glob(pattern, recursive=True):
        if os.path.isfile(p):
            dest = os.path.join(out, "files", p.replace("/", "__"))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copy(p, dest)

print(open(os.path.join(out, "summary.md")).read())
for e in errors[:8]:
    msg = e.replace("%", "%25").replace("\r", "").replace("\n", "%0A")[:3500]
    print(f"::error title={job} build error::{msg}")
