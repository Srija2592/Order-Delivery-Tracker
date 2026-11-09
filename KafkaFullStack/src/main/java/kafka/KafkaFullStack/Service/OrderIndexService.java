package kafka.KafkaFullStack.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderIndexService implements CommandLineRunner {

    private final OpenSearchClient openSearchClient;

    @Override
    public void run(String... args) throws Exception {

        boolean exists = openSearchClient.indices()
                .exists(e -> e.index("orders"))
                .value();

        if (!exists) {

            openSearchClient.indices().create(c -> c
                    .index("orders")
                    .mappings(m -> m
                            .properties("eventId", p -> p.keyword(k -> k))
                            .properties("orderId", p -> p.keyword(k -> k))
                            .properties("status", p -> p.keyword(k -> k))
                            .properties("truckId", p -> p.keyword(k -> k))
                            .properties("creationDate", p -> p
                                    .date(d -> d.format("epoch_millis")))
                            .properties("items", p -> p.keyword(k -> k))
                            .properties("srcLat", p -> p.double_(d -> d))
                            .properties("srcLon", p -> p.double_(d -> d))
                            .properties("desLat", p -> p.double_(d -> d))
                            .properties("desLon", p -> p.double_(d -> d))
                    )
            );

            log.info("Created OpenSearch index: orders");

        } else {
            log.info("OpenSearch index already exists: orders");
        }
    }
}