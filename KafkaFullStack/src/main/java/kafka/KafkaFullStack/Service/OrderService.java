package kafka.KafkaFullStack.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.KafkaFullStack.Kafka.OrderCreatedEvent;
import kafka.KafkaFullStack.Model.Order;
import kafka.KafkaFullStack.Model.OutboxEvent;
import kafka.KafkaFullStack.Repository.OrderRepository;
import kafka.KafkaFullStack.Repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Order createOrder(Order order) {

        Order savedOrder = orderRepository.save(order);

        // Build the ORDER_CREATED event
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                savedOrder.getId(),
                savedOrder.getStatus(),
                savedOrder.getTruckId(),
                savedOrder.getCreationDate(),
                savedOrder.getItems(),
                savedOrder.getSrcLat(),
                savedOrder.getSrcLon(),
                savedOrder.getDesLat(),
                savedOrder.getDesLon()
        );

        // Save event in the outbox collection
        outboxRepository.save(new OutboxEvent(
                event.eventId(),
                event.orderId(),
                "ORDER_CREATED",
                toJson(event),
                System.currentTimeMillis(),
                "PENDING"
        ));

        return savedOrder;
    }

    private String toJson(OrderCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize order event", e);
        }
    }
}
