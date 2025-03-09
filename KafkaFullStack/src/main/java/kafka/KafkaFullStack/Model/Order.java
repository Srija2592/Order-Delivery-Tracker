package kafka.KafkaFullStack.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
@AllArgsConstructor
@NoArgsConstructor // Added no-argument constructor
@Document(collection = "orders")
public class Order {
    @Id
    private String id;
    private String[] items;
    private String status;
    private long creationDate;
    @Min(-90) @Max(90)
    private Double srcLat;
    @Min(-180) @Max(180)
    private Double srcLon;
    @Min(-90) @Max(90)
    private Double desLat;
    @Min(-180) @Max(180)
    private Double desLon;
    @Min(-90) @Max(90)
    private Double curLat;
    @Min(-180) @Max(180)
    private Double curLon;
    private String truckId;
    @DBRef
    private User assignment;

    @DBRef
    private User creator;

    public Order(String id, String[] items, String inTransit, long creationDate, String s, Double desLat, Double desLon) {
        this.id = id;
        this.items = items;
        this.status = inTransit;
        this.creationDate = creationDate;
        this.truckId = s;
        this.desLat = desLat;
        this.desLon = desLon;
    }

    /**
     * Calculates the Haversine distance between current and destination coordinates in kilometers.
     * @return Distance in kilometers, or -1 if coordinates are invalid
     */
    public double calculateDistanceToDestination() {
        if (curLat == null || curLon == null || desLat == null || desLon == null) {
            return -1;
        }
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(desLat - curLat);
        double dLon = Math.toRadians(desLon - curLon);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(curLat)) * Math.cos(Math.toRadians(desLat)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
