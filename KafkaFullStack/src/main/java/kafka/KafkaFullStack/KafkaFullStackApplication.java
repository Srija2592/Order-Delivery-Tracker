package kafka.KafkaFullStack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KafkaFullStackApplication {

	public static void main(String[] args) {
		SpringApplication.run(KafkaFullStackApplication.class, args);
		System.out.println("Real time delivery tracker is running...");
	}

}
