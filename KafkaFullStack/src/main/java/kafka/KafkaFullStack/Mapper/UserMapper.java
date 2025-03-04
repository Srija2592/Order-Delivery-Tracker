package kafka.KafkaFullStack.Mapper;

import kafka.KafkaFullStack.Dto.UserDto;
import kafka.KafkaFullStack.Model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class UserMapper {

    public void updateUserFromDto(UserDto userDto, User user) {
        String username = userDto.getUsername();
        String email=userDto.getEmail();
        user.setUsername(username);
        user.setEmail(email);
        user.setName(userDto.getName());
        user.setRole(userDto.getRole());
        System.out.println(user);
    }
}
