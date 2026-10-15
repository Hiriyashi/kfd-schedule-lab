"""Check committed samples against deterministic regeneration without modifying them."""
from pathlib import Path
import tempfile
import generate_samples

ROOT = Path(__file__).resolve().parents[1]


def check():
    with tempfile.TemporaryDirectory(prefix="schedule-samples-") as folder:
        generate_samples.SAMPLES = Path(folder)
        generate_samples.generate()
        differences = []
        for expected in sorted(Path(folder).iterdir()):
            actual = ROOT / "samples" / expected.name
            if not actual.is_file() or actual.read_bytes() != expected.read_bytes():
                differences.append(expected.name)
        if differences:
            print("Sample mismatch: " + ", ".join(differences))
            return 1
    print("Samples and expected output are consistent")
    return 0


if __name__ == "__main__":
    raise SystemExit(check())
