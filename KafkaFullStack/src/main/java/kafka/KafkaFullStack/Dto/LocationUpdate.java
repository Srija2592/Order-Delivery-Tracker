package kafka.KafkaFullStack.Dto;

import lombok.Data;

@Data
public class LocationUpdate {
    private String orderId;
    private Double lat;
    private Double lon;
    private String status;
    private long timestamp;

    public LocationUpdate(String orderId, Double lat, Double lon, String status, long timestamp) {
        this.orderId = orderId;
        this.lat = lat;
        this.lon = lon;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters and setters (or use Lombok for brevity)
}
