package com.graphqlguy.schemanav.corpus;

import com.graphqlguy.schemanav.tokens.TokenMeter;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the schema and emits one corpus entry per field coordinate, in the configured
 * format. Object and interface types are covered (interfaces matter on real schemas:
 * GitHub's leans on Node, Actor, and friends), including the Query and Mutation
 * roots, whose fields are the operations an agent ultimately wants to find.
 * Introspection's own __-prefixed types are skipped. This is the same field space
 * the AI Working Group's evaluation snapshot indexes.
 */
@Component
public class CorpusGenerator {

    private final TokenMeter tokenMeter;

    public CorpusGenerator(TokenMeter tokenMeter) {
        this.tokenMeter = tokenMeter;
    }

    public List<CorpusEntry> generate(GraphQLSchema schema, CorpusFormat format) {
        List<CorpusEntry> entries = new ArrayList<>();
        for (GraphQLNamedType type : schema.getAllTypesAsList()) {
            boolean fieldsContainer = type instanceof GraphQLObjectType
                    || type instanceof GraphQLInterfaceType;
            if (!fieldsContainer || type.getName().startsWith("__")) {
                continue;
            }
            GraphQLFieldsContainer owner = (GraphQLFieldsContainer) type;
            for (GraphQLFieldDefinition field : owner.getFieldDefinitions()) {
                String coordinate = owner.getName() + "." + field.getName();
                String text = render(owner, field, coordinate, format);
                entries.add(new CorpusEntry(
                        coordinate, owner.getName(), text, tokenMeter.count(text)));
            }
        }
        return entries;
    }

    private String render(GraphQLFieldsContainer owner, GraphQLFieldDefinition field,
                          String coordinate, CorpusFormat format) {
        String returnType = GraphQLTypeUtil.simplePrint(field.getType());
        String description = field.getDescription() == null ? "" : field.getDescription().strip();
        return switch (format) {
            case RAW -> coordinate;
            case GLOSS -> {
                StringBuilder gloss = new StringBuilder();
                gloss.append("GraphQL field ").append(coordinate).append(". Owner type: ")
                        .append(owner.getName()).append(". Returns: ").append(returnType).append(".");
                if (!description.isEmpty()) {
                    gloss.append(" ").append(description);
                }
                yield gloss.toString();
            }
            case SDL -> {
                StringBuilder sdl = new StringBuilder();
                if (!description.isEmpty()) {
                    sdl.append("\"\"\"").append(description).append("\"\"\"\n");
                }
                sdl.append(field.getName());
                if (!field.getArguments().isEmpty()) {
                    List<String> args = field.getArguments().stream()
                            .map(a -> a.getName() + ": " + GraphQLTypeUtil.simplePrint(a.getType()))
                            .toList();
                    sdl.append("(").append(String.join(", ", args)).append(")");
                }
                sdl.append(": ").append(returnType)
                        .append("  # on type ").append(owner.getName());
                yield sdl.toString();
            }
        };
    }
}
