package com.graphqlguy.schemanav.corpus;

import com.graphqlguy.schemanav.tokens.TokenMeter;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the schema and emits one corpus entry per field coordinate, in the configured
 * format. Object types are covered, including the Query and Mutation roots, whose
 * fields are the operations an agent ultimately wants to find. Introspection's own
 * __-prefixed types are skipped.
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
            if (!(type instanceof GraphQLObjectType objectType)) {
                continue;
            }
            if (objectType.getName().startsWith("__")) {
                continue;
            }
            for (GraphQLFieldDefinition field : objectType.getFieldDefinitions()) {
                String coordinate = objectType.getName() + "." + field.getName();
                String text = render(objectType, field, coordinate, format);
                entries.add(new CorpusEntry(
                        coordinate, objectType.getName(), text, tokenMeter.count(text)));
            }
        }
        return entries;
    }

    private String render(GraphQLObjectType owner, GraphQLFieldDefinition field,
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
