#!/usr/bin/env bash
# Downloads a snapshot of GitHub's public GraphQL schema into schemas/github.graphqls.
#
# The SDL is published in the octokit/graphql-schema repository. It is fetched on
# demand instead of being committed here; check GitHub's terms before redistributing
# the file itself.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p schemas

URL="https://raw.githubusercontent.com/octokit/graphql-schema/master/schema.graphql"
OUT="schemas/github.graphqls"

echo "Fetching GitHub's public GraphQL schema..."
curl -fsSL "$URL" -o "$OUT"

LINES=$(wc -l < "$OUT" | tr -d ' ')
echo "Saved $OUT ($LINES lines)."
echo "Try it with:"
echo "  mvn -q spring-boot:run -Dspring-boot.run.arguments=\"corpus\" -Dspring-boot.run.jvmArguments=\"-Dschemanav.schema.source=$OUT\""
