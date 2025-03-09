package Delivery.Delivery.Model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Data
@Document(collection = "users")
public class User {
    @Id
    private String username;
    private String name;
    private String email;
    private String password;

    @DBRef
    private List<Order> orders;

    @Field("role")
    private Role role;
}