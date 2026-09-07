package com.cherryoj.submissionservice.messaging;

import com.cherryoj.submissionservice.persistence.SubmissionMapper;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name="cherry.submission.messaging-enabled",havingValue="true")
public class SubmissionMessaging {
    private final SubmissionMapper store;
    private final SubmissionLifecycle lifecycle;
    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper json;
    private final String requests,deadLetters;
    public SubmissionMessaging(SubmissionMapper store,SubmissionLifecycle lifecycle,KafkaTemplate<String,String> kafka,
            ObjectMapper json,@Value("${cherry.submission.requests-topic}") String requests,
            @Value("${cherry.submission.lifecycle-topic}") String lifecycleTopic) {
        this.store=store; this.lifecycle=lifecycle; this.kafka=kafka; this.json=json;
        this.requests=requests; this.deadLetters=lifecycleTopic+".dlt";
    }
    @Scheduled(fixedDelay=1000)
    public void publish() {
        for(var event:store.pending()) {
            try {
                var record=new ProducerRecord<String,String>(requests,event.messageKey(),event.payload());
                if(event.traceParent()!=null) record.headers().add("traceparent",event.traceParent().getBytes(StandardCharsets.US_ASCII));
                kafka.send(record).get(10,TimeUnit.SECONDS);
                store.published(event.eventId());
            } catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
            catch(Exception error) { store.retry(event.eventId()); }
        }
    }
    @KafkaListener(topics="${cherry.submission.lifecycle-topic}")
    public void receive(ConsumerRecord<String,String> record) {
        try { lifecycle.apply(record.key(),record.value()); }
        catch(IllegalArgumentException | java.time.DateTimeException | tools.jackson.core.JacksonException invalid) {
            // 毒消息只传播安全定位信息。绝不把未验证原文再写入 DLT 或日志。
            String summary=json.writeValueAsString(java.util.Map.of("code","INVALID_LIFECYCLE", "topic",record.topic(),
                    "partition",record.partition(),"offset",record.offset()));
            try { kafka.send(deadLetters,null,summary).get(10,TimeUnit.SECONDS); }
            catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("dead letter interrupted"); }
            catch(Exception error) { throw new IllegalStateException("dead letter unavailable"); }
        }
    }
}
