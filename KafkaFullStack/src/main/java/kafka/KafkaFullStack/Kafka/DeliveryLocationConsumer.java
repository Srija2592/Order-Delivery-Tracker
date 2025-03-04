package kafka.KafkaFullStack.Kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeliveryLocationConsumer {
    private final SimpMessagingTemplate webSocket;

    @Autowired
    public DeliveryLocationConsumer(SimpMessagingTemplate webSocket) {
        this.webSocket = webSocket;
    }

    @KafkaListener(topics = "delivery-locations", groupId = "delivery-group")
    public void listen(ConsumerRecord<String, String> record) {
        String message = record.value();
        webSocket.convertAndSend("/api/topic/locations", message);
        System.out.println("Sent to WebSocket: " + message);

        // Log delivered orders
        if (message.contains(":Delivered:")) {
            System.out.println("Order Delivered: " + message);
        } else if (message.contains(":Shipped:")) {
            System.out.println("Order Shipped: " + message);
        }
    }
}
