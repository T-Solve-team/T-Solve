"""Convert the T-Solve project docs (team Google Drive folder, outside this repo) to Markdown in
<docs>/_context/ for AI. Used by the /tsolve-context Claude Code command.

Usage:  python scripts/build_context.py [docs-folder] [--force]
        docs-folder defaults to the TSOLVE_DOCS environment variable.
        Only changed docs are re-converted; --force re-converts everything.
Needs:  pip install "markitdown[docx,pdf,xlsx]"
"""
import html
import os
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

# Reference material / samples / env, not T-Solve content.
SKIP_DIRS = {".venv", "_context", ".claude", "Report 1 Sample", "Report 2 Sample",
             "Sample Doc Do An", "SEP490 Templates-20260916", "__pycache__", "Daily Report"}
SKIP_FILES = {"Software_Requirements_3rd_Edition.pdf", "Sách học làm report 3.pdf",
              "grep.exe.stackdump", "CLAUDE.md"}

SETUP_HELP = """Docs folder not found. Point TSOLVE_DOCS to your local copy of the project docs folder,
e.g. in .claude/settings.local.json (git-ignored), then restart Claude Code:
  { "env": { "TSOLVE_DOCS": "C:/path/to/SEP490_G89" },
    "permissions": { "additionalDirectories": ["C:/path/to/SEP490_G89"] } }
or pass it as the first argument: python scripts/build_context.py C:/path/to/SEP490_G89"""

CONVERT = {".docx", ".pdf", ".xlsx", ".xlsm"}
COPY = {".md", ".txt"}
IMAGE = {".png", ".jpg", ".jpeg", ".svg"}


def drawio_to_md(path):
    """Diagram labels as vertex list + edge list (drawio raw XML is too noisy)."""
    clean = lambda s: re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", html.unescape(s or ""))).strip()
    lines = []
    for diagram in ET.parse(path).getroot().iter("diagram"):
        cells = {c.get("id"): c for c in diagram.iter("mxCell")}
        if not cells:  # ponytail: compressed drawio not handled, save uncompressed in draw.io if hit
            lines.append(f"\n## {diagram.get('name')}\n(compressed diagram, open the PNG instead)")
            continue
        lines.append(f"\n## {diagram.get('name')}\n\n### Nodes")
        lines += [f"- {clean(c.get('value'))}" for c in cells.values()
                  if c.get("vertex") == "1" and clean(c.get("value"))]
        lines.append("\n### Edges")
        for c in cells.values():
            if c.get("edge") == "1":
                src = clean(cells.get(c.get("source"), ET.Element("x")).get("value")) or "?"
                dst = clean(cells.get(c.get("target"), ET.Element("x")).get("value")) or "?"
                label = clean(c.get("value"))
                lines.append(f"- {src} -> {dst}" + (f" : {label}" if label else ""))
    return "\n".join(lines)


def csv_to_md(path, n=5):
    with open(path, encoding="utf-8", errors="replace") as f:
        head = [next(f, "") for _ in range(n + 1)]
    return f"Dataset sample (header + first {n} rows):\n\n```csv\n{''.join(head)}```"


def main():
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # Vietnamese file names on Windows console
    force = "--force" in sys.argv
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    docs = args[0] if args else os.environ.get("TSOLVE_DOCS", "")
    if not docs or not Path(docs).is_dir():
        sys.exit(SETUP_HELP)
    try:
        from markitdown import MarkItDown
    except ImportError:
        sys.exit('markitdown is not installed: pip install "markitdown[docx,pdf,xlsx]"')
    ROOT = Path(docs).resolve()
    OUT = ROOT / "_context"
    print(f"DOCS={ROOT.as_posix()}")
    md = None
    index = []  # (source, context file or None, note)
    for src in sorted(ROOT.rglob("*")):
        rel = src.relative_to(ROOT)
        if not src.is_file() or SKIP_DIRS & set(rel.parts) or src.name in SKIP_FILES \
                or ".bak" in src.name or src.suffix == ".lck":
            continue
        ext = src.suffix.lower()
        if ext in IMAGE:
            index.append((rel, None, "image, Read it directly"))
            continue
        if ext not in CONVERT | COPY | {".drawio", ".csv"}:
            index.append((rel, None, "not converted (binary/huge export)"))
            continue

        dst = OUT / rel.with_name(rel.name + ".md")
        if force or not dst.exists() or dst.stat().st_mtime < src.stat().st_mtime:
            try:
                if ext in CONVERT:
                    md = md or MarkItDown()
                    text = md.convert(str(src)).text_content
                elif ext == ".drawio":
                    text = drawio_to_md(src)
                elif ext == ".csv":
                    text = csv_to_md(src)
                else:
                    text = src.read_text(encoding="utf-8", errors="replace")
            except Exception as e:  # one bad file must not stop the rest
                print(f"FAIL {rel}: {e}")
                index.append((rel, None, f"convert failed: {type(e).__name__}"))
                continue
            dst.parent.mkdir(parents=True, exist_ok=True)
            dst.write_text(f"<!-- source: {rel.as_posix()} -->\n{text}", encoding="utf-8")
            print(f"ok   {rel}")
        tokens = dst.stat().st_size // 4  # rough: ~4 bytes/token
        index.append((rel, dst.relative_to(ROOT), f"~{tokens:,} tokens"))

    # Drop outputs whose source was deleted or moved (_context/ is generated, safe to prune).
    keep = {ROOT / c for _, c, _ in index if c} | {OUT / "INDEX.md"}
    for old in OUT.rglob("*.md"):
        if old not in keep:
            old.unlink()
            print(f"del  {old.relative_to(OUT)}")
    for d in sorted(OUT.rglob("*"), key=lambda p: len(p.parts), reverse=True):
        if d.is_dir() and not any(d.iterdir()):
            d.rmdir()

    rows = [f"| {s.as_posix()} | {c.as_posix() if c else '-'} | {n} |" for s, c, n in index]
    (OUT / "INDEX.md").write_text(
        "# T-Solve context index\n\nGenerated by `t-solve/scripts/build_context.py`. Do not edit.\n\n"
        "| Source | Context file | Note |\n|---|---|---|\n" + "\n".join(rows) + "\n",
        encoding="utf-8")
    print(f"\n{len(index)} files indexed -> {OUT / 'INDEX.md'}")


if __name__ == "__main__":
    main()
