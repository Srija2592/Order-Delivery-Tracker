package kafka.KafkaFullStack.Kafka;

import kafka.KafkaFullStack.Model.Order;
import kafka.KafkaFullStack.Repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

@Service
public class DeliveryProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Random random = new Random();
    private final Map<String, OrderDetails> orderDetails = new HashMap<>();
    private final OrderRepository orderRepository;

    @Autowired
    public DeliveryProducer(KafkaTemplate<String, String> kafkaTemplate, OrderRepository orderRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRepository = orderRepository;
        // Initialize with orders from database
        this.initOrderDetails();
    }

    @PostConstruct
    public void initOrderDetails() {
        orderRepository.findAll().forEach(order -> {
            orderDetails.put(order.getId(), new OrderDetails(
                    order.getLat(), order.getLon(), order.getDesLat(), order.getDesLon(), order.getStatus()
            ));
        });
        System.out.println("Initialized " + orderDetails.size() + " orders from database");
    }

    private static class OrderDetails {
        double lat, lon, desLat, desLon;
        String status;

        OrderDetails(Double lat, Double lon, Double desLat, Double desLon, String status) {
            this.lat = (lat != null) ? lat : 37.7749; // Default: San Francisco
            this.lon = (lon != null) ? lon : -122.4194;
            this.desLat = (desLat != null) ? desLat : this.lat;
            this.desLon = (desLon != null) ? desLon : this.lon;
            this.status = (status != null) ? status : "In Transit";
        }

        void update(double lat, double lon, String status) {
            this.lat = lat;
            this.lon = lon;
            this.status = status;
        }

        boolean hasReachedDestination() {
            return isClose(lat, desLat) && isClose(lon, desLon);
        }

        boolean isAtOrigin() {
            return isClose(lat, 37.7749) && isClose(lon, -122.4194);
        }

        private boolean isClose(double a, double b) {
            return Math.abs(a - b) < 0.0001; // Approx. 11 meters precision
        }
    }

    @Scheduled(fixedRate = 2000) // Runs every 2 seconds
    public void simulateDeliveries() {
        for (String orderId : new ArrayList<>(orderDetails.keySet())) {
            OrderDetails details = orderDetails.get(orderId);

            // Skip if already delivered
            if ("Delivered".equals(details.status)) continue;

            // Simulate movement if "In Transit" or "Shipped"
            if ("In Transit".equals(details.status) || "Shipped".equals(details.status)) {
                details.lat += (random.nextDouble() - 0.5) * 0.001;
                details.lon += (random.nextDouble() - 0.5) * 0.001;
            }

            // Update status based on location
            String newStatus = details.status;
            if (details.hasReachedDestination()) {
                newStatus = "Delivered";
            } else if (details.isAtOrigin() && !"Shipped".equals(details.status)) {
                newStatus = "Shipped";
            }

            if (!newStatus.equals(details.status)) {
                details.status = newStatus;
            }

            // Send updated location and status to Kafka
            sendLocation(orderId, details.lat, details.lon, details.status);

            // Sync with database
            orderRepository.findById(orderId).ifPresent(order -> {
                order.setLat(details.lat);
                order.setLon(details.lon);
                order.setStatus(details.status);
                orderRepository.save(order);
            });
        }
    }

    public void sendLocation(String orderId, double lat, double lon, String status) {
        long timestamp = System.currentTimeMillis();
        String message = String.format("%s:%f:%f:%s:%d", orderId, lat, lon, status, timestamp);

        // Update local cache
        orderDetails.computeIfAbsent(orderId, k -> new OrderDetails(lat, lon, null, null, status))
                .update(lat, lon, status);

        System.out.println("Sending message: " + message);
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send("delivery-locations", orderId, message);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                System.out.println("Sent successfully: " + message);
            } else {
                System.err.println("Failed to send: " + ex.getMessage());
                throw new RuntimeException("Failed to send location update", ex); // Propagate error
            }
        });
    }

    public void addNewOrder(String orderId, double lat, double lon, double desLat, double desLon, String status) {
        orderDetails.put(orderId, new OrderDetails(lat, lon, desLat, desLon, status));
        sendLocation(orderId, lat, lon, status);

        // Sync with database
        orderRepository.findById(orderId).ifPresentOrElse(
                order -> {
                    order.setLat(lat);
                    order.setLon(lon);
                    order.setDesLat(desLat);
                    order.setDesLon(desLon);
                    order.setStatus(status);
                    orderRepository.save(order);
                },
                () -> {
                    throw new IllegalStateException("Order not found for ID: " + orderId);
                }
        );
    }
}