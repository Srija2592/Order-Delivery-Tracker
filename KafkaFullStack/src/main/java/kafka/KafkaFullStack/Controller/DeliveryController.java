package kafka.KafkaFullStack.Controller;

import kafka.KafkaFullStack.Kafka.DeliveryProducer;
import kafka.KafkaFullStack.Kafka.OrderCreatedEvent;
import kafka.KafkaFullStack.Model.Order;
import kafka.KafkaFullStack.Model.OutboxEvent;
import kafka.KafkaFullStack.Model.User;
import kafka.KafkaFullStack.Repository.OrderRepository;
import kafka.KafkaFullStack.Repository.OutboxEventRepository;
import kafka.KafkaFullStack.Repository.UserRepository;
import kafka.KafkaFullStack.Service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static org.springframework.data.mongodb.util.BsonUtils.toJson;

@RestController
@RequestMapping("/api/user")
@Validated
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryProducer deliveryProducer;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private final OutboxEventRepository outboxEventRepository;
    private final OrderService orderService;


    @GetMapping("/track/{orderId}")
    public ResponseEntity<Map<String, Object>> trackOrder(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getSrcLat() == null || order.getSrcLon() == null) {
                        order.setSrcLat(37.7749);
                        order.setSrcLon(-122.4194);
                    }
                    Map<String, Object> response = new HashMap<>();
                    response.put("order", order);
                    response.put("distanceToDestinationKm", order.calculateDistanceToDestination());
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_USER')")
    public ResponseEntity<Order> getOrderDetails(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getSrcLat() == null || order.getSrcLon() == null) {
                        order.setSrcLat(37.7749);
                        order.setSrcLon(-122.4194);
                    }
                    return ResponseEntity.ok(order);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/orders/username/{username}")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_USER')")
    public ResponseEntity<List<Order>> getOrdersByUsername(@PathVariable String username) {
        List<Order> orders = orderRepository.findAllByCreator_username(username);
        orders.forEach(order -> {
            if (order.getSrcLat() == null || order.getSrcLon() == null) {
                order.setSrcLat(37.7749);
                order.setSrcLon(-122.4194);
            }
        });
        return ResponseEntity.ok(orders);
    }



    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_USER')")
    public ResponseEntity<String> addOrder(
            @RequestBody OrderRequest orderRequest) {

        if (!userRepository.existsById(orderRequest.username())) {
            return ResponseEntity.badRequest()
                    .body("User " + orderRequest.username() + " not found");
        }

        User user = userRepository.findByUsername(
                orderRequest.username()
        ).orElseThrow();

        Order order = new Order(
                orderRequest.id(),
                orderRequest.items(),
                "In Transit",
                orderRequest.creationDate(),
                orderRequest.truckId(),
                orderRequest.desLat(),
                orderRequest.desLon()
        );

        order.setCreator(user);

        // Initial location: Hyderabad
        order.setSrcLat(17.4065);
        order.setSrcLon(78.4772);
        order.setCurLat(17.4065);
        order.setCurLon(78.4772);

        // Delegate order and outbox persistence to OrderService
        Order savedOrder = orderService.createOrder(order);

        return ResponseEntity.ok(
                "Order " + savedOrder.getId() + " added with truck "
                        + savedOrder.getTruckId()
        );
    }

    record OrderRequest(String id, String username, String[] items, long creationDate, String truckId, Double desLat, Double desLon) {
    }
}