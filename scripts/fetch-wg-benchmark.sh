#!/usr/bin/env bash
# Downloads the GitHub-schema slice of the GraphQL AI Working Group's evaluation
# benchmark: the pinned schema snapshot and the 200 labelled queries (100
# natural-language, 100 keyword variants), then converts the queries into this
# project's TSV format.
#
# The data lives in the still-open PR #140 branch of graphql/ai-wg
# (benchmark/evaluation). It is fetched on demand instead of being committed
# here, and the pinned schema snapshot is used instead of a current GitHub SDL
# because the labels refer to it.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p schemas benchmarks .wg-cache/queries

BASE="https://raw.githubusercontent.com/graphql/ai-wg/pse/adds-semantic-search-evaulation-bench/benchmark/evaluation/src"

echo "Fetching the pinned GitHub schema snapshot..."
curl -fsSL "$BASE/schemas/github/schema.graphql" -o schemas/github-wg.graphqls

echo "Fetching 200 labelled queries..."
for i in $(seq -w 1 100); do
  for suffix in "" "-kw"; do
    f="gh-ext-$i$suffix.yaml"
    if [ ! -s ".wg-cache/queries/$f" ]; then
      curl -fsSL "$BASE/queries/all-schemas/$f" -o ".wg-cache/queries/$f" || echo "  missing: $f"
    fi
  done
done

echo "Converting to benchmarks/github-wg.tsv..."
python3 - <<'PYEOF'
import pathlib, re

out = []
skipped_targets = 0
for f in sorted(pathlib.Path('.wg-cache/queries').glob('gh-ext-*.yaml')):
    text = f.read_text()
    # Minimal YAML reading for this known, simple layout (avoids a YAML dependency).
    m = re.search(r'^query:\s*(.+?)(?=^\w+:)', text, re.M | re.S)
    question = ' '.join(m.group(1).split())
    mi = re.search(r'^mustInclude:\n((?:\s+-\s+.*\n)+)', text, re.M)
    coords = re.findall(r'-\s+(\S+)', mi.group(1)) if mi else []
    # Keep plain Type.field coordinates; the project's index has no entries for
    # argument coordinates like Commit.history(first:) or bare type names.
    plain = [c for c in coords if re.fullmatch(r'\w+\.\w+', c)]
    skipped_targets += len(coords) - len(plain)
    if plain:
        out.append(question + '\t' + ','.join(plain))

header = ("# GitHub-schema slice of the AI Working Group evaluation benchmark\n"
          "# (graphql/ai-wg PR #140 branch). Targets are the plain Type.field\n"
          "# entries of each query's mustInclude list; argument coordinates and\n"
          "# bare type names are dropped because the index has no entries for them.\n")
pathlib.Path('benchmarks/github-wg.tsv').write_text(header + '\n'.join(out) + '\n')
print(f"wrote benchmarks/github-wg.tsv: {len(out)} queries, "
      f"{sum(len(l.split(chr(9))[1].split(',')) for l in out)} plain-field targets, "
      f"{skipped_targets} non-field targets dropped")
PYEOF

LINES=$(wc -l < schemas/github-wg.graphqls | tr -d ' ')
echo "Schema snapshot: schemas/github-wg.graphqls ($LINES lines)"
