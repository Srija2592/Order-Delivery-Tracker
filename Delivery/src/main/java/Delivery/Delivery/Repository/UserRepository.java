package Delivery.Delivery.Repository;

import Delivery.Delivery.Model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User,String> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

//    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :username")
//    Optional<User> findByUsernameWithRole(@Param("username") String username);

}