package kafka.KafkaFullStack.Controller;

import kafka.KafkaFullStack.Kafka.DeliveryProducer;
import kafka.KafkaFullStack.Model.Order;
import kafka.KafkaFullStack.Repository.DeliveryTrackingRepository;
import kafka.KafkaFullStack.Repository.OrderRepository;
import kafka.KafkaFullStack.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@RestController
@RequestMapping("/api/deliveries")
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

    @PostMapping("/location")
    public ResponseEntity<String> addLocation(
            @RequestParam @NotEmpty String orderId,
            @RequestParam @NotNull Double lat,
            @RequestParam @NotNull Double lon) {

        orderRepository.findById(orderId).ifPresent(order -> {
            order.setLat(lat);
            order.setLon(lon);

            // Auto-update status based on location
            if (isAtDestination(order, lat, lon)) {
                order.setStatus("Delivered");
            } else {
                order.setStatus("In Transit");
            }

            orderRepository.save(order);
            deliveryProducer.sendLocation(orderId, lat, lon, order.getStatus());
        });

        return ResponseEntity.ok("Location updated for order " + orderId);
    }

    private boolean isAtDestination(Order order, double lat, double lon) {
        final double THRESHOLD = 0.0001; // Small threshold for floating-point comparison
        return Math.abs(lat - order.getDesLat()) < THRESHOLD && Math.abs(lon - order.getDesLon()) < THRESHOLD;
    }

    @GetMapping("/simulate/{orderId}")
    public ResponseEntity<String> simulateUpdate(@PathVariable String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return ResponseEntity.badRequest().body("Order not found");
        }

        if ("In Transit".equals(order.getStatus())) {
            double[] pos = {order.getLat() != null ? order.getLat() : 37.7749, order.getLon() != null ? order.getLon() : -122.4194};
            pos[0] += (Math.random() - 0.5) * 0.001;
            pos[1] += (Math.random() - 0.5) * 0.001;

            // Auto-update status during simulation
            String status = isAtDestination(order, pos[0], pos[1]) ? "Delivered" : "In Transit";

            deliveryProducer.sendLocation(orderId, pos[0], pos[1], status);
            order.setLat(pos[0]);
            order.setLon(pos[1]);
            order.setStatus(status);
            orderRepository.save(order);

            return ResponseEntity.ok("Simulated update for order " + orderId);
        }

        return ResponseEntity.ok("No simulation needed for delivered order " + orderId);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Order> getOrderDetails(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getLat() == null || order.getLon() == null) {
                        order.setLat(37.7749);
                        order.setLon(-122.4194);
                    }
                    return ResponseEntity.ok(order);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("user/orders/{username}")
    public ResponseEntity<List<Order>> getOrdersByUsername(@PathVariable String username) {
        List<Order> orders = orderRepository.findAllByUsername(username);
        orders.forEach(order -> {
            if (order.getLat() == null || order.getLon() == null) {
                order.setLat(37.7749);
                order.setLon(-122.4194);
            }
        });
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/orders")
    public ResponseEntity<String> addOrder(@RequestBody OrderRequest orderRequest) {
        if (!userRepository.existsById(orderRequest.username())) {
            return ResponseEntity.badRequest().body("User " + orderRequest.username() + " not found");
        }

        Order order = new Order(
                orderRequest.id(),
                orderRequest.username(),
                orderRequest.items(),
                "In Transit", // Initial status set to "In Transit"
                orderRequest.creationDate(),
                orderRequest.truckId(),
                orderRequest.desLat(),
                orderRequest.desLon()
        );

        // Set initial location to Hyderabad
        order.setLat(17.3850);
        order.setLon(78.4867);
        orderRepository.save(order);

        // Add to Kafka simulation
        deliveryProducer.addNewOrder(
                order.getId(),
                order.getLat(),
                order.getLon(),
                order.getDesLat(),
                order.getDesLon(),
                order.getStatus()
        );

        return ResponseEntity.ok("Order " + order.getId() + " added with truck " + order.getTruckId());
    }
    @GetMapping("/track/{orderId}")
    public ResponseEntity<Order> trackOrder(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getLat() == null || order.getLon() == null) {
                        order.setLat(37.7749);
                        order.setLon(-122.4194);
                    }
                    return ResponseEntity.ok(order);
                })
                .orElse(ResponseEntity.notFound().build());
    }
    record OrderRequest(String id, String username, String[] items, long creationDate, String truckId, Double desLat, Double desLon) {
    }
}