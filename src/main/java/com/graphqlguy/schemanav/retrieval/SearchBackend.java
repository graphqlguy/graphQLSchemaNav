package com.graphqlguy.schemanav.retrieval;

import com.graphqlguy.schemanav.corpus.CorpusEntry;

import java.util.List;

/**
 * The retrieval contract both backends implement. The benchmark harness, the search
 * command, and the agent tools depend only on this interface, which is what makes
 * "swap the backend" a one-property experiment.
 */
public interface SearchBackend {

    /** Builds (or rebuilds) the index over the given corpus. */
    void index(List<CorpusEntry> corpus);

    /** Returns the top-k entries for the query, best first. */
    List<SearchHit> search(String query, int k);

    String name();
}
