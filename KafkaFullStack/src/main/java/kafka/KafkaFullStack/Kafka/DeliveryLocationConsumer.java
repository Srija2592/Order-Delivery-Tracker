package kafka.KafkaFullStack.Kafka;

import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;


@Service
public class DeliveryLocationConsumer {
    private final SimpMessagingTemplate webSocket;
    private KafkaConsumer<String, String> consumer;

    public DeliveryLocationConsumer(SimpMessagingTemplate webSocket) {
        this.webSocket = webSocket;
        init();
    }

    private void init() {
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092");
        props.put("group.id", "user-group");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("auto.offset.reset", "latest");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("delivery-locations"));
        startPolling();
    }

    private void startPolling() {
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
                records.forEach(record -> {
                    String orderId = record.key();
                    String message = record.value();
                    webSocket.convertAndSend("/api/topic/locations/" + orderId, message);
                    System.out.println("User App: Relayed to WebSocket for " + orderId + ": " + message);
                });
            }
        }).start();
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.close();
        }
    }
}