package com.graphqlguy.schemanav.retrieval;

import com.graphqlguy.schemanav.corpus.CorpusEntry;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Semantic retrieval: every corpus entry becomes a vector (a point in a space where
 * nearby means similar), the query becomes a vector too, and ranking is cosine
 * similarity between them. The embedding model is whatever Spring AI is configured
 * with, which for this project means whatever Ollama serves; swapping models is a
 * configuration change, never a code change.
 *
 * The similarity scan is a deliberate plain loop over float arrays: a schema corpus
 * is tens of thousands of entries at most, that fits comfortably in memory, and
 * keeping the mechanism visible beats hiding it behind a vector store.
 *
 * Instruction-tuned embedding models embed questions and corpus text differently.
 * Ollama has no equivalent of sentence-transformers' prompt_name, so the prompts are
 * reproduced here by prepending the configured prefixes verbatim: the query prefix on
 * every question, the document prefix (empty for the GraphQL fine-tune) on every
 * corpus entry. Skipping the query prefix silently degrades ranking quality.
 */
public class EmbeddingBackend implements SearchBackend {

    private final EmbeddingModel embeddingModel;
    private final String modelDescription;
    private final String queryPrefix;
    private final String documentPrefix;
    private final List<CorpusEntry> entries = new ArrayList<>();
    private final List<float[]> vectors = new ArrayList<>();

    public EmbeddingBackend(EmbeddingModel embeddingModel, String modelDescription,
                            String queryPrefix, String documentPrefix) {
        this.embeddingModel = embeddingModel;
        this.modelDescription = modelDescription;
        this.queryPrefix = queryPrefix;
        this.documentPrefix = documentPrefix;
    }

    @Override
    public void index(List<CorpusEntry> corpus) {
        entries.clear();
        vectors.clear();
        for (CorpusEntry entry : corpus) {
            entries.add(entry);
            vectors.add(embeddingModel.embed(documentPrefix + entry.text()));
        }
    }

    @Override
    public List<SearchHit> search(String query, int k) {
        if (vectors.isEmpty()) {
            throw new IllegalStateException("index() must run before search()");
        }
        float[] queryVector = embeddingModel.embed(queryPrefix + query);
        List<SearchHit> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            CorpusEntry entry = entries.get(i);
            double score = cosine(queryVector, vectors.get(i));
            scored.add(new SearchHit(entry.coordinate(), score, entry.text(), entry.tokenCount()));
        }
        scored.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        return scored.subList(0, Math.min(k, scored.size()));
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public String name() {
        return "embedding (" + modelDescription + ")";
    }
}
