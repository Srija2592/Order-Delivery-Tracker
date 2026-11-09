package kafka.KafkaFullStack.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "outbox")
public class OutboxEvent {

    @Id
    private String eventId;

    private String aggregateId;
    private String eventType;
    private String payload;
    private long createdAt;
    private String status;
}
