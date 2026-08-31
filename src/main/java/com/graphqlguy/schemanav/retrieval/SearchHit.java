package com.graphqlguy.schemanav.retrieval;

/**
 * One ranked result. The snippet is what an agent would actually read, and
 * snippetTokens is what that reading costs, measured with the fixed yardstick;
 * the two travel together everywhere in this project.
 */
public record SearchHit(String coordinate, double score, String snippet, int snippetTokens) {
}
