package Delivery.Delivery.Controller;

import Delivery.Delivery.Dto.LocationUpdateRequest;
import Delivery.Delivery.Model.Order;
import Delivery.Delivery.Model.User;
import Delivery.Delivery.Repository.OrderRepository;
import Delivery.Delivery.Repository.UserRepository;
import Delivery.Delivery.Service.DeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deliveries")
@Validated
public class DeliveryController {

    private static final double STEP_SIZE = 0.001; // Controls how large the steps are
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    
    private final DeliveryService deliveryService;

    public DeliveryController(KafkaTemplate<String, String> kafkaTemplate, OrderRepository orderRepository, UserRepository userRepository, DeliveryService deliveryService) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.deliveryService = deliveryService;
    }

    @GetMapping("/orders/non-delivered")
    public ResponseEntity<List<Order>> getNonDeliveredOrders() {
        List<Order> orders = deliveryService.getNonDeliveredOrders();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/orders/{username}")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_DELIVERY')")
    public ResponseEntity<List<Order>> getAssignedOrders(@PathVariable String username) {
       List<Order> orders = deliveryService.getAssignedOrders(username);
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/assign/{orderId}/{username}")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_DELIVERY')")
    public ResponseEntity<?> assignOrder(@PathVariable String orderId, @PathVariable String username) {
        return deliveryService.assignOrder(orderId, username);
    }

    @Value("${openrouteservice.api.key}") // Inject API key from application.properties
    private String openRouteServiceApiKey;

    @Async
    @PutMapping("/location")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_DELIVERY')")
    public ResponseEntity<String> updateLocation(@RequestBody LocationUpdateRequest request) {
        return deliveryService.updateLocation(request);
    }
}