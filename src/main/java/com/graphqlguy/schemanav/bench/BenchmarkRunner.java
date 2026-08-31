package com.graphqlguy.schemanav.bench;

import com.graphqlguy.schemanav.retrieval.SearchBackend;
import com.graphqlguy.schemanav.retrieval.SearchHit;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The measurement loop, and the point of the whole instrument: accuracy and token
 * cost reported side by side, so every backend, model, and corpus format lands as a
 * point on an accuracy-versus-cost plane.
 *
 * Metrics, in plain words:
 * - recall@k: in what fraction of questions the expected coordinate appears anywhere
 *   in the top k results. recall@1 means "the first hit was right".
 * - MRR@k (mean reciprocal rank): 1/rank of the expected coordinate, averaged over
 *   questions (0 when it is absent from the top k). Rewards putting the right answer
 *   near the top, not merely somewhere in the list.
 * - tokens/query: what reading the top k snippets would cost an agent, measured with
 *   the fixed yardstick. This is the price paid for the recall.
 */
@Component
public class BenchmarkRunner {

    public record Result(String backend, int queries, double recallAt1, double recallAt5,
                         double recallAt10, double mrrAt10, double avgTokensPerQuery) {
    }

    public List<LabelledQuery> load(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> {
                        String[] parts = line.split("\t");
                        if (parts.length != 2) {
                            throw new IllegalStateException(
                                    "Each benchmark line needs exactly one tab between"
                                    + " question and coordinate: " + line);
                        }
                        return new LabelledQuery(parts[0].strip(), parts[1].strip());
                    })
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the benchmark file " + file, e);
        }
    }

    public Result run(SearchBackend backend, List<LabelledQuery> queries, int k) {
        int hitAt1 = 0;
        int hitAt5 = 0;
        int hitAt10 = 0;
        double reciprocalRankSum = 0;
        long tokenSum = 0;

        for (LabelledQuery query : queries) {
            List<SearchHit> hits = backend.search(query.question(), k);
            tokenSum += hits.stream().mapToLong(SearchHit::snippetTokens).sum();
            int rank = rankOf(hits, query.expectedCoordinate());
            if (rank > 0) {
                reciprocalRankSum += 1.0 / rank;
                if (rank <= 1) hitAt1++;
                if (rank <= 5) hitAt5++;
                if (rank <= 10) hitAt10++;
            }
        }
        int n = queries.size();
        return new Result(backend.name(), n,
                (double) hitAt1 / n, (double) hitAt5 / n, (double) hitAt10 / n,
                reciprocalRankSum / n, (double) tokenSum / n);
    }

    private int rankOf(List<SearchHit> hits, String expected) {
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).coordinate().equals(expected)) {
                return i + 1;
            }
        }
        return 0;
    }

    public String format(Result r) {
        return """
                backend          : %s
                queries          : %d
                recall@1         : %.3f
                recall@5         : %.3f
                recall@10        : %.3f
                MRR@10           : %.3f
                tokens/query     : %.0f (top-10 snippets, fixed yardstick)"""
                .formatted(r.backend(), r.queries(), r.recallAt1(), r.recallAt5(),
                        r.recallAt10(), r.mrrAt10(), r.avgTokensPerQuery());
    }
}
