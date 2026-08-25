package com.graphqlguy.schemanav.retrieval;

import com.graphqlguy.schemanav.config.SchemaNavProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Chooses the retrieval backend from one property (schemanav.retrieval.backend).
 * bm25 works with no external process at all. embedding needs a running Ollama
 * with the configured embedding model pulled; the error message says so instead
 * of failing obscurely later.
 */
@Configuration
public class SearchBackendFactory {

    @Bean
    public SearchBackend searchBackend(SchemaNavProperties properties,
                                       ObjectProvider<EmbeddingModel> embeddingModels,
                                       Environment environment) {
        String backend = properties.getRetrieval().getBackend();
        return switch (backend) {
            case "bm25" -> new LuceneBm25Backend();
            case "embedding" -> {
                EmbeddingModel model = embeddingModels.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "schemanav.retrieval.backend=embedding needs an embedding model."
                            + " Start Ollama and check spring.ai.ollama.* in application.yaml.");
                }
                String modelName = environment.getProperty(
                        "spring.ai.ollama.embedding.options.model", "ollama default");
                yield new EmbeddingBackend(model, modelName,
                        properties.getRetrieval().getQueryPrefix(),
                        properties.getRetrieval().getDocumentPrefix());
            }
            default -> throw new IllegalStateException(
                    "Unknown schemanav.retrieval.backend: " + backend
                    + " (expected bm25 or embedding)");
        };
    }
}
