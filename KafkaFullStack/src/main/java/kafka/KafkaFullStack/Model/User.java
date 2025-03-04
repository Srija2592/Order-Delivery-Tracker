package kafka.KafkaFullStack.Model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Document(collection = "users")
public class User {
    @Id
    private String username;
    private String name;
    private String email;
    private String password;

    @Field("role")
    private Role role;
}