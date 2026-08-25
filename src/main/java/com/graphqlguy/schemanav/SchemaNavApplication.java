package com.graphqlguy.schemanav;

import com.graphqlguy.schemanav.bench.BenchmarkRunner;
import com.graphqlguy.schemanav.bench.LabelledQuery;
import com.graphqlguy.schemanav.config.SchemaNavProperties;
import com.graphqlguy.schemanav.corpus.CorpusEntry;
import com.graphqlguy.schemanav.corpus.CorpusFormat;
import com.graphqlguy.schemanav.corpus.CorpusGenerator;
import com.graphqlguy.schemanav.retrieval.SearchBackend;
import com.graphqlguy.schemanav.retrieval.SearchHit;
import com.graphqlguy.schemanav.schema.SchemaSource;
import com.graphqlguy.schemanav.tokens.TokenMeter;
import graphql.schema.GraphQLSchema;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * The command-line entry point. Commands:
 *
 *   corpus              print corpus statistics per format (entries and token totals)
 *   search <question>   index, then run one query and print ranked hits with token footers
 *   bench [file]        run the labelled-query benchmark and print the accuracy/cost table
 *
 * Everything configurable lives in application.yaml under schemanav.* and can be
 * overridden per run, for example:
 *   --schemanav.schema.source=schemas/github.graphqls
 *   --schemanav.retrieval.backend=embedding
 *   --schemanav.corpus.format=SDL
 */
@SpringBootApplication
@EnableConfigurationProperties(SchemaNavProperties.class)
public class SchemaNavApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchemaNavApplication.class, args);
    }

    @Bean
    CommandLineRunner commands(SchemaNavProperties properties,
                               SchemaSource schemaSource,
                               CorpusGenerator corpusGenerator,
                               SearchBackend backend,
                               BenchmarkRunner benchmarkRunner,
                               TokenMeter tokenMeter) {
        return args -> {
            if (args.length == 0) {
                System.out.println("""
                        Usage:
                          corpus              corpus statistics per format
                          search <question>   one query against the configured backend
                          bench [file]        labelled-query benchmark (accuracy and tokens)""");
                return;
            }
            GraphQLSchema schema = schemaSource.load();
            CorpusFormat format = properties.getCorpus().getFormat();
            String command = args[0];

            switch (command) {
                case "corpus" -> {
                    System.out.println("schema  : " + schemaSource.describe());
                    for (CorpusFormat f : CorpusFormat.values()) {
                        List<CorpusEntry> corpus = corpusGenerator.generate(schema, f);
                        long tokens = corpus.stream().mapToLong(CorpusEntry::tokenCount).sum();
                        System.out.printf("%-6s : %d coordinates, %d tokens total (%s)%n",
                                f, corpus.size(), tokens, tokenMeter.encodingName());
                    }
                }
                case "search" -> {
                    String question = String.join(" ",
                            Arrays.copyOfRange(args, 1, args.length));
                    if (question.isBlank()) {
                        System.out.println("search needs a question, e.g.: search movies with a high rating");
                        return;
                    }
                    List<CorpusEntry> corpus = corpusGenerator.generate(schema, format);
                    backend.index(corpus);
                    System.out.println("schema  : " + schemaSource.describe());
                    System.out.println("backend : " + backend.name() + ", corpus format " + format);
                    System.out.println("query   : " + question);
                    System.out.println();
                    int budget = properties.getRetrieval().getContextBudgetTokens();
                    List<SearchHit> hits = backend.search(question,
                            budget > 0 ? BenchmarkRunner.RETRIEVAL_DEPTH
                                       : properties.getRetrieval().getTopK());
                    int payloadTokens = 0;
                    int shown = 0;
                    for (SearchHit hit : hits) {
                        if (budget > 0 && payloadTokens + hit.snippetTokens() > budget) {
                            break;
                        }
                        payloadTokens += hit.snippetTokens();
                        shown++;
                        System.out.printf("%2d. %-40s score %.3f  [%d tokens]%n",
                                shown, hit.coordinate(), hit.score(), hit.snippetTokens());
                        System.out.println("    " + hit.snippet().replace("\n", "\n    "));
                    }
                    System.out.println();
                    if (budget > 0) {
                        System.out.println("context budget: " + budget + " tokens; " + shown
                                + " hits fit, using " + payloadTokens + " tokens ("
                                + tokenMeter.encodingName() + ").");
                    } else {
                        System.out.println("result payload: " + payloadTokens + " tokens ("
                                + tokenMeter.encodingName() + "); reading these hits is what the"
                                + " search costs an agent.");
                    }
                }
                case "bench" -> {
                    Path file = Path.of(args.length > 1 ? args[1] : properties.getBench().getFile());
                    List<LabelledQuery> queries = benchmarkRunner.load(file);
                    List<CorpusEntry> corpus = corpusGenerator.generate(schema, format);
                    backend.index(corpus);
                    System.out.println("schema  : " + schemaSource.describe());
                    System.out.println("corpus  : format " + format + ", " + corpus.size() + " coordinates");
                    System.out.println("bench   : " + file + ", " + queries.size() + " labelled queries");
                    System.out.println();
                    BenchmarkRunner.Result result = benchmarkRunner.run(
                            backend, queries, properties.getBench().getBudgets());
                    System.out.println(benchmarkRunner.format(result));
                }
                default -> System.out.println("Unknown command: " + command);
            }
        };
    }
}
