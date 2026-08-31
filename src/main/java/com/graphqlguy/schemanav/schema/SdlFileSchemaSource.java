package com.graphqlguy.schemanav.schema;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a schema from an SDL file on disk. The generated schema is "unexecutable":
 * it has types and descriptions but no data fetchers, which is exactly enough for
 * indexing and navigation. Execution goes through a live endpoint, never through
 * this object.
 */
public class SdlFileSchemaSource implements SchemaSource {

    private final Path path;

    public SdlFileSchemaSource(Path path) {
        this.path = path;
    }

    @Override
    public GraphQLSchema load() {
        String sdl;
        try {
            sdl = Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read the schema file at " + path.toAbsolutePath()
                    + ". Check schemanav.schema.source in application.yaml.", e);
        }
        TypeDefinitionRegistry registry = new SchemaParser().parse(sdl);
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }

    @Override
    public String describe() {
        return "SDL file " + path;
    }
}
