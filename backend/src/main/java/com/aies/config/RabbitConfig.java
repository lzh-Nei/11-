package com.aies.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 异步链拓扑（UC-16 解析链 W7-C1 + UC-20 评价链 W7-C2）：
 *
 * <pre>
 * SubmissionService(事务提交后) --parse.submit--> aies.parse.exchange
 *     --direct--> aies.parse.queue --> ParseWorker（置QUEUED→提取→SUCCESS/FAILED）
 * ParseWorker(SUCCESS后) --eval.submit--> aies.eval.exchange（UC-20 MQ内部链路，无REST触发）
 *     --direct--> aies.eval.queue --> EvaluationWorker（LLM评分→事务双写→冷桶留痕）
 * </pre>
 *
 * <p>可靠性口径：
 * ① 队列/交换机 durable，消息 JSON 序列化且默认持久化；
 * ② 业务失败由 worker 捕获并落 FAILED 后正常 ack；未捕获异常/脏消息不 requeue，
 *    经 {@code x-dead-letter-exchange} 进入对应 DLQ，保留现场供人工排查；
 * ③ 发布确认：publisher-confirm-type=correlated + mandatory，nack/不可路由写 ERROR；
 * ④ 解析与评价使用独立监听容器参数（见 application.yml mq.*），评价并发受
 *    LLM 信号量（ai.semaphore.permits）约束。</p>
 */
@Slf4j
@Configuration
public class RabbitConfig {

    public static final String PARSE_QUEUE = "aies.parse.queue";
    public static final String PARSE_EXCHANGE = "aies.parse.exchange";
    public static final String PARSE_ROUTING_KEY = "parse.submit";

    public static final String EVAL_QUEUE = "aies.eval.queue";
    public static final String EVAL_EXCHANGE = "aies.eval.exchange";
    public static final String EVAL_ROUTING_KEY = "eval.submit";

    /** 统一死信交换机：消费失败/不可路由的业务消息进入各自 DLQ，不自动消费 */
    public static final String DLX_EXCHANGE = "aies.dlx";
    public static final String PARSE_DLQ = "aies.parse.dlq";
    public static final String EVAL_DLQ = "aies.eval.dlq";
    public static final String PARSE_DEAD_ROUTING_KEY = "parse.dead";
    public static final String EVAL_DEAD_ROUTING_KEY = "eval.dead";

    /** 管理端 MQ 监控只读查询队列水位（getQueueInfo），不声明/不消费 */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    /* ==================== 解析链 ==================== */

    @Bean
    public Queue parseQueue() {
        return QueueBuilder.durable(PARSE_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(PARSE_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange parseExchange() {
        return new DirectExchange(PARSE_EXCHANGE, true, false);
    }

    @Bean
    public Binding parseBinding(Queue parseQueue, DirectExchange parseExchange) {
        return BindingBuilder.bind(parseQueue).to(parseExchange).with(PARSE_ROUTING_KEY);
    }

    /* ==================== 评价链 ==================== */

    /** UC-20 评价队列（W7-C2）：durable，消费失败不requeue（FAILED收敛落库同解析链口径） */
    @Bean
    public Queue evalQueue() {
        return QueueBuilder.durable(EVAL_QUEUE)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(EVAL_DEAD_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange evalExchange() {
        return new DirectExchange(EVAL_EXCHANGE, true, false);
    }

    @Bean
    public Binding evalBinding(Queue evalQueue, DirectExchange evalExchange) {
        return BindingBuilder.bind(evalQueue).to(evalExchange).with(EVAL_ROUTING_KEY);
    }

    /* ==================== 死信拓扑 ==================== */

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue parseDeadLetterQueue() {
        return QueueBuilder.durable(PARSE_DLQ).build();
    }

    @Bean
    public Queue evalDeadLetterQueue() {
        return QueueBuilder.durable(EVAL_DLQ).build();
    }

    @Bean
    public Binding parseDeadLetterBinding(Queue parseDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(parseDeadLetterQueue).to(deadLetterExchange).with(PARSE_DEAD_ROUTING_KEY);
    }

    @Bean
    public Binding evalDeadLetterBinding(Queue evalDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(evalDeadLetterQueue).to(deadLetterExchange).with(EVAL_DEAD_ROUTING_KEY);
    }

    /* ==================== 序列化、确认与监听参数 ==================== */

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        // mandatory：路由不到队列时触发 returns 回调，而不是静默丢弃
        template.setMandatory(true);
        // 发布确认：broker nack 时记录 correlationId（生产者投递时传入）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.debug("MQ消息已确认: id={}", correlationId(correlationData));
            } else {
                log.error("MQ消息未被broker确认: id={} cause={}", correlationId(correlationData), cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "MQ消息不可路由: exchange={} routingKey={} replyCode={} replyText={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText()));
        return template;
    }

    /** 解析链：CPU+IO 任务，允许适度并发；prefetch=1 保证长任务公平分发 */
    @Bean
    public SimpleRabbitListenerContainerFactory parseListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter,
            @Value("${mq.parse.concurrency:2}") int concurrency,
            @Value("${mq.parse.max-concurrency:4}") int maxConcurrency,
            @Value("${mq.parse.prefetch:1}") int prefetch,
            @Value("${mq.parse.max-attempts:3}") int maxAttempts) {
        return listenerFactory(connectionFactory, jacksonMessageConverter, concurrency, maxConcurrency,
                prefetch, maxAttempts);
    }

    /** 评价链：LLM 慢任务，保守并发（实际调用还受 ai.semaphore.permits 限制） */
    @Bean
    public SimpleRabbitListenerContainerFactory evalListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter,
            @Value("${mq.eval.concurrency:1}") int concurrency,
            @Value("${mq.eval.max-concurrency:2}") int maxConcurrency,
            @Value("${mq.eval.prefetch:1}") int prefetch,
            @Value("${mq.eval.max-attempts:2}") int maxAttempts) {
        return listenerFactory(connectionFactory, jacksonMessageConverter, concurrency, maxConcurrency,
                prefetch, maxAttempts);
    }

    private SimpleRabbitListenerContainerFactory listenerFactory(ConnectionFactory connectionFactory,
                                                                 MessageConverter messageConverter,
                                                                 int concurrency, int maxConcurrency,
                                                                 int prefetch, int maxAttempts) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(maxConcurrency);
        factory.setPrefetchCount(prefetch);
        // 未捕获异常不 requeue：有 DLX 时进入死信队列，避免毒消息无限重投
        factory.setDefaultRequeueRejected(false);
        if (maxAttempts > 1) {
            // 瞬时故障退避重试（1s→2s→4s，上限5s）；耗尽后 reject 进入死信，保留现场
            factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                    .maxAttempts(maxAttempts)
                    .backOffOptions(1000L, 2.0, 5000L)
                    .recoverer(new RejectAndDontRequeueRecoverer())
                    .build());
        }
        return factory;
    }

    private String correlationId(org.springframework.amqp.rabbit.connection.CorrelationData data) {
        return data == null ? "null" : data.getId();
    }
}
