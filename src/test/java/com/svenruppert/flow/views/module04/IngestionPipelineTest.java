package com.svenruppert.flow.views.module04;

import com.svenruppert.flow.views.module02.DefaultSimilarity;
import com.svenruppert.flow.views.module02.InMemoryVectorStore;
import com.svenruppert.flow.views.module02.VectorStore;
import com.svenruppert.flow.views.module03.Document;
import com.svenruppert.flow.views.module03.FixedSizeChunker;
import com.svenruppert.flow.views.module04.testutil.StubLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestionPipelineTest {

    private LuceneBM25KeywordIndex keywordIndex;
    private VectorStore vectorStore;

    @BeforeEach
    void setUp() throws IOException {
        keywordIndex = new LuceneBM25KeywordIndex();
        vectorStore = new InMemoryVectorStore(new DefaultSimilarity());
    }

    @AfterEach
    void tearDown() throws IOException {
        keywordIndex.close();
    }

    @Test
    @DisplayName("ingest stores each chunk in both vector and keyword index")
    void ingest_storesChunksInBothVectorStoreAndKeywordIndex() throws IOException {
        // Two fixed chunks of length 10 from a 20-character document.
        String content = "abcdefghijklmnopqrst";
        Document doc = new Document(content, Paths.get("notes.txt"),
                Map.of(Document.CONTENT_TYPE, "text/plain"));

        StubLlmClient llm = new StubLlmClient()
                .withEmbed("abcdefghij", new float[]{1f, 0f})
                .withEmbed("klmnopqrst", new float[]{0f, 1f});

        IngestionPipeline pipeline = new IngestionPipeline(
                llm, "stub-model", new FixedSizeChunker(10),
                vectorStore, keywordIndex);
        pipeline.ingest(doc);

        assertEquals(2, vectorStore.size());
        assertEquals(2, keywordIndex.size());
        assertEquals(2, pipeline.chunkRegistry().size());

        assertEquals(true, pipeline.chunkRegistry().containsKey("notes.txt::0"));
        assertEquals(true, pipeline.chunkRegistry().containsKey("notes.txt::1"));
    }

    @Test
    @DisplayName("clear empties both stores and the registry")
    void clear_emptiesBoth() throws IOException {
        Document doc = new Document("alpha beta",
                Paths.get("snippet.txt"),
                Map.of(Document.CONTENT_TYPE, "text/plain"));

        StubLlmClient llm = new StubLlmClient()
                .withEmbed("alpha beta", new float[]{1f, 0f});

        IngestionPipeline pipeline = new IngestionPipeline(
                llm, "stub-model", new FixedSizeChunker(100),
                vectorStore, keywordIndex);
        pipeline.ingest(doc);
        pipeline.clear();

        assertEquals(0, vectorStore.size());
        assertEquals(0, keywordIndex.size());
        assertEquals(0, pipeline.chunkRegistry().size());
    }
}
