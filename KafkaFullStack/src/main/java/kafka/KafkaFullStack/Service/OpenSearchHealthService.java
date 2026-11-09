package kafka.KafkaFullStack.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchHealthService implements CommandLineRunner {

    private final OpenSearchClient openSearchClient;

    @Override
    public void run(String... args) throws Exception {
        var info = openSearchClient.info();

        log.info("Connected to OpenSearch!");
        log.info("Cluster: {}", info.clusterName());
        log.info("Version: {}", info.version().number());
    }
}