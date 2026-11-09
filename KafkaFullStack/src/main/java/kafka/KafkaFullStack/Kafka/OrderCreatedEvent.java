package kafka.KafkaFullStack.Kafka;

public record OrderCreatedEvent(
        String eventId,
        String orderId,
        String status,
        String truckId,
        long creationDate,
        String[] items,
        Double srcLat,
        Double srcLon,
        Double desLat,
        Double desLon
) {}
