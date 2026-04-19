package com.svenruppert.flow.views.module02;

import org.junit.jupiter.api.DisplayName;

@DisplayName("InMemoryVectorStore contract")
class InMemoryVectorStoreTest extends VectorStoreContractTest {

    @Override
    protected VectorStore createStore() {
        return new InMemoryVectorStore(new DefaultSimilarity());
    }
}
