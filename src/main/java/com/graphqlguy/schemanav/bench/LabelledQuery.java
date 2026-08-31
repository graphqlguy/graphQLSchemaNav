package com.graphqlguy.schemanav.bench;

/**
 * One benchmark row: a natural-language question and the schema coordinate a correct
 * retrieval should surface. Stored as tab-separated lines so the file needs nothing
 * beyond a text editor.
 */
public record LabelledQuery(String question, String expectedCoordinate) {
}
