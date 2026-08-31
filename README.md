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
   (see the class for the `Modelfile` built from the `q8_0` GGUF of
   `xthor/Qwen3-Embedding-0.6B-GraphQL`), or use any embedding model Ollama serves.
2. Set `schemanav.retrieval.backend: embedding` and the model name under
   `spring.ai.ollama.embedding.options.model` in `application.yaml`.
3. Re-run the same commands; the benchmark table now measures that model.

## Status

Scaffold. Working today: schema loading (SDL file or introspection), corpus generation
in three formats with token accounting, the Lucene BM25 backend, the embedding backend
against a running Ollama, and the benchmark harness with a Movie DB smoke-test set.
Still to come, tracked in the class spec: the Working Group benchmark data for the
large schemas, the query/document prompt handling for the fine-tuned model, and the
search/introspect/execute agent loop with live token spend.
