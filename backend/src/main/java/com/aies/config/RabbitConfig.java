package com.aies.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
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
 * <p>队列/交换机均 durable（broker 重启不丢）；消息 JSON 序列化；
 * 解析失败由 worker 捕获置 FAILED 后正常 ack（application.yml
 * default-requeue-rejected=false 兜底，防毒文件无限重投）；
 * 评价管道失败收敛为 FAILED(40003) 落库后 ack（同口径），事务回滚留
 * PENDING 可经重放恢复。</p>
 */
@Configuration
public class RabbitConfig {

    public static final String PARSE_QUEUE = "aies.parse.queue";
    public static final String PARSE_EXCHANGE = "aies.parse.exchange";
    public static final String PARSE_ROUTING_KEY = "parse.submit";

    public static final String EVAL_QUEUE = "aies.eval.queue";
    public static final String EVAL_EXCHANGE = "aies.eval.exchange";
    public static final String EVAL_ROUTING_KEY = "eval.submit";

    /** 管理端 MQ 监控只读查询队列水位（getQueueInfo），不声明/不消费 */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public Queue parseQueue() {
        return QueueBuilder.durable(PARSE_QUEUE).build();
    }

    @Bean
    public DirectExchange parseExchange() {
        return new DirectExchange(PARSE_EXCHANGE, true, false);
    }

    @Bean
    public Binding parseBinding(Queue parseQueue, DirectExchange parseExchange) {
        return BindingBuilder.bind(parseQueue).to(parseExchange).with(PARSE_ROUTING_KEY);
    }

    /** UC-20 评价队列（W7-C2）：durable，消费失败不requeue（FAILED收敛落库同解析链口径） */
    @Bean
    public Queue evalQueue() {
        return QueueBuilder.durable(EVAL_QUEUE).build();
    }

    @Bean
    public DirectExchange evalExchange() {
        return new DirectExchange(EVAL_EXCHANGE, true, false);
    }

    @Bean
    public Binding evalBinding(Queue evalQueue, DirectExchange evalExchange) {
        return BindingBuilder.bind(evalQueue).to(evalExchange).with(EVAL_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jacksonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter);
        return template;
    }
}
