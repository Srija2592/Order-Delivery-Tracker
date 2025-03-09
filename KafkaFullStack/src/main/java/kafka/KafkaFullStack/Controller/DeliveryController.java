package kafka.KafkaFullStack.Controller;

import kafka.KafkaFullStack.Kafka.DeliveryProducer;
import kafka.KafkaFullStack.Model.Order;
import kafka.KafkaFullStack.Model.User;
import kafka.KafkaFullStack.Repository.OrderRepository;
import kafka.KafkaFullStack.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/user")
@Validated
public class DeliveryController {
    private final DeliveryProducer deliveryProducer;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;


    @Autowired
    public DeliveryController(DeliveryProducer deliveryProducer, OrderRepository orderRepository, UserRepository userRepository) {
        this.deliveryProducer = deliveryProducer;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }



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
    public ResponseEntity<String> addOrder(@RequestBody OrderRequest orderRequest) {
        if (!userRepository.existsById(orderRequest.username())) {
            return ResponseEntity.badRequest().body("User " + orderRequest.username() + " not found");
        }

        User user = userRepository.findByUsername(orderRequest.username()).orElseThrow();
        Order order = new Order(
                orderRequest.id(),
                orderRequest.items(),
                "In Transit", // Initial status set to "In Transit"
                orderRequest.creationDate(),
                orderRequest.truckId(),
                orderRequest.desLat(),
                orderRequest.desLon()
        );

        order.setCreator(user);
        // Set initial location to Hyderabad
        order.setSrcLat(17.4065);
        order.setSrcLon(78.4772);
        order.setCurLat(17.4065);
        order.setCurLon(78.4772);
        orderRepository.save(order);

        // Add to Kafka simulation
        deliveryProducer.addNewOrder(
                order.getId(),
                order.getSrcLat(),
                order.getSrcLon(),
                order.getCurLat(),
                order.getCurLon(),
                order.getDesLat(),
                order.getDesLon(),
                order.getStatus()
        );

        return ResponseEntity.ok("Order " + order.getId() + " added with truck " + order.getTruckId());
    }

    record OrderRequest(String id, String username, String[] items, long creationDate, String truckId, Double desLat, Double desLon) {
    }
}