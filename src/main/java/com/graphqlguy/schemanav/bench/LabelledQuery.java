package com.graphqlguy.schemanav.bench;

import java.util.List;

/**
 * One benchmark row: a natural-language question and the schema coordinates a correct
 * retrieval should surface (real tasks usually need several fields, so the ground
 * truth is a list). Stored as tab-separated lines with comma-separated coordinates,
 * so the file needs nothing beyond a text editor.
 */
public record LabelledQuery(String question, List<String> expectedCoordinates) {
}
