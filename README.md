# graphQLSchemaNav

The companion project for the graphQLGuy class on schema navigation at scale: an
instrument for measuring how well an AI agent can find its way around a GraphQL schema,
and at what token cost. It is a plain Spring Boot command-line application; there is no
server in it.

Three things are pluggable by configuration, because experimenting with them is the
point of the class:

- **The schema.** `schemanav.schema.source` takes a path to an SDL file or an http(s)
  endpoint to introspect. The bundled default is a small Movie Database schema;
  `scripts/fetch-github-schema.sh` downloads GitHub's public schema for the
  large-scale experiments.
- **The retrieval backend.** `schemanav.retrieval.backend` is `bm25` (Apache Lucene,
  works with nothing else installed) or `embedding` (any model a local Ollama serves).
- **The corpus format.** `schemanav.corpus.format` renders each schema coordinate as
  `RAW`, `GLOSS`, or `SDL` text before indexing; the format changes retrieval quality
  as much as the model does, which the benchmark makes visible.

Every result the instrument prints carries its token cost, measured with one fixed
tokenizer encoding (`schemanav.tokens.encoding`, default `O200K_BASE` via JTokkit).
Token counts differ per model family, so the fixed encoding is a consistent ruler for
comparisons, not a billing prediction.

## Quickstart

```bash
# Corpus statistics for the bundled Movie DB schema, per format
mvn -q spring-boot:run -Dspring-boot.run.arguments=corpus

# One search against the BM25 backend
mvn -q spring-boot:run -Dspring-boot.run.arguments="search movies with a high rating"

# The labelled-query benchmark: accuracy and tokens side by side
mvn -q spring-boot:run -Dspring-boot.run.arguments=bench
```

## The embedding backend

1. Install [Ollama](https://ollama.com) and pull the GraphQL-tuned embedding model
   straight from Hugging Face, then give it the name the configuration expects:

   ```bash
   ollama pull hf.co/xthor/Qwen3-Embedding-0.6B-GraphQL:Q8_0
   ollama cp hf.co/xthor/Qwen3-Embedding-0.6B-GraphQL:Q8_0 qwen3-graphql-embedder
   ```

   Any other embedding model Ollama serves works too; set its name under
   `spring.ai.ollama.embedding.options.model`.
2. Run any command with `--schemanav.retrieval.backend=embedding` (or set it in
   `application.yaml`).

One detail the code handles for you, reproduced from the model's
sentence-transformers configuration: the model is instruction-tuned, so questions are
embedded with an instruction prefix (`schemanav.retrieval.query-prefix`) while corpus
entries are embedded plain. Removing the prefix silently degrades ranking.

## Status

Working today, all verified by running: schema loading (SDL file or introspection),
corpus generation in three formats with token accounting, the Lucene BM25 backend,
the embedding backend against a local Ollama serving the GraphQL fine-tune (with the
query/document prompt handling), and the benchmark harness with a Movie DB smoke-test
set. Still to come, tracked in the class spec: the Working Group benchmark data for
the large schemas and the search/introspect/execute agent loop with live token spend.
