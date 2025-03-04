package kafka.KafkaFullStack.Service;

import kafka.KafkaFullStack.Model.RefreshToken;
import kafka.KafkaFullStack.Repository.RefreshTokenRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Transactional
@AllArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshToken generateRefreshToken() {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setCreatedDate(LocalDate.now());

        return refreshTokenRepository.save(refreshToken);
    }

    void validateRefreshToken(String token) throws Exception {
        refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new Exception("Invalid refresh Token"));
    }

    public void deleteRefreshToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }
}
