package com.example.matching.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * RabbitMQ 消息队列配置
 */
@Configuration
public class RabbitMQConfig {

    private static final int MAX_QUEUE_LENGTH = 10_000;
    private static final int MAX_QUEUE_BYTES = 100 * 1024 * 1024;

    // ========== 队列名称常量 ==========
    public static final String MATCHING_TASK_QUEUE = "matching.task.queue";
    public static final String EXCEL_IMPORT_ANALYZE_QUEUE = "excel.import.analyze.queue";
    public static final String EXCEL_IMPORT_CONFIRM_QUEUE = "excel.import.confirm.queue";
    public static final String RESUME_PARSE_QUEUE = "resume.parse.queue";
    public static final String POST_EVOLUTION_AGENT_TASK_QUEUE = "post.evolution.agent.task.queue";
    public static final String KG_GRAPH_BUILD_TASK_QUEUE = "kg.graph.build.task.queue";
    public static final String KG_GRAPH_CHANGE_SET_QUEUE = "kg.graph.change-set.queue";
    public static final String AI_TEST_QUEUE = "ai.test.queue";
    public static final String LEARNING_OUTCOME_CLOSURE_QUEUE = "learning.outcome.closure.queue";
    public static final String CAPABILITY_ASSESSMENT_STAGE_QUEUE = "capability.assessment.stage.queue";
    public static final String CAPABILITY_ASSESSMENT_LIFECYCLE_QUEUE = "capability.assessment.lifecycle.queue";

    // 简历解析延迟重试队列（TTL 到期后自动回流主队列）
    public static final String RESUME_PARSE_RETRY_30S_QUEUE = "resume.parse.retry.30s";
    public static final String RESUME_PARSE_RETRY_5M_QUEUE = "resume.parse.retry.5m";
    public static final String RESUME_PARSE_RETRY_30M_QUEUE = "resume.parse.retry.30m";

    // 死信队列
    public static final String DEAD_LETTER_QUEUE = "dead.letter.queue";
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";

    // ========== 交换机名称 ==========
    public static final String MATCHING_EXCHANGE = "matching.exchange";

    private QueueBuilder boundedQueue(String name) {
        return QueueBuilder.durable(name)
                .maxLength(MAX_QUEUE_LENGTH)
                .maxLengthBytes(MAX_QUEUE_BYTES);
    }

    // ========== 死信队列 ==========
    @Bean
    public Queue deadLetterQueue() {
        return boundedQueue(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Binding bindDeadLetter() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("dead.letter");
    }

    // ========== 业务队列（带死信配置：失败消息转入 DLX，保留 7 天） ==========
    @Bean
    public Queue matchingTaskQueue() {
        return boundedQueue(MATCHING_TASK_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)  // 7 天
                .build();
    }

    @Bean
    public Queue excelImportAnalyzeQueue() {
        return boundedQueue(EXCEL_IMPORT_ANALYZE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)  // 7 天
                .build();
    }

    @Bean
    public Queue excelImportConfirmQueue() {
        return boundedQueue(EXCEL_IMPORT_CONFIRM_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Queue resumeParseQueue() {
        return boundedQueue(RESUME_PARSE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    // ========== 简历解析延迟重试队列（TTL 到期后自动回流主队列） ==========
    @Bean
    public Queue resumeParseRetry30sQueue() {
        return boundedQueue(RESUME_PARSE_RETRY_30S_QUEUE)
                .deadLetterExchange(MATCHING_EXCHANGE)
                .deadLetterRoutingKey("resume.parse.execute")
                .ttl(30 * 1000)  // 30秒
                .build();
    }

    @Bean
    public Queue resumeParseRetry5mQueue() {
        return boundedQueue(RESUME_PARSE_RETRY_5M_QUEUE)
                .deadLetterExchange(MATCHING_EXCHANGE)
                .deadLetterRoutingKey("resume.parse.execute")
                .ttl(5 * 60 * 1000)  // 5分钟
                .build();
    }

    @Bean
    public Queue resumeParseRetry30mQueue() {
        return boundedQueue(RESUME_PARSE_RETRY_30M_QUEUE)
                .deadLetterExchange(MATCHING_EXCHANGE)
                .deadLetterRoutingKey("resume.parse.execute")
                .ttl(30 * 60 * 1000)  // 30分钟
                .build();
    }

    @Bean
    public Queue postEvolutionAgentTaskQueue() {
        return boundedQueue(POST_EVOLUTION_AGENT_TASK_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Queue kgGraphBuildTaskQueue() {
        return boundedQueue(KG_GRAPH_BUILD_TASK_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Queue kgGraphChangeSetQueue() {
        return boundedQueue(KG_GRAPH_CHANGE_SET_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Queue aiTestQueue() {
        return boundedQueue(AI_TEST_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Queue learningOutcomeClosureQueue() {
        return boundedQueue(LEARNING_OUTCOME_CLOSURE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .ttl(7 * 24 * 60 * 60 * 1000)
                .build();
    }

    @Bean
    public Queue capabilityAssessmentStageQueue() {
        return boundedQueue(CAPABILITY_ASSESSMENT_STAGE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .build();
    }

    @Bean
    public Queue capabilityAssessmentLifecycleQueue() {
        return boundedQueue(CAPABILITY_ASSESSMENT_LIFECYCLE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey("dead.letter")
                .build();
    }

    // ========== 交换机定义 ==========
    @Bean
    public TopicExchange matchingExchange() {
        return new TopicExchange(MATCHING_EXCHANGE, true, false);
    }

    // ========== 绑定关系 ==========
    @Bean
    public Binding bindMatchingTask() {
        return BindingBuilder.bind(matchingTaskQueue()).to(matchingExchange()).with("matching.task.#");
    }

    @Bean
    public Binding bindExcelImportAnalyze() {
        return BindingBuilder.bind(excelImportAnalyzeQueue()).to(matchingExchange()).with("excel.import.analyze.#");
    }

    @Bean
    public Binding bindExcelImportConfirm() {
        return BindingBuilder.bind(excelImportConfirmQueue()).to(matchingExchange()).with("excel.import.confirm.#");
    }

    @Bean
    public Binding bindResumeParseTask() {
        return BindingBuilder.bind(resumeParseQueue()).to(matchingExchange()).with("resume.parse.#");
    }

    @Bean
    public Binding bindResumeParseRetry30s() {
        return BindingBuilder.bind(resumeParseRetry30sQueue()).to(matchingExchange()).with("resume.parse.retry.30s");
    }

    @Bean
    public Binding bindResumeParseRetry5m() {
        return BindingBuilder.bind(resumeParseRetry5mQueue()).to(matchingExchange()).with("resume.parse.retry.5m");
    }

    @Bean
    public Binding bindResumeParseRetry30m() {
        return BindingBuilder.bind(resumeParseRetry30mQueue()).to(matchingExchange()).with("resume.parse.retry.30m");
    }

    @Bean
    public Binding bindPostEvolutionAgentTask() {
        return BindingBuilder.bind(postEvolutionAgentTaskQueue())
                .to(matchingExchange())
                .with("post.evolution.agent.#");
    }

    @Bean
    public Binding bindKgGraphBuildTask() {
        return BindingBuilder.bind(kgGraphBuildTaskQueue())
                .to(matchingExchange())
                .with("kg.graph.build.#");
    }

    @Bean
    public Binding bindKgGraphChangeSet() {
        return BindingBuilder.bind(kgGraphChangeSetQueue())
                .to(matchingExchange())
                .with("kg.graph.change.#");
    }

    @Bean
    public Binding bindAiTest() {
        return BindingBuilder.bind(aiTestQueue())
                .to(matchingExchange())
                .with("ai.test.#");
    }

    @Bean
    public Binding bindLearningOutcomeClosure() {
        return BindingBuilder.bind(learningOutcomeClosureQueue())
                .to(matchingExchange())
                .with("learning.outcome.closure");
    }

    @Bean
    public Binding bindCapabilityAssessmentStage() {
        return BindingBuilder.bind(capabilityAssessmentStageQueue())
                .to(matchingExchange())
                .with("capability.assessment.stage.#");
    }

    @Bean
    public Binding bindCapabilityAssessmentLifecycle() {
        return BindingBuilder.bind(capabilityAssessmentLifecycleQueue())
                .to(matchingExchange())
                .with("capability.assessment.lifecycle.#");
    }

    // ========== 消息转换器配置 ==========
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    // ========== RabbitTemplate配置 ==========
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter,
                                         org.springframework.beans.factory.ObjectProvider<java.util.List<ReturnedMessageHandler>> handlersProvider) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        rabbitTemplate.setMandatory(true);
        // 统一 ReturnsCallback：日志 + 延迟获取所有 ReturnedMessageHandler（打破循环依赖）
        rabbitTemplate.setReturnsCallback(returned -> {
            String cid = returned.getMessage().getMessageProperties().getCorrelationId();
            LoggerFactory.getLogger(RabbitTemplate.class).warn(
                    "MQ message returned: exchange={}, routingKey={}, replyText={}, correlationId={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyText(), cid);
            if (cid != null) {
                java.util.List<ReturnedMessageHandler> handlers = handlersProvider.getIfAvailable();
                if (handlers != null) {
                    for (ReturnedMessageHandler handler : handlers) {
                        try {
                            handler.onMessageReturned(cid);
                        } catch (Exception e) {
                            LoggerFactory.getLogger(RabbitTemplate.class).warn("ReturnedMessageHandler failed: {}", e.getMessage());
                        }
                    }
                }
            }
        });
        return rabbitTemplate;
    }

    // ========== RabbitListener 配置：分离快/慢工厂 ==========
    @Bean(name = "fastRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory fastRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = buildBaseFactory(configurer, connectionFactory, jsonMessageConverter);
        // 快速消费者：4-8 个消费者，预取 10 条（低延迟、高吞吐）
        factory.setConcurrentConsumers(4);
        factory.setMaxConcurrentConsumers(8);
        factory.setPrefetchCount(10);
        return factory;
    }

    @Bean(name = "slowRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory slowRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = buildBaseFactory(configurer, connectionFactory, jsonMessageConverter);
        // 慢速消费者：1-2 个消费者，预取 1 条（避免长时间任务堆积阻塞）
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        factory.setPrefetchCount(1);
        return factory;
    }

    /** Excel 岗位导入使用独立执行器，避免与标签治理/其他 AI 任务竞争。 */
    @Bean(name = "excelImportAiRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory excelImportAiRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            @org.springframework.beans.factory.annotation.Qualifier("postImportAiExecutor") ThreadPoolTaskExecutor postImportAiExecutor) {
        SimpleRabbitListenerContainerFactory factory = buildBaseFactory(configurer, connectionFactory, jsonMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        factory.setPrefetchCount(1);
        factory.setTaskExecutor(postImportAiExecutor);
        return factory;
    }

    /**
     * 学习成果已经由人工审核确认。闭环消费失败需先重试，不能一次失败就直接丢入死信。
     */
    @Bean(name = "learningOutcomeClosureRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory learningOutcomeClosureRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = buildBaseFactory(configurer, connectionFactory, jsonMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(2);
        factory.setPrefetchCount(1);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }

    private SimpleRabbitListenerContainerFactory buildBaseFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        // 消费失败不重回队列，由死信队列接盘
        factory.setDefaultRequeueRejected(false);
        // 接收后从消息 headers 恢复 traceId，保证 HTTP -> MQ -> 消费链路同一 traceId
        factory.setAfterReceivePostProcessors(message -> {
            Object traceId = message.getMessageProperties().getHeader("traceId");
            if (traceId != null) {
                com.example.matching.common.trace.TraceContext.set(String.valueOf(traceId));
            }
            return message;
        });
        return factory;
    }
}
