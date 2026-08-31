package com.graphqlguy.schemanav.corpus;

/**
 * One searchable unit: a schema coordinate and the text that represents it in the index.
 *
 * @param coordinate  the schema coordinate, like "Movie.title"
 * @param ownerType   the type that owns the field, like "Movie"
 * @param text        the indexable rendering of this coordinate (depends on CorpusFormat)
 * @param tokenCount  how many tokens the text costs, measured with the fixed yardstick
 */
public record CorpusEntry(String coordinate, String ownerType, String text, int tokenCount) {
}
