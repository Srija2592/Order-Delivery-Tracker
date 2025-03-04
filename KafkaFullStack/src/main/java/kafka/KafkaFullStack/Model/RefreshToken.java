package kafka.KafkaFullStack.Model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDate;

@Data
@Getter
@Setter
@Document(collection = "refresh-token")
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {

    @Id
    private String id; // ✅ Use String for MongoDB auto-generation
    private String token;
    private LocalDate createdDate;
}
