package com.graphqlguy.schemanav.corpus;

/**
 * How a schema coordinate is rendered into the text the index actually searches.
 * The published measurements show this choice matters as much as the retrieval model:
 * on the GitHub schema, raw coordinates put the right answer at P95 rank 404, while
 * GLOSS and SDL bring P95 down to around 40.
 */
public enum CorpusFormat {

    /** Just the coordinate: "Movie.title". The worst performer; kept for the comparison. */
    RAW,

    /** One neutral sentence per coordinate: name, owner type, return type, description. */
    GLOSS,

    /** A small SDL snippet: the field definition with its description, in schema syntax. */
    SDL
}
