package kafka.KafkaFullStack.Kafka;

import kafka.KafkaFullStack.Model.OutboxEvent;
import kafka.KafkaFullStack.Repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxEventRepository outboxRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC = "order-events";

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {

        List<OutboxEvent> pendingEvents =
                outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        for (OutboxEvent event : pendingEvents) {

            try {
                // Publish event to Kafka and wait for acknowledgement
                kafkaTemplate.send(
                        TOPIC,
                        event.getAggregateId(),
                        event.getPayload()
                ).get(10, TimeUnit.SECONDS);

                // Mark as published only after Kafka acknowledges it
                event.setStatus("PUBLISHED");

                outboxRepository.save(event);

                log.info("Published event: {}", event.getEventId());

            } catch (Exception e) {

                log.error(
                        "Failed to publish event: {}",
                        event.getEventId(),
                        e
                );
            }
        }
    }
}