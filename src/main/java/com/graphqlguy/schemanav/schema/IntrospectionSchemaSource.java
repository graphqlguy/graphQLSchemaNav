package com.graphqlguy.schemanav.schema;

import graphql.introspection.IntrospectionQuery;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.Document;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Loads a schema by sending the standard introspection query to a live GraphQL
 * endpoint. Useful for "point the instrument at your own service"; for large public
 * schemas that require authentication (GitHub's, for example), prefer downloading the
 * published SDL snapshot instead, see scripts/fetch-github-schema.sh.
 */
public class IntrospectionSchemaSource implements SchemaSource {

    private final String endpoint;

    public IntrospectionSchemaSource(String endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    @SuppressWarnings("unchecked")
    public GraphQLSchema load() {
        Map<String, Object> response = RestClient.create()
                .post()
                .uri(endpoint)
                .header("Content-Type", "application/json")
                .body(Map.of("query", IntrospectionQuery.INTROSPECTION_QUERY))
                .retrieve()
                .body(Map.class);
        if (response == null || response.get("data") == null) {
            throw new IllegalStateException(
                    "The introspection query against " + endpoint
                    + " did not return a data payload. The endpoint may be down or"
                    + " may have introspection disabled.");
        }
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        Document document = new IntrospectionResultToSchema().createSchemaDefinition(data);
        TypeDefinitionRegistry registry = new SchemaParser().buildRegistry(document);
        return UnExecutableSchemaGenerator.makeUnExecutableSchema(registry);
    }

    @Override
    public String describe() {
        return "introspection of " + endpoint;
    }
}
