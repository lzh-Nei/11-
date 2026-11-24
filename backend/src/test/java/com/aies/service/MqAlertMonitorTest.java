package com.aies.service;

import com.aies.config.RabbitConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MQ 积压/死信告警阈值计算。 */
class MqAlertMonitorTest {

    @Test
    void evaluateReportsBacklogAndDeadLetters() {
        RabbitAdmin admin = mock(RabbitAdmin.class);
        when(admin.getQueueInfo(RabbitConfig.PARSE_QUEUE))
                .thenReturn(new QueueInformation(RabbitConfig.PARSE_QUEUE, 150, 2));
        when(admin.getQueueInfo(RabbitConfig.EVAL_QUEUE))
                .thenReturn(new QueueInformation(RabbitConfig.EVAL_QUEUE, 0, 1));
        when(admin.getQueueInfo(RabbitConfig.PARSE_DLQ))
                .thenReturn(new QueueInformation(RabbitConfig.PARSE_DLQ, 2, 0));
        when(admin.getQueueInfo(RabbitConfig.EVAL_DLQ))
                .thenReturn(new QueueInformation(RabbitConfig.EVAL_DLQ, 0, 0));

        MqAlertMonitor.AlertState state = new MqAlertMonitor(admin, 1, 100).evaluate();

        assertThat(state.alert()).isTrue();
        assertThat(state.level()).isEqualTo("ERROR");
        assertThat(state.messages()).anyMatch(m -> m.contains("解析队列积压 150"));
        assertThat(state.messages()).anyMatch(m -> m.contains("解析死信 2"));
    }

    @Test
    void evaluateQuietWhenBelowThresholds() {
        RabbitAdmin admin = mock(RabbitAdmin.class);
        when(admin.getQueueInfo(RabbitConfig.PARSE_QUEUE))
                .thenReturn(new QueueInformation(RabbitConfig.PARSE_QUEUE, 50, 2));
        when(admin.getQueueInfo(RabbitConfig.EVAL_QUEUE))
                .thenReturn(new QueueInformation(RabbitConfig.EVAL_QUEUE, 0, 1));
        when(admin.getQueueInfo(RabbitConfig.PARSE_DLQ))
                .thenReturn(new QueueInformation(RabbitConfig.PARSE_DLQ, 0, 0));
        when(admin.getQueueInfo(RabbitConfig.EVAL_DLQ))
                .thenReturn(new QueueInformation(RabbitConfig.EVAL_DLQ, 0, 0));

        MqAlertMonitor.AlertState state = new MqAlertMonitor(admin, 1, 100).evaluate();

        assertThat(state.alert()).isFalse();
        assertThat(state.level()).isEqualTo("OK");
        assertThat(state.messages()).isEmpty();
        assertThat(state.signature()).isEmpty();
    }

    @Test
    void evaluateDegradesWhenBrokerUnavailable() {
        RabbitAdmin admin = mock(RabbitAdmin.class);
        when(admin.getQueueInfo(anyString())).thenThrow(new AmqpException("broker down"));

        MqAlertMonitor.AlertState state = new MqAlertMonitor(admin, 1, 100).evaluate();

        assertThat(state.alert()).isTrue();
        assertThat(state.level()).isEqualTo("ERROR");
        assertThat(state.messages().get(0)).contains("读取失败").contains("broker down");
    }
}
