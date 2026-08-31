package com.graphqlguy.schemanav.retrieval;

import com.graphqlguy.schemanav.corpus.CorpusEntry;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lexical retrieval with Lucene, whose default similarity is BM25. This is the same
 * family of index that Apollo MCP Server and Grafbase build with tantivy, and the
 * ranking Hot Chocolate 16's semantic introspection uses by default: word statistics,
 * with no model anywhere. It costs nothing to build and is the baseline every
 * embedding backend has to beat.
 */
public class LuceneBm25Backend implements SearchBackend {

    private final StandardAnalyzer analyzer = new StandardAnalyzer();
    private final ByteBuffersDirectory directory = new ByteBuffersDirectory();
    private IndexSearcher searcher;

    @Override
    public void index(List<CorpusEntry> corpus) {
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            for (CorpusEntry entry : corpus) {
                Document doc = new Document();
                doc.add(new TextField("text", entry.text(), Field.Store.YES));
                doc.add(new StoredField("coordinate", entry.coordinate()));
                doc.add(new StoredField("tokens", entry.tokenCount()));
                writer.addDocument(doc);
            }
            writer.commit();
        } catch (IOException e) {
            throw new IllegalStateException("Building the Lucene index failed", e);
        }
        try {
            searcher = new IndexSearcher(DirectoryReader.open(directory));
        } catch (IOException e) {
            throw new IllegalStateException("Opening the Lucene index for search failed", e);
        }
    }

    @Override
    public List<SearchHit> search(String query, int k) {
        if (searcher == null) {
            throw new IllegalStateException("index() must run before search()");
        }
        try {
            QueryParser parser = new QueryParser("text", analyzer);
            Query parsed = parser.parse(QueryParser.escape(query));
            TopDocs top = searcher.search(parsed, k);
            List<SearchHit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : top.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                hits.add(new SearchHit(
                        doc.get("coordinate"),
                        scoreDoc.score,
                        doc.get("text"),
                        doc.getField("tokens").numericValue().intValue()));
            }
            return hits;
        } catch (Exception e) {
            throw new IllegalStateException("BM25 search failed for query: " + query, e);
        }
    }

    @Override
    public String name() {
        return "bm25 (Lucene " + org.apache.lucene.util.Version.LATEST + ")";
    }
}
