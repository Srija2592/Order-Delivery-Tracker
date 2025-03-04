package kafka.KafkaFullStack.Repository;
import kafka.KafkaFullStack.Model.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken,String> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByToken(String token);
}
