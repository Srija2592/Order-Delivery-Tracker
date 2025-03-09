package Delivery.Delivery.Kafka;

import Delivery.Delivery.Model.Order;
import Delivery.Delivery.Repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.*;

@Service
public class DeliveryProducer {

    private static class OrderDetails {
        Double srcLat, srcLon, desLat, desLon, curLat, curLon;
        String status;

        public OrderDetails(Double desLat, Double desLon, Double curLat, Double curLon, Double srcLat, Double srcLon, String status) {
            this.srcLat = (srcLat != null) ? srcLat : 17.3850;
            this.srcLon = (srcLon != null) ? srcLon : 78.4867;

            this.desLat = (desLat != null) ? desLat : this.srcLat; // Use updated srcLat
            this.desLon = (desLon != null) ? desLon : this.srcLon; // Use updated srcLon

            this.curLat = (curLat != null) ? curLat : this.srcLat; // Use updated srcLat
            this.curLon = (curLon != null) ? curLon : this.srcLon; // Use updated srcLon

            this.status = (status != null) ? status : "In Transit";
        }


        void update(Double srcLat, Double srcLon, Double curLat, double curLon, double desLat, double desLon, String status) {
            this.srcLat = srcLat;
            this.srcLon = srcLon;
            this.status = status;
            this.curLat = curLat;
            this.curLon = curLon;
            this.desLat = desLat;
            this.desLon = desLon;
        }

        boolean hasReachedDestination() {
            return isClose(curLat, desLat) && isClose(curLon, desLon);
        }

        boolean isAtOrigin() {
            return isClose(curLat, 17.3850) && isClose(curLon, 78.4867);
        }

        private boolean isClose(double a, double b) {
            return Math.abs(a - b) < 0.0001;
        }
    }



    // Allow delivery person to request location update
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Map<String, OrderDetails> orderDetails = new HashMap<>();
    private final Map<String, Boolean> locationUpdateFlags = new HashMap<>();
    private final OrderRepository orderRepository;

    @Autowired
    public DeliveryProducer(KafkaTemplate<String, String> kafkaTemplate, OrderRepository orderRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderRepository = orderRepository;
    }

    @PostConstruct
    public void initOrderDetails() {
        List<Order> orders = orderRepository.findAll();
        if (orders != null) {
            orders.forEach(order -> {
                orderDetails.put(order.getId(), new OrderDetails(
                        order.getDesLat(), order.getDesLon(),
                        order.getCurLat() != null ? order.getCurLat() : order.getSrcLat(),
                        order.getCurLon() != null ? order.getCurLon() : order.getSrcLon(),
                        order.getSrcLat(), order.getSrcLon(), order.getStatus()
                ));
                locationUpdateFlags.put(order.getId(), "Shipped".equals(order.getStatus()) || "In Transit".equals(order.getStatus()));
            });
        }
        System.out.println("Delivery App: Initialized " + orderDetails.size() + " orders");
    }

    @Scheduled(fixedRate = 2000) // Every 2 seconds
    public void simulateDeliveries() {
        for (String orderId : new ArrayList<>(orderDetails.keySet())) {
            if (!locationUpdateFlags.getOrDefault(orderId, false)) {
                continue;
            }

            orderRepository.findById(orderId).ifPresent(order -> {
                OrderDetails details = orderDetails.get(orderId);
                if (details != null) {
                    // Send current status without modifying coordinates
                    sendLocation(orderId, order.getSrcLat(), order.getSrcLon(), order.getCurLat(), order.getCurLon(),
                            order.getDesLat(), order.getDesLon(), order.getStatus());
                    System.out.println("Sent status update for order " + orderId + " at (" + order.getCurLat() + ", " + order.getCurLon() + ")");
                }
            });
        }
    }

    public void sendLocation(String orderId, Double srcLat, Double srcLon, Double curLat, Double curLon,
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

    public void enableLocationUpdate(String orderId) {
        locationUpdateFlags.put(orderId, true);
        System.out.println("Delivery App: Enabled location updates for " + orderId);
    }

    public void disableLocationUpdate(String orderId) {
        locationUpdateFlags.put(orderId, false);
    }
}
