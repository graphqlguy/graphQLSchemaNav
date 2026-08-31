# graphQLSchemaNav

Companion code for **Class 3, Schema Navigation at Scale**, of the GraphQL for AI
Agents course: a command-line instrument that indexes a GraphQL schema, retrieves
coordinates for a natural-language question, and measures both accuracy and token
cost.

## How this repository is organised

`main` is the **starting point**, not a finished project. It carries the project
skeleton, the Maven dependencies, the Movie Database schema, and a small labelled
benchmark file. Clone it and follow the class; it builds the instrument on top.

| Branch | Contents |
| --- | --- |
| `main` | the starting point |
| `schemanav_class_3` | the finished instrument: corpus rendering, BM25 and embedding backends, the benchmark harness, and the agent loop |

The rest of the course has its own companion, `graphQLMovieDB-agents`.
