package Delivery.Delivery.Repository;
import Delivery.Delivery.Model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OrderRepository extends MongoRepository<Order, String> {
    List<Order> findAllByCreator_username(String userId);

    List<Order> findAllByStatus(String status);
}