package com.graphqlguy.schemanav.schema;

import com.graphqlguy.schemanav.config.SchemaNavProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Chooses the schema source from one property. A value starting with http:// or
 * https:// means "introspect this endpoint"; anything else is a path to an SDL file.
 */
@Configuration
public class SchemaSourceFactory {

    @Bean
    public SchemaSource schemaSource(SchemaNavProperties properties) {
        String source = properties.getSchema().getSource();
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return new IntrospectionSchemaSource(source);
        }
        return new SdlFileSchemaSource(Path.of(source));
    }
}
