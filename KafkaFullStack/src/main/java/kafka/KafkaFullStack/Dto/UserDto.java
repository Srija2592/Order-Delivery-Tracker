package kafka.KafkaFullStack.Dto;

import kafka.KafkaFullStack.Model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDto {
    private String name;

    private String username;

    private String email;

    private Role role;
}
