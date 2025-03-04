package kafka.KafkaFullStack.Repository;
import kafka.KafkaFullStack.Model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findAllByUsername(String userId);
}