package kafka.KafkaFullStack.Kafka;

import kafka.KafkaFullStack.Repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class DeliveryProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Random random = new Random();
    private final Map<String, OrderDetails> orderDetails = new HashMap<>();
    private final Map<String, Boolean> locationUpdateFlags = new HashMap<>();
    private final OrderRepository orderRepository;

    @Autowired
    public DeliveryProducer(KafkaTemplate<String, String> kafkaTemplate, OrderRepository orderRepository) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "KafkaTemplate must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "OrderRepository must not be null");
    }
    public void sendLocation(String orderId, Double srcLat, Double srcLon, Double curLat, Double curLon,
                             Double desLat, Double desLon, String status) {
        long timestamp = System.currentTimeMillis();
        // Ensure message format matches what Angular expects
        String message = String.format("%s:%.6f:%.6f:%s:%d:%.6f:%.6f",
                orderId, srcLat, srcLon, status, timestamp, curLat, curLon);
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send("delivery-locations", orderId, message);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("Error sending Kafka message: " + ex.getMessage());
            } else {
                System.out.println("Sent location update for order: " + orderId);
            }
        });
    }

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
    public void enableLocationUpdate(String orderId) {
        locationUpdateFlags.put(orderId, true);
    }

    // Stop location updates if not needed
    public void disableLocationUpdate(String orderId) {
        locationUpdateFlags.put(orderId, false);
    }

    public void addNewOrder(String orderId, Double srcLat, Double srcLon, Double curLat, Double curLon, Double desLat, Double desLon, String status) {
        orderDetails.put(orderId, new OrderDetails(srcLat, srcLon, curLat, curLon, desLat, desLon, status));
        sendLocation(orderId, srcLat, srcLon, curLat, curLon,desLat, desLon, status);

        orderRepository.findById(orderId).ifPresentOrElse(order -> {
            order.setSrcLat(srcLat);
            order.setSrcLon(srcLon);
            order.setCurLat(curLat);
            order.setCurLon(curLon);
            order.setDesLat(desLat);
            order.setDesLon(desLon);
            order.setStatus(status);
            orderRepository.save(order);
        }, () -> {
            throw new IllegalStateException("Order not found for ID: " + orderId);
        });
    }
}
