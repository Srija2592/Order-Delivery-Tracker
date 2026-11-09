package kafka.KafkaFullStack.Configuration;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Bean
    public OpenSearchTransport openSearchTransport() {
        HttpHost host = new HttpHost(
                "http",
                "localhost",
                9200
        );

        return ApacheHttpClient5TransportBuilder
                .builder(host)
                .build();
    }

    @Bean
    public OpenSearchClient openSearchClient(
            OpenSearchTransport transport) {
        return new OpenSearchClient(transport);
    }
}