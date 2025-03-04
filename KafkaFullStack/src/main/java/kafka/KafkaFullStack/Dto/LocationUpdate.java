package kafka.KafkaFullStack.Dto;

import lombok.Data;

@Data
public class LocationUpdate {
    private String orderId;
    private double lat;
    private double lon;
    private String status;
    private long timestamp;

    public LocationUpdate(String orderId, double lat, double lon, String status, long timestamp) {
        this.orderId = orderId;
        this.lat = lat;
        this.lon = lon;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters and setters (or use Lombok for brevity)
}
