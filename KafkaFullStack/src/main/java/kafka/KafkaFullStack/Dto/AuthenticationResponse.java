package kafka.KafkaFullStack.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthenticationResponse {

    private String username;

    private String authenticationToken;

    private String refreshToken;

    private Instant expiresAt;

    private Set<String> role;
}
