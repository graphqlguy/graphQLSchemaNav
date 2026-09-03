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
   `spring.ai.ollama.embedding.model`.
2. Run any command with `--schemanav.retrieval.backend=embedding` among its
   arguments (or set the backend in `application.yaml`):

   ```bash
   mvn -q spring-boot:run \
     -Dspring-boot.run.arguments="bench --schemanav.retrieval.backend=embedding"
   ```

One detail the code handles for you, reproduced from the model's
sentence-transformers configuration: the model is instruction-tuned, so questions are
embedded with an instruction prefix (`schemanav.retrieval.query-prefix`) while corpus
entries are embedded plain. Removing the prefix silently degrades ranking.

## The agent loop

```bash
mvn -q spring-boot:run -Dspring-boot.run.arguments="agent get the titles of the most recent open pull requests in a repository"
```

This runs the search/introspect/execute pattern end to end with a local chat model
(any tool-calling model Ollama serves; `spring.ai.ollama.chat.model`). The
loop is driven by hand instead of letting the framework execute tools invisibly,
because watching the mechanism is the point. What you see, turn by turn:

1. The model receives the conversation and either answers or asks for a tool.
2. `searchSchema` returns ranked coordinates (within the context budget when
   `schemanav.retrieval.context-budget-tokens` is set), `introspectType` returns one
   type's full definition, `executeGraphql` validates the operation against the
   schema (and runs it when `schemanav.execute.endpoint` points at a live service).
3. Every tool result prints with its token cost, and every model call prints the
   provider-reported prompt and completion tokens.

The run ends with a **context receipt** separating the two currencies that are easy
to conflate. *Prompt tokens* are what the provider actually processed: the whole
conversation is resent on every call, so every tool result is paid for again on each
later turn; that compounding is why payload sizes matter. *Tool payload tokens* are
what the three tools injected, measured with the fixed yardstick: the part schema
navigation controls. The receipt's last line states the alternative for contrast:
inlining the whole schema would add its full corpus size to every single call
(211,092 tokens for the GitHub snapshot), which is the number this entire pattern
exists to avoid. On the small Movie DB schema the receipt shows the opposite and
equally honest lesson: the whole schema is 1,030 tokens, so there navigation costs
more than inclusion, and the pattern is the wrong tool.

## Status

Working today, all verified by running: schema loading (SDL file or introspection),
corpus generation in three formats with token accounting, the Lucene BM25 backend,
the embedding backend against a local Ollama serving the GraphQL fine-tune (with the
query/document prompt handling, batched and disk-cached), the benchmark harness with
multi-target recall, the context-budget sweep, the Working Group's GitHub benchmark
slice (`scripts/fetch-wg-benchmark.sh`), and the agent loop with the context receipt.
Known refinements worth making: `introspectType` on a big type (GitHub's Repository)
returns a very large payload and deserves its own budget cap, and the loop's chat
model is Ollama-only so far (an Anthropic option is planned).
