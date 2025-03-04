package kafka.KafkaFullStack.Controller;
import jakarta.validation.Valid;
import kafka.KafkaFullStack.Dto.AuthenticationResponse;
import kafka.KafkaFullStack.Dto.LoginRequest;
import kafka.KafkaFullStack.Dto.RefreshTokenRequest;
import kafka.KafkaFullStack.Dto.RegisterRequest;
import kafka.KafkaFullStack.Service.AuthService;
import kafka.KafkaFullStack.Service.RefreshTokenService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.OK;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor

public class AuthController {

    private final AuthService authService;

    private final RefreshTokenService refreshTokenService;

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody RegisterRequest registerRequest){
        authService.signup(registerRequest);
        return new ResponseEntity<>("User registration successful", OK);
    }



    @PostMapping("/login")
    public AuthenticationResponse login(@RequestBody LoginRequest loginRequest){
        return authService.login(loginRequest);

    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        refreshTokenService.deleteRefreshToken(refreshTokenRequest.getRefreshToken());
        return ResponseEntity.status(HttpStatus.OK).body("Refresh Token Deleted Successfully!!");
    }

    @PostMapping("/refresh/token")
    public AuthenticationResponse refreshTokens(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) throws Exception {
        return authService.refreshToken(refreshTokenRequest);
    }

    @GetMapping("/loggedIn")
    public boolean isLoggedIn(){
        System.out.println(authService.isLoggedIn()+".....................................");
        return authService.isLoggedIn();
    }
}

