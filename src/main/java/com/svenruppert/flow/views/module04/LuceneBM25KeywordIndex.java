package com.svenruppert.flow.views.module04;

import com.svenruppert.dependencies.core.logger.HasLogger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link KeywordIndex} backed by an embedded Lucene index with BM25
 * scoring.
 *
 * <h2>BM25 in one paragraph</h2>
 * BM25 is a term-frequency scoring function with two refinements over
 * naive TF: term frequency <em>saturates</em> (the tenth occurrence of
 * a term contributes less than the first), and document length is
 * normalised (a long document does not rank higher just because it
 * repeats the term). Rare terms carry more weight through the IDF
 * factor. Lucene's {@link BM25Similarity} ships the default parameters
 * (k1 = 1.2, b = 0.75) that are a reasonable starting point for most
 * corpora.
 *
 * <h2>Implementation notes</h2>
 * The index lives entirely in memory via {@link ByteBuffersDirectory}
 * -- we do not need on-disk persistence for the workshop. A
 * {@link StandardAnalyzer} drives tokenisation for both ingestion and
 * queries; queries are analysed the same way as stored text (critical,
 * otherwise casing and punctuation desynchronise the two sides).
 *
 * <p>Queries are assembled as a simple OR of term queries, one per
 * analysed token. That keeps this module free of the
 * {@code lucene-queryparser} artefact and its own quirks -- for this
 * workshop a BOOL-OR is equivalent to the natural-language queries we
 * throw at it.
 */
public final class LuceneBM25KeywordIndex implements KeywordIndex, HasLogger {

    private static final String FIELD_ID = "id";
    private static final String FIELD_TEXT = "text";

    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;

    public LuceneBM25KeywordIndex() throws IOException {
        this.directory = new ByteBuffersDirectory();
        this.analyzer = new StandardAnalyzer();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer)
                .setSimilarity(new BM25Similarity());
        this.writer = new IndexWriter(directory, cfg);
        // Commit once so a reader can be opened immediately even on an
        // otherwise empty index.
        this.writer.commit();
    }

    @Override
    public void add(String id, String text) throws IOException {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        doc.add(new TextField(FIELD_TEXT, text, Field.Store.NO));
        writer.addDocument(doc);
    }

    @Override
    public List<KeywordSearchResult> search(String query, int k) throws IOException {
        Objects.requireNonNull(query, "query");
        if (k <= 0) return List.of();

        Query lucene = buildQuery(query);
        if (lucene == null) return List.of();

        writer.commit();
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            if (reader.numDocs() == 0) return List.of();
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());
            TopDocs hits = searcher.search(lucene, k);
            StoredFields stored = searcher.storedFields();
            List<KeywordSearchResult> out = new ArrayList<>(hits.scoreDocs.length);
            for (ScoreDoc sd : hits.scoreDocs) {
                Document doc = stored.document(sd.doc);
                String id = doc.get(FIELD_ID);
                if (id != null) out.add(new KeywordSearchResult(id, sd.score));
            }
            return List.copyOf(out);
        }
    }

    /**
     * Tokenises the query through the same {@link StandardAnalyzer} the
     * index used, then combines the tokens as an OR of {@link TermQuery}.
     * Returns {@code null} if the query analyses to no terms (all
     * tokens were stop-words, say).
     */
    private Query buildQuery(String query) throws IOException {
        BooleanQuery.Builder bq = new BooleanQuery.Builder();
        int terms = 0;
        try (TokenStream ts = analyzer.tokenStream(FIELD_TEXT, query)) {
            CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                bq.add(new TermQuery(new Term(FIELD_TEXT, term.toString())),
                        BooleanClause.Occur.SHOULD);
                terms++;
            }
            ts.end();
        }
        return terms == 0 ? null : bq.build();
    }

    @Override
    public int size() throws IOException {
        writer.commit();
        try (DirectoryReader reader = DirectoryReader.open(writer)) {
            return reader.numDocs();
        }
    }

    @Override
    public void clear() throws IOException {
        writer.deleteAll();
        writer.commit();
    }

    @Override
    public void close() throws IOException {
        try {
            writer.close();
        } finally {
            directory.close();
            analyzer.close();
        }
    }
}
