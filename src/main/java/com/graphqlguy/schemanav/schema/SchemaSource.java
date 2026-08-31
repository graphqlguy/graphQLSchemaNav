package com.graphqlguy.schemanav.schema;

import graphql.schema.GraphQLSchema;

/**
 * The single abstraction everything downstream reads from. The corpus generator, both
 * retrieval backends, the benchmark harness, and the agent tools all consume a
 * GraphQLSchema and stay unaware of where it came from, which is what makes swapping
 * schemas a one-line configuration change.
 */
public interface SchemaSource {

    GraphQLSchema load();

    /** A short human-readable label for logs and result footers. */
    String describe();
}
