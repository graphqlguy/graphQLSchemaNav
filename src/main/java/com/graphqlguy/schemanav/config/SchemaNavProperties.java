package com.graphqlguy.schemanav.config;

import com.graphqlguy.schemanav.corpus.CorpusFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every pluggable choice in the instrument lives here, so an experiment is a
 * one-line change in application.yaml (or a --schemanav.* flag on the command line):
 * which schema, which corpus format, which retrieval backend, which tokenizer yardstick.
 */
@ConfigurationProperties(prefix = "schemanav")
public class SchemaNavProperties {

    private final Schema schema = new Schema();
    private final Corpus corpus = new Corpus();
    private final Retrieval retrieval = new Retrieval();
    private final Tokens tokens = new Tokens();
    private final Bench bench = new Bench();

    public Schema getSchema() { return schema; }
    public Corpus getCorpus() { return corpus; }
    public Retrieval getRetrieval() { return retrieval; }
    public Tokens getTokens() { return tokens; }
    public Bench getBench() { return bench; }

    public static class Schema {
        /**
         * Either a path to an SDL file (ends in .graphql or .graphqls)
         * or an http(s) URL of a GraphQL endpoint to introspect.
         */
        private String source = "schemas/moviedb.graphqls";
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class Corpus {
        /** How each schema coordinate is rendered into indexable text. */
        private CorpusFormat format = CorpusFormat.GLOSS;
        public CorpusFormat getFormat() { return format; }
        public void setFormat(CorpusFormat format) { this.format = format; }
    }

    public static class Retrieval {
        /** bm25 or embedding. */
        private String backend = "bm25";
        private int topK = 10;
        /**
         * Instruction-tuned embedding models embed questions and corpus text differently.
         * These prefixes reproduce sentence-transformers' prompt_name="query" and
         * prompt_name="document": each is prepended verbatim before embedding. The
         * defaults are the fine-tuned GraphQL model's own configuration (documents get
         * no prefix). An index built with the wrong prompt silently underperforms.
         */
        private String queryPrefix =
                "Instruct: Given a web search query, retrieve relevant passages that answer the query\nQuery:";
        private String documentPrefix = "";
        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
        public String getQueryPrefix() { return queryPrefix; }
        public void setQueryPrefix(String queryPrefix) { this.queryPrefix = queryPrefix; }
        public String getDocumentPrefix() { return documentPrefix; }
        public void setDocumentPrefix(String documentPrefix) { this.documentPrefix = documentPrefix; }
    }

    public static class Tokens {
        /**
         * The fixed tokenizer yardstick. Token counts differ per model family, so the
         * instrument pins one encoding and uses it consistently; comparisons need a
         * consistent ruler, not an absolute one.
         */
        private String encoding = "O200K_BASE";
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
    }

    public static class Bench {
        /** Tab-separated file: natural-language question, expected Type.field coordinate. */
        private String file = "benchmarks/moviedb-sample.tsv";
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
    }
}
