package com.cherryoj.judgingservice.formal;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name="cherry.formal.enabled",havingValue="true")
public class FormalMessaging {
    private final FormalTaskStore store;
    private final KafkaTemplate<String,String> kafka;
    private final FormalProperties properties;
    private final ObjectMapper json;
    public FormalMessaging(FormalTaskStore store,KafkaTemplate<String,String> kafka,FormalProperties properties,ObjectMapper json) {
        this.store=store; this.kafka=kafka; this.properties=properties; this.json=json;
    }
    @KafkaListener(topics="${cherry.formal.requests-topic}")
    public void receive(ConsumerRecord<String,String> record) {
        try {
            if(record.value()==null || record.value().getBytes(StandardCharsets.UTF_8).length>1048576) throw new IllegalArgumentException();
            var event=json.readTree(record.value());
            if(!event.isObject()) throw new IllegalArgumentException();
            for(var entry:event.properties()) if(!Set.of("eventId","eventType","eventVersion","occurredAt","traceId","aggregateId","payload").contains(entry.getKey())) throw new IllegalArgumentException();
            if(!"JudgeRequested".equals(event.path("eventType").asString()) || !event.path("eventVersion").isIntegralNumber() || event.path("eventVersion").asInt()!=1) throw new IllegalArgumentException();
            String id=event.path("aggregateId").asString(),eventId=event.path("eventId").asString(),trace=event.path("traceId").asString();
            UUID.fromString(id); UUID.fromString(eventId);
            if(!id.equals(record.key()) || !trace.matches("[a-f0-9]{32}")) throw new IllegalArgumentException();
            var payload=event.path("payload");
            if(!payload.isObject() || payload.size()!=2 || !id.equals(payload.path("submissionId").asString())
                    || !"2".equals(payload.path("judgeInputContractVersion").asString())) throw new IllegalArgumentException();
            var header=record.headers().lastHeader("traceparent");
            String parent=header==null?null:new String(header.value(),StandardCharsets.US_ASCII);
            if(parent!=null && !parent.matches("00-[a-f0-9]{32}-[a-f0-9]{16}-0[01]")) parent=null;
            store.receive(eventId,id,Instant.parse(event.path("occurredAt").asString()),trace,parent);
        } catch(IllegalArgumentException | java.time.DateTimeException | tools.jackson.core.JacksonException invalid) {
            String summary=json.writeValueAsString(java.util.Map.of("code","INVALID_JUDGE_REQUEST","topic",record.topic(),"partition",record.partition(),"offset",record.offset()));
            try { kafka.send(properties.requestsTopic()+".dlt",null,summary).get(10,TimeUnit.SECONDS); }
            catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("dead letter interrupted"); }
            catch(Exception error) { throw new IllegalStateException("dead letter unavailable"); }
        }
    }
    @Scheduled(fixedDelay=1000,scheduler="formalTaskScheduler")
    public void publish() {
        for(var event:store.pending()) {
            try {
                var record=new ProducerRecord<String,String>(properties.lifecycleTopic(),event.key(),event.payload());
                if(event.traceParent()!=null) record.headers().add("traceparent",event.traceParent().getBytes(StandardCharsets.US_ASCII));
                kafka.send(record).get(10,TimeUnit.SECONDS); store.published(event.id());
            } catch(InterruptedException error) { Thread.currentThread().interrupt(); return; }
            catch(Exception error) { store.retryPublish(event.id()); }
        }
    }
}
