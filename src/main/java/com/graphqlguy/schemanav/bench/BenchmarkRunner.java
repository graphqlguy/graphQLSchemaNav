package com.graphqlguy.schemanav.bench;

import com.graphqlguy.schemanav.retrieval.SearchBackend;
import com.graphqlguy.schemanav.retrieval.SearchHit;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The measurement loop, and the point of the whole instrument: accuracy and token
 * cost reported side by side, so every backend, model, and corpus format lands as a
 * point on an accuracy-versus-cost plane.
 *
 * Metrics, in plain words:
 * - recall@k: what fraction of a question's expected coordinates appear in the top k
 *   results, averaged over questions. This mirrors the AI Working Group evaluation
 *   suite's definition (fraction of targets in top-K, macro-averaged).
 * - tokens@k: what reading the top k snippets costs, measured with the fixed
 *   yardstick. The price paid for the recall.
 * - The context-budget sweep: instead of "top k results", the agent gets "as many
 *   results as fit in B tokens". Small budgets on any schema reproduce the pressure
 *   a huge schema puts on a normal context window: big schema, small brain, as a
 *   dial. Each budget row reports the recall achievable within it.
 */
@Component
public class BenchmarkRunner {

    /** How deep the ranked list goes; every metric is computed within this window. */
    public static final int RETRIEVAL_DEPTH = 100;

    private static final int[] REPORTED_K = {1, 5, 10, 20, 50};

    public record BudgetPoint(int budgetTokens, double recall, double avgTokensUsed,
                              double avgHitsRead) {
    }

    public record Result(String backend, int queries, int targets, double[] recallAtK,
                         double avgTokensAt10, double avgTokensAt50,
                         List<BudgetPoint> budgetSweep) {
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
                                    + " question and coordinates: " + line);
                        }
                        List<String> coordinates = Arrays.stream(parts[1].split(","))
                                .map(String::strip)
                                .filter(s -> !s.isEmpty())
                                .toList();
                        return new LabelledQuery(parts[0].strip(), coordinates);
                    })
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the benchmark file " + file, e);
        }
    }

    public Result run(SearchBackend backend, List<LabelledQuery> queries,
                      List<Integer> budgets) {
        double[] recallSums = new double[REPORTED_K.length];
        double tokensAt10Sum = 0;
        double tokensAt50Sum = 0;
        double[] budgetRecallSums = new double[budgets.size()];
        double[] budgetTokensSums = new double[budgets.size()];
        double[] budgetHitsSums = new double[budgets.size()];
        int totalTargets = 0;

        for (LabelledQuery query : queries) {
            List<SearchHit> hits = backend.search(query.question(), RETRIEVAL_DEPTH);
            Set<String> expected = Set.copyOf(query.expectedCoordinates());
            totalTargets += expected.size();

            // Cumulative found-fraction and token cost along the ranked list.
            for (int ki = 0; ki < REPORTED_K.length; ki++) {
                int k = REPORTED_K[ki];
                recallSums[ki] += foundFraction(hits, expected, k, Integer.MAX_VALUE);
            }
            tokensAt10Sum += tokensOfTop(hits, 10);
            tokensAt50Sum += tokensOfTop(hits, 50);

            for (int bi = 0; bi < budgets.size(); bi++) {
                int budget = budgets.get(bi);
                int used = 0;
                int read = 0;
                for (SearchHit hit : hits) {
                    if (used + hit.snippetTokens() > budget) {
                        break;
                    }
                    used += hit.snippetTokens();
                    read++;
                }
                budgetRecallSums[bi] += foundFraction(hits, expected, read, Integer.MAX_VALUE);
                budgetTokensSums[bi] += used;
                budgetHitsSums[bi] += read;
            }
        }

        int n = queries.size();
        double[] recallAtK = new double[REPORTED_K.length];
        for (int ki = 0; ki < REPORTED_K.length; ki++) {
            recallAtK[ki] = recallSums[ki] / n;
        }
        List<BudgetPoint> sweep = new ArrayList<>();
        for (int bi = 0; bi < budgets.size(); bi++) {
            sweep.add(new BudgetPoint(budgets.get(bi), budgetRecallSums[bi] / n,
                    budgetTokensSums[bi] / n, budgetHitsSums[bi] / n));
        }
        return new Result(backend.name(), n, totalTargets, recallAtK,
                tokensAt10Sum / n, tokensAt50Sum / n, sweep);
    }

    private double foundFraction(List<SearchHit> hits, Set<String> expected, int k,
                                 int tokenCap) {
        if (expected.isEmpty()) {
            return 0;
        }
        Set<String> top = hits.stream().limit(k)
                .map(SearchHit::coordinate)
                .collect(Collectors.toSet());
        long found = expected.stream().filter(top::contains).count();
        return (double) found / expected.size();
    }

    private long tokensOfTop(List<SearchHit> hits, int k) {
        return hits.stream().limit(k).mapToLong(SearchHit::snippetTokens).sum();
    }

    public String format(Result r) {
        StringBuilder out = new StringBuilder();
        out.append("""
                backend          : %s
                queries          : %d (%d expected coordinates)
                recall@1         : %.3f
                recall@5         : %.3f
                recall@10        : %.3f
                recall@20        : %.3f
                recall@50        : %.3f
                tokens@10        : %.0f
                tokens@50        : %.0f
                """.formatted(r.backend(), r.queries(), r.targets(),
                r.recallAtK()[0], r.recallAtK()[1], r.recallAtK()[2],
                r.recallAtK()[3], r.recallAtK()[4],
                r.avgTokensAt10(), r.avgTokensAt50()));
        out.append("\ncontext-budget sweep (recall achievable within B tokens of results):\n");
        out.append("  budget   recall   tokens used   hits read\n");
        for (BudgetPoint p : r.budgetSweep()) {
            out.append("  %6d   %.3f    %8.0f     %6.1f%n"
                    .formatted(p.budgetTokens(), p.recall(), p.avgTokensUsed(), p.avgHitsRead()));
        }
        return out.toString();
    }
}
