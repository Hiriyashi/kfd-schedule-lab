"""Compare console text and exit codes with the sample manifest."""
import hashlib
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "verification"


def text_summary(path):
    digest = hashlib.sha256()
    size = 0
    last = ""
    count = 0
    with path.open(encoding="utf-8", newline=None) as source:
        for line in source:
            last = line.rstrip("\r\n")
            data = (last + "\n").encode("utf-8")
            digest.update(data)
            size += len(data)
            count += 1
    return {"sha256": digest.hexdigest(), "text_bytes": size, "lines": count, "last_line": last}


def verify(huge=False):
    OUTPUT.mkdir(exist_ok=True)
    manifest = json.loads((ROOT / "samples/manifest.json").read_text(encoding="utf-8"))
    binary = ROOT / "app/build/install/schedule/bin" / ("schedule.bat" if os.name == "nt" else "schedule")
    if not binary.is_file():
        print("Build the application first: Gradle build :app:installDist")
        return 1
    if huge and not (ROOT / "samples/huge.txt").is_file():
        print("Generate the large file first: python tools/generate_samples.py --huge")
        return 1
    env = os.environ.copy()
    # Give captured println output a consistent encoding on every operating system.
    env["JAVA_OPTS"] = (env.get("JAVA_OPTS", "") + " -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8").strip()
    for key in ("JDK_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS"):
        env.pop(key, None)
    results = []

    def execute(label, args, expected=None):
        out_path = OUTPUT / (label + ".output.txt")
        start = time.perf_counter()
        with out_path.open("wb") as output:
            completed = subprocess.run([str(binary), *args], cwd=ROOT, env=env,
                                       stdout=output, stderr=subprocess.STDOUT, timeout=180)
        summary = text_summary(out_path)
        if isinstance(expected, str):
            ok = (completed.returncode == 2 and summary["lines"] == 1
                  and summary["last_line"] == expected)
            expected_code = 2
        else:
            expected_code = expected["code"]
            ok = (completed.returncode == expected_code
                  and summary["sha256"] == expected["sha256"]
                  and summary["text_bytes"] == expected["bytes"])
        result = {"case": label, "code": completed.returncode, "expected_code": expected_code,
                  **summary, "seconds": round(time.perf_counter() - start, 3), "passed": ok}
        results.append(result)
        print(json.dumps(result, ensure_ascii=False), flush=True)
        if label.startswith("huge.txt."):
            out_path.unlink()

    names = ["clean.txt", "dirty.txt", "empty.txt"]
    if huge:
        names.append("huge.txt")
    for name in names:
        for command in ("period", "type", "errors"):
            args = [command, "--file", "samples/" + name]
            if command == "period":
                args += ["--from", "2026-09-28", "--to", "2027-05-13"]
            if command == "type":
                args += ["--type", "EXAM"]
            execute(f"{name}.{command}", args, manifest["commands"][f"{name}/{command}"])

    october = (ROOT / "samples/dirty.october.expected").read_bytes()
    execute("dirty.october", ["period", "--file", "samples/dirty.txt",
                            "--from", "2026-10-01", "--to", "2026-10-31"],
            {"code": 1, "bytes": len(october), "sha256": hashlib.sha256(october).hexdigest()})

    execute("missing-arguments", [], "Invalid arguments")
    execute("invalid-type", ["type", "--file", "samples/clean.txt", "--type", "exam"], "Invalid arguments")
    execute("reversed-period", ["period", "--file", "samples/clean.txt",
                               "--from", "2027-05-13", "--to", "2026-09-28"], "Invalid arguments")
    with tempfile.TemporaryDirectory(prefix="schedule-check-") as folder:
        execute("missing-file", ["errors", "--file", str(Path(folder) / "missing.txt")], "Cannot read file")
        execute("directory", ["errors", "--file", folder], "Cannot read file")
        # One example of each type, independent of the sample generator.
        file = Path(folder) / "four-types.txt"
        records = [
            "2026-10-05|LECTURE|Topic L|Room 1|Ivanov",
            "2026-10-05|PRACTICE|Topic P|Room 2|Petrov",
            "2026-10-05|EXAM|Topic E|Room 3|Ivanov,Petrov",
            "2026-10-05|CANCELLED|Topic C|-|-",
        ]
        file.write_bytes(("\r\n".join(records) + "\r\n").encode("ascii"))
        for record in records:
            fields = record.split("|")
            kind = fields[1]
            fields[-1] = fields[-1].replace(",", ", ")
            expected = (" | ".join(fields) + "\nTotal: 1\n").encode("ascii")
            execute("four-types." + kind, ["type", "--type", kind, "--file", str(file)],
                    {"code": 0, "bytes": len(expected), "sha256": hashlib.sha256(expected).hexdigest()})

    report = {"cases": results, "passed": all(r["passed"] for r in results)}
    (OUTPUT / "acceptance.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--huge", action="store_true", help="Also verify the optional two-million-line file")
    sys.exit(verify(parser.parse_args().huge))
