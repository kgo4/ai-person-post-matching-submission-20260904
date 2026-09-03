package com.example.matching.service.rag;

import com.example.matching.service.rag.impl.RagContextServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RAG 检索只读事务")
class RagRetrievalReadOnlyTransactionTest {

    @Test
    @DisplayName("retrieveContext 使用只读事务")
    void retrieveContext_runsInReadOnlyTransaction() throws Exception {
        RagRetrievalService retrievalService = mock(RagRetrievalService.class);
        RagRetrievalResult emptyResult = RagRetrievalResult.builder()
                .scenario("JD_ABILITY_EXTRACT")
                .hits(java.util.List.of())
                .contextText("")
                .build();
        when(retrievalService.retrieveContext(anyString(), any(RagScenarioEnum.class), anyInt()))
                .thenReturn("");

        RagContextServiceImpl service = new RagContextServiceImpl(retrievalService);

        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        RagContextService proxy = (RagContextService) factory.getProxy();

        Transactional annotation = RagContextServiceImpl.class.getMethod(
                        "retrieveContext", String.class, String.class, int.class)
                .getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();

        String context = proxy.retrieveContext("Java", "JD_ABILITY_EXTRACT", 5);
        assertThat(context).isEmpty();
    }
}
