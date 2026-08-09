package Delivery.Delivery.Service;

import Delivery.Delivery.Dto.LocationUpdateRequest;
import Delivery.Delivery.Model.Order;
import Delivery.Delivery.Model.User;
import Delivery.Delivery.Repository.OrderRepository;
import Delivery.Delivery.Repository.UserRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class DeliveryService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${openrouteservice.api.key}")
    private String openRouteServiceApiKey;

    public DeliveryService(OrderRepository orderRepository, UserRepository userRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    public List<Order> getNonDeliveredOrders() {
        List<Order> orders = orderRepository.findAll()
                .stream()
                .filter(order -> order.getAssignment() == null)
                .collect(Collectors.toList());
        return orders;
    }

    public List<Order> getAssignedOrders(String username) {
        List<Order> orders = orderRepository.findAll()
                .stream()
                .filter(order -> order.getAssignment() != null && order.getAssignment().getUsername().equals(username))
                .collect(Collectors.toList());
        return orders;
    }

    public ResponseEntity<?> assignOrder(String orderId, String username) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    if (order.getAssignment() != null) {
                        return ResponseEntity.badRequest().body("Order is already assigned");
                    }
                    User user = userRepository.findByUsername(username).orElseThrow();
                    order.setAssignment(user);
                    order.setStatus("Shipped");
                    orderRepository.save(order);
                    return ResponseEntity.ok(order);
                })
                .orElse(ResponseEntity.notFound().build());
    }


    public ResponseEntity<String> updateLocation(LocationUpdateRequest request) {
        String orderId = request.getOrderId();
        return orderRepository.findById(orderId).map(order -> {
            if ("Delivered".equals(order.getStatus())) {
                return ResponseEntity.ok("Order already delivered: " + orderId);
            }

            Double curLat = order.getCurLat() != null ? order.getCurLat() : order.getSrcLat();
            Double curLon = order.getCurLon() != null ? order.getCurLon() : order.getSrcLon();

            if (order.getCurLat() == null || order.getCurLon() == null) {
                order.setCurLat(curLat);
                order.setCurLon(curLon);
                order.setStatus("In Transit");
                orderRepository.save(order);
            }

            sendLocation(orderId, order.getSrcLat(), order.getSrcLon(), curLat, curLon, order.getDesLat(), order.getDesLon(), "In Transit");

            List<double[]> route = fetchRouteFromAPI(order.getCurLat(), order.getCurLon(), order.getDesLat(), order.getDesLon());

            if (route == null || route.isEmpty()) {
                return ResponseEntity.status(500).body("Failed to retrieve route from OpenRouteService.");
            }

            for (double[] point : route) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ResponseEntity.status(500).body("Error during movement simulation: " + e.getMessage());
                }

                double newLat = point[0];
                double newLon = point[1];
                if (Math.abs(newLat - order.getDesLat()) < 1e-6 && Math.abs(newLon - order.getDesLon()) < 1e-6) {
                    order.setCurLat(newLat);
                    order.setCurLon(newLon);
                    order.setStatus("Delivered");
                    orderRepository.save(order);
                    break;
                }

                sendLocation(orderId, order.getSrcLat(), order.getSrcLon(), newLat, newLon, order.getDesLat(), order.getDesLon(), "In Transit");

                order.setCurLat(newLat);
                order.setCurLon(newLon);
                order.setStatus("Shipped");
                orderRepository.save(order);
            }

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
        kafkaTemplate.send("delivery-locations", orderId, message);
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
                    route.add(new double[]{lat, lon});
                }
                return route;
            } else {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
