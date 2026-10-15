"""Create schedule examples and their expected console output; stdlib only."""
from pathlib import Path
from datetime import date, timedelta
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parents[1]
SAMPLES = ROOT / "samples"


def row(day, kind, topic, room, teachers):
    return [str(day), kind, topic, room, teachers]


FIRST = [
    row("2026-10-05", "LECTURE", "Classes and delegation", "Room 312", "Portnov"),
    row("2026-10-08", "PRACTICE", "Domain modeling", "Room 312", "Anokhin"),
    row("2026-10-15", "EXAM", "Oral exam 1", "Room 401", "Portnov,Stepanova"),
    row("2026-10-19", "CANCELLED", "Lecture postponed", "-", "-"),
]


def render(fields, teachers=False):
    d, kind, topic, room, names = fields
    values = [d, kind, topic, room]
    if teachers:
        values.append(names.replace(",", ", "))
    return " | ".join(values) + "\n"


def write(name, value):
    data = value.encode("utf-8") if isinstance(value, str) else value
    (SAMPLES / name).write_bytes(data)


def digest(data):
    return {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def generate(huge=False):
    SAMPLES.mkdir(exist_ok=True)
    clean = FIRST.copy()
    for i in range(196):
        day = date(2026, 9, 28) + timedelta(days=(i * 17) % 228)
        kind = ["LECTURE", "PRACTICE", "EXAM", "CANCELLED"][i % 4]
        clean.append(row(day, kind, f"Topic {i + 5:03}", "-" if kind == "CANCELLED" else "Room 312",
                         "-" if kind == "CANCELLED" else "Portnov,Stepanova" if kind == "EXAM" else "Anokhin"))
    clean[-1] = row("2027-05-13", "LECTURE", "Final session", "Room 312", "Portnov")
    dirty = FIRST.copy() + [row("2026-11-01", "LECTURE", f"Topic {i:03}", "Room 312", "Anokhin") for i in range(5, 501)]
    corruptions = [
        ("2026-13-45|LECTURE|Topic|Room 1|Ivanov", 'invalid date "2026-13-45"'),
        ("|LECTURE|Topic|Room 1|Ivanov", 'empty required field "date"'),
        ("2026-10-05|SEMINAR|Topic|Room 1|Ivanov", 'unknown session type "SEMINAR"'),
        ("2026-10-05;LECTURE;Topic;Room 1;Ivanov", "invalid field separator"),
        ("2026-10-05|EXAM|Topic|Room 1|Ivanov", 'type "EXAM" requires at least 2 teachers, got 1'),
        ("2026-10-05|LECTURE|Topic|Room 1|Ivanov|extra", "got 6 fields, expected 5"),
    ]
    failures = {}
    for index, number in enumerate([14, 27, 31] + list(range(32, 193))):
        raw, message = corruptions[index % len(corruptions)]
        dirty[number - 1] = raw
        failures[number] = f"line {number}: {message}\n"
    assert len(clean) == 200 and len(dirty) == 500 and len(failures) == 164
    manifest = {"generator": "v3", "commands": {}, "files": {}}
    for name, records, rejected in [("clean.txt", clean, {}), ("dirty.txt", dirty, failures), ("empty.txt", [], {})]:
        write(name, "".join(("|".join(r) if isinstance(r, list) else r) + "\n" for r in records))
        valid = [r for r in records if isinstance(r, list)]
        outputs = {
            "period": "".join(render(r) for r in valid) + f"Total: {len(valid)}\n",
            "type": "".join(render(r, True) for r in valid if r[1] == "EXAM") + f"Total: {sum(r[1] == 'EXAM' for r in valid)}\n",
            "errors": "".join(rejected.values()) + f"Rejected: {len(rejected)} of {len(records)}\n",
        }
        for command, output in outputs.items():
            expected = f"{name.removesuffix('.txt')}.{command}.expected"
            write(expected, output)
            manifest["commands"][f"{name}/{command}"] = {"code": int(bool(rejected)), "expected": expected, **digest(output.encode())}
    write("clean.expected", (SAMPLES / "clean.period.expected").read_bytes())
    write("dirty.october.expected", "".join(render(r) for r in FIRST) + "Total: 4\n")
    huge_row = row("2026-10-05", "LECTURE", "Streaming parsing", "Room 312", "Portnov")
    block = ("|".join(huge_row) + "\n").encode()
    count = 2_000_000
    if huge:
        with (SAMPLES / "huge.txt").open("wb") as target:
            for _ in range(count // 10_000): target.write(block * 10_000)
    for command in ("period", "type", "errors"):
        h = hashlib.sha256()
        total = 0
        if command == "period":
            chunk = render(huge_row).encode() * 10_000
            for _ in range(count // 10_000): h.update(chunk); total += len(chunk)
        last = "Total: 2000000\n" if command == "period" else "Total: 0\n" if command == "type" else "Rejected: 0 of 2000000\n"
        h.update(last.encode()); total += len(last.encode())
        manifest["commands"][f"huge.txt/{command}"] = {"code": 0, "bytes": total, "sha256": h.hexdigest()}
    for path in sorted(SAMPLES.iterdir()):
        if path.is_file() and path.name not in ("manifest.json", "huge.txt"):
            h = hashlib.sha256()
            with path.open("rb") as source:
                while chunk := source.read(1024 * 1024): h.update(chunk)
            manifest["files"][path.name] = {"bytes": path.stat().st_size, "sha256": h.hexdigest()}
    (SAMPLES / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps({"clean": 200, "dirty": 500, "rejected": 164, "huge_lines": count if huge else "not generated", "huge_bytes": len(block) * count}, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--huge", action="store_true")
    generate(parser.parse_args().huge)
