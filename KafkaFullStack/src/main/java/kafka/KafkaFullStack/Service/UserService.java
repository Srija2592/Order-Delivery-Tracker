package kafka.KafkaFullStack.Service;

import kafka.KafkaFullStack.Dto.UserDto;
import kafka.KafkaFullStack.Mapper.UserMapper;
import kafka.KafkaFullStack.Model.User;
import kafka.KafkaFullStack.Repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional
@AllArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserDetailsServiceImpl userDetailsService;

    public User updateUser(UserDto userDto, String username) {
        UserDetails user = userDetailsService.loadUserByUsername(userDto.getUsername());
        User user1 = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + user.getUsername()));
        user1.setName(userDto.getName()); // Assuming UserDto has getFullname()
        user1.setEmail(userDto.getEmail());   // Assuming UserDto has getEmail()
        user1.setRole(userDto.getRole());     // Assuming UserDto has getRole()
        return userRepository.save(user1);
    }

    public User getUser(String username) { // Changed return type to User for frontend compatibility
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return user; // Return User object directly
    }
}