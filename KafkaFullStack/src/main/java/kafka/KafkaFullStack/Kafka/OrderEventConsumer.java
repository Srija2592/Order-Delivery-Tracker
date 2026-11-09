package kafka.KafkaFullStack.Kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final OpenSearchClient openSearchClient;

    @KafkaListener(
            topics = "order-events",
            groupId = "order-events-consumer-group"
    )
    public void consumeOrderEvent(String message) {

        try {
            // 1. Convert JSON message into Java event
            OrderCreatedEvent event =
                    objectMapper.readValue(message, OrderCreatedEvent.class);

            log.info("Received ORDER_CREATED event for order: {}",
                    event.orderId());

            // 2. Index the event in OpenSearch
            var response = openSearchClient.index(i -> i
                    .index("orders")
                    .id(event.orderId())
                    .document(event)
            );

            // 3. Log indexing result
            log.info("Order indexed successfully. Order ID: {}", event.orderId());

        } catch (Exception e) {
            log.error("Failed to process order event: {}", message, e);

            // Propagate the exception so Kafka's error handling
            // mechanism can handle the failed record.
            throw new RuntimeException("Order event processing failed", e);
        }
    }
}