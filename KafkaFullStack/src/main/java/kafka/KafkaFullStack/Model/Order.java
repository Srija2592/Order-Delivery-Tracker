package kafka.KafkaFullStack.Model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data
@Document(collection = "orders")
public class Order {
    @Id
    private String id;
    private String username;
    private String[] items;
    private String status;
    private long creationDate;
    private Double lat;
    private Double lon;
    private Double desLat;
    private Double desLon;
    private String truckId; // Add truck ID to map order to truck

    public Order(String id, String username, String[] items, String status, long creationDate, String truckId, Double desLat, Double desLon) {
        this.id = id;
        this.username = username;
        this.items = items;
        this.status = status;
        this.creationDate = creationDate;
        this.truckId = truckId;
        this.desLat = desLat;
        this.desLon = desLon;

    }
}