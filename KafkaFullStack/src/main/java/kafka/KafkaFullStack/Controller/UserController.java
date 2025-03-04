package kafka.KafkaFullStack.Controller;

import kafka.KafkaFullStack.Dto.UserDto;
import kafka.KafkaFullStack.Model.User;
import kafka.KafkaFullStack.Service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:4200")
public class UserController {

    private final UserService userService;

    @PutMapping("update/{id}")
    public ResponseEntity<User> updateUser(@RequestBody UserDto userDto, @PathVariable String id) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.updateUser(userDto, id));
    }

    @GetMapping("{username}")
    public ResponseEntity<User> getUser(@PathVariable String username) {
        return ResponseEntity.status(HttpStatus.OK).body(userService.getUser(username));
    }
}