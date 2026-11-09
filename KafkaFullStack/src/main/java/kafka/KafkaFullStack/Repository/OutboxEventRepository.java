package kafka.KafkaFullStack.Repository;

import kafka.KafkaFullStack.Model.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OutboxEventRepository
        extends MongoRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
}