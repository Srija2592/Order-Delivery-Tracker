package Delivery.Delivery.Controller;

import Delivery.Delivery.Dto.LocationUpdateRequest;
import Delivery.Delivery.Model.Order;
import Delivery.Delivery.Model.User;
import Delivery.Delivery.Repository.OrderRepository;
import Delivery.Delivery.Repository.UserRepository;
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

    public DeliveryController(KafkaTemplate<String, String> kafkaTemplate, OrderRepository orderRepository, UserRepository userRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/orders/non-delivered")
    public ResponseEntity<List<Order>> getNonDeliveredOrders() {
        List<Order> orders = orderRepository.findAll()
                .stream()
                .filter(order -> order.getAssignment() == null)
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/orders/{username}")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_DELIVERY')")
    public ResponseEntity<List<Order>> getAssignedOrders(@PathVariable String username) {
        List<Order> orders = orderRepository.findAll()
                .stream()
                .filter(order -> order.getAssignment() != null && order.getAssignment().getUsername().equals(username))
                .collect(Collectors.toList());
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/assign/{orderId}/{username}")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_DELIVERY')")
    public ResponseEntity<String> assignOrder(@PathVariable String orderId, @PathVariable String username) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getAssignment() != null) {
                        return ResponseEntity.badRequest().body("Order is already assigned");
                    }
                    User user = userRepository.findByUsername(username).orElseThrow();
                    order.setAssignment(user);
                    order.setStatus("Shipped");
                    orderRepository.save(order);
                    return ResponseEntity.ok("Order " + orderId + " assigned to " + username);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Value("${openrouteservice.api.key}") // Inject API key from application.properties
    private String openRouteServiceApiKey;

    @Async
    @PutMapping("/location")
    @PreAuthorize("hasAuthority('SCOPE_ROLE_DELIVERY')")
    public ResponseEntity<String> updateLocation(@RequestBody LocationUpdateRequest request) {
        String orderId = request.getOrderId();
        return orderRepository.findById(orderId).map(order -> {
            if ("Delivered".equals(order.getStatus())) {
                return ResponseEntity.ok("Order already delivered: " + orderId);
            }

            // Initialize location if not set
            Double curLat = order.getCurLat() != null ? order.getCurLat() : order.getSrcLat();
            Double curLon = order.getCurLon() != null ? order.getCurLon() : order.getSrcLon();

            if (order.getCurLat() == null || order.getCurLon() == null) {
                order.setCurLat(curLat);
                order.setCurLon(curLon);
                order.setStatus("In Transit");
                orderRepository.save(order);
            }

            // Send initial location to Kafka
            sendLocation(orderId, order.getSrcLat(), order.getSrcLon(), curLat, curLon, order.getDesLat(), order.getDesLon(), "In Transit");

            // Fetch route coordinates using OpenRouteService
            List<double[]> route = fetchRouteFromAPI(order.getCurLat(), order.getCurLon(), order.getDesLat(), order.getDesLon());

            if (route == null || route.isEmpty()) {
                return ResponseEntity.status(500).body("Failed to retrieve route from OpenRouteService.");
            }

            // Simulate movement through route points
            for (double[] point : route) {
                try {
                    TimeUnit.SECONDS.sleep(5); // Wait 2 seconds between updates
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ResponseEntity.status(500).body("Error during movement simulation: " + e.getMessage());
                }

                double newLat = point[0]; // Swap to [lat, lon] for consistency
                double newLon = point[1];
                // Stop sending if current location reaches destination
                if (Math.abs(newLat - order.getDesLat()) < 1e-6 && Math.abs(newLon - order.getDesLon()) < 1e-6) {
                    order.setCurLat(newLat);
                    order.setCurLon(newLon);
                    order.setStatus("Delivered");
                    orderRepository.save(order);
                    break; // Exit the loop when destination is reached
                }


                System.err.println("Sending update: " + newLat + ", " + newLon);
                sendLocation(orderId, order.getSrcLat(), order.getSrcLon(), newLat, newLon, order.getDesLat(), order.getDesLon(), "In Transit");

                // Update current location in database
                order.setCurLat(newLat);
                order.setCurLon(newLon);
                order.setStatus("Shipped");
                orderRepository.save(order);
            }

            // Finalize delivery
            orderRepository.save(order);
            sendLocation(orderId, order.getSrcLat(), order.getSrcLon(), order.getDesLat(), order.getDesLon(), order.getDesLat(), order.getDesLon(), "Delivered");

            return ResponseEntity.ok("Order " + orderId + " delivered successfully.");
        }).orElse(ResponseEntity.notFound().build());
    }

    private void sendLocation(String orderId, Double srcLat, Double srcLon, Double curLat, Double curLon,
                              Double desLat, Double desLon, String status) {
        long timestamp = System.currentTimeMillis();
        String message = String.format("%s:%.6f:%.6f:%.6f:%.6f:%.6f:%.6f:%s:%d",
                orderId, srcLat, srcLon, curLat, curLon, desLat, desLon, status, timestamp);
        System.err.println("Message sent: " + message);
        kafkaTemplate.send("delivery-locations", orderId, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) System.err.println("Error sending Kafka: " + ex.getMessage());
                    else System.out.println("Sent update for " + orderId);
                });
    }

    private List<double[]> fetchRouteFromAPI(double srcLat, double srcLon, double desLat, double desLon) {
        try {
            String url = "https://api.openrouteservice.org/v2/directions/driving-car?"
                    + "api_key=" + openRouteServiceApiKey
                    + "&start=" + srcLon + "," + srcLat
                    + "&end=" + desLon + "," + desLat;

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JSONObject jsonResponse = new JSONObject(response.getBody());
                JSONArray coordinates = jsonResponse.getJSONArray("features")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<double[]> route = new ArrayList<>();
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray point = coordinates.getJSONArray(i);
                    double lon = point.getDouble(0);
                    double lat = point.getDouble(1);
                    route.add(new double[]{lat, lon}); // [lat, lon] for consistency

                    System.err.println("Route point: [" + lat + ", " + lon + "]");
                }
                return route;
            } else {
                System.err.println("Error fetching route: " + response.getStatusCode());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

}