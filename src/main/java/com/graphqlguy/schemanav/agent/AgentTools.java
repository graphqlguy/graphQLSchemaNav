package com.graphqlguy.schemanav.agent;

import com.graphqlguy.schemanav.config.SchemaNavProperties;
import com.graphqlguy.schemanav.retrieval.SearchBackend;
import com.graphqlguy.schemanav.retrieval.SearchHit;
import com.graphqlguy.schemanav.tokens.TokenMeter;
import graphql.ExecutionInput;
import graphql.ParseAndValidate;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnionType;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The three tools of the search/introspect/execute pattern, the same triad Apollo
 * MCP Server and Grafbase expose, built here on this project's own retrieval layer.
 *
 * The model never sees the schema. It sees only what these tools return, and every
 * return value is token-metered and printed, so a reader watching a run can see
 * precisely how much schema knowledge the agent bought and what it paid.
 */
public class AgentTools {

    private final GraphQLSchema schema;
    private final SearchBackend backend;
    private final TokenMeter tokenMeter;
    private final TokenLedger ledger;
    private final SchemaNavProperties properties;

    public AgentTools(GraphQLSchema schema, SearchBackend backend, TokenMeter tokenMeter,
                      TokenLedger ledger, SchemaNavProperties properties) {
        this.schema = schema;
        this.backend = backend;
        this.tokenMeter = tokenMeter;
        this.ledger = ledger;
        this.properties = properties;
    }

    @Tool(description = "Search the GraphQL schema for field coordinates relevant to a"
            + " natural-language question. Returns ranked Type.field coordinates with short"
            + " descriptions. Always start here; never guess type or field names.")
    public String searchSchema(
            @ToolParam(description = "The question or capability to look for, in plain words")
            String question) {
        int budget = properties.getRetrieval().getContextBudgetTokens();
        List<SearchHit> hits = backend.search(question, 100);
        StringBuilder out = new StringBuilder();
        int used = 0;
        int shown = 0;
        for (SearchHit hit : hits) {
            if (budget > 0 && used + hit.snippetTokens() > budget) {
                break;
            }
            if (budget <= 0 && shown >= properties.getRetrieval().getTopK()) {
                break;
            }
            used += hit.snippetTokens();
            shown++;
            out.append(hit.coordinate()).append(": ").append(hit.snippet()).append('\n');
        }
        return traced("searchSchema(\"" + question + "\")", out.toString());
    }

    @Tool(description = "Show the full definition of one schema type: its fields with"
            + " arguments, return types, and descriptions. Use after searchSchema, on the"
            + " owner types of the coordinates you plan to use, before writing any operation.")
    public String introspectType(
            @ToolParam(description = "The exact type name, for example Repository")
            String typeName) {
        GraphQLNamedType type = (GraphQLNamedType) schema.getType(typeName);
        String body = type == null
                ? "No type named '" + typeName + "' exists in this schema. Use searchSchema"
                  + " to find real coordinates; owner types appear before the dot."
                : renderType(type);
        return traced("introspectType(\"" + typeName + "\")", body);
    }

    @Tool(description = "Validate a GraphQL operation against the schema, and run it when a"
            + " live endpoint is configured. Returns validation errors to fix, or the result."
            + " Always check your operation here before answering.")
    public String executeGraphql(
            @ToolParam(description = "The complete GraphQL operation document")
            String query) {
        ParseAndValidateResult result = ParseAndValidate.parseAndValidate(
                schema, ExecutionInput.newExecutionInput(query).build());
        String body;
        if (result.isFailure()) {
            body = "Validation failed:\n" + result.getErrors().stream()
                    .map(error -> "- " + error.getMessage())
                    .collect(Collectors.joining("\n"))
                    + "\nFix hint: call introspectType on every type named in the errors"
                    + " before retrying; never guess field or input names.";
        } else if (properties.getExecute().getEndpoint().isBlank()) {
            body = "The operation parses and validates against the schema. No live endpoint"
                    + " is configured (schemanav.execute.endpoint), so it was not executed.";
        } else {
            body = String.valueOf(RestClient.create()
                    .post()
                    .uri(properties.getExecute().getEndpoint())
                    .header("Content-Type", "application/json")
                    .body(Map.of("query", query))
                    .retrieve()
                    .body(String.class));
        }
        return traced("executeGraphql(...)", body);
    }

    /** Prints the call and its token-metered result; charges the ledger. */
    private String traced(String call, String body) {
        int tokens = tokenMeter.count(body);
        ledger.addToolPayload(tokens);
        System.out.println("  tool " + call + " -> " + tokenMeter.footer(body));
        String indented = body.strip().replace("\n", "\n  |  ");
        System.out.println("  |  " + indented);
        return body;
    }

    private String renderType(GraphQLNamedType type) {
        StringBuilder out = new StringBuilder();
        if (type instanceof GraphQLFieldsContainer container) {
            out.append("type ").append(container.getName()).append(" {\n");
            for (GraphQLFieldDefinition field : container.getFieldDefinitions()) {
                if (field.getDescription() != null) {
                    out.append("  # ").append(field.getDescription().strip()
                            .replace("\n", " ")).append('\n');
                }
                String args = field.getArguments().isEmpty() ? "" :
                        "(" + field.getArguments().stream()
                                .map(a -> a.getName() + ": " + GraphQLTypeUtil.simplePrint(a.getType()))
                                .collect(Collectors.joining(", ")) + ")";
                out.append("  ").append(field.getName()).append(args).append(": ")
                        .append(GraphQLTypeUtil.simplePrint(field.getType())).append('\n');
            }
            out.append("}");
        } else if (type instanceof GraphQLEnumType enumType) {
            out.append("enum ").append(enumType.getName()).append(" { ")
                    .append(enumType.getValues().stream()
                            .map(v -> v.getName()).collect(Collectors.joining(" ")))
                    .append(" }");
        } else if (type instanceof GraphQLInputObjectType inputType) {
            out.append("input ").append(inputType.getName()).append(" {\n");
            for (GraphQLInputObjectField field : inputType.getFieldDefinitions()) {
                out.append("  ").append(field.getName()).append(": ")
                        .append(GraphQLTypeUtil.simplePrint(field.getType())).append('\n');
            }
            out.append("}");
        } else if (type instanceof GraphQLUnionType unionType) {
            out.append("union ").append(unionType.getName()).append(" = ")
                    .append(unionType.getTypes().stream()
                            .map(GraphQLNamedType::getName)
                            .collect(Collectors.joining(" | ")));
        } else {
            out.append(type.getName()).append(" (scalar)");
        }
        return out.toString();
    }
}
