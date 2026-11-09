# Local Development and Architecture Guide

This guide documents the current local development setup for the Order Delivery Tracker, including the real-time tracking flow, MongoDB Transactional Outbox, Kafka, OpenSearch, and the FastAPI/Gemini AI service.

> Commands are written for Windows PowerShell unless stated otherwise. Start Docker Desktop before running the containers.

## 1. Architecture at a glance

The project contains two Spring Boot services, an Angular application, and a Python AI service.

| Component | Technology | Local address / port | Responsibility |
|---|---|---:|---|
| Main backend | Java 17, Spring Boot, Spring Data MongoDB, Spring Kafka | `localhost:8080` | Authentication, users, order creation and order APIs |
| Delivery backend | Java 17, Spring Boot, Kafka | `localhost:8081` | Delivery assignment and location simulation |
| Frontend | Angular, Leaflet, STOMP/SockJS | `localhost:4200` | Customer and delivery dashboards; live map |
| Kafka | Docker container named `kafka` | `localhost:9092` | Event transport |
| MongoDB | MongoDB Atlas | Atlas cluster | Orders, users, and outbox events |
| OpenSearch | Docker container named `opensearch` | `localhost:9200` | Order document indexing and search foundation |
| AI service | Python 3.11+, FastAPI, Google ADK, Chroma | `localhost:8000` | Conversational order support and policy/FAQ retrieval |

### Two independent Kafka flows

**Live location flow**

```text
Delivery service
    |
    | delivery location messages
    v
Kafka: delivery-locations
    |
    v
Main backend location consumer
    |
    | STOMP over WebSocket
    v
Angular + Leaflet live tracking map
```

**Order lifecycle / search indexing flow**

```text
Create order request
    |
    v
OrderService.createOrder()  (@Transactional)
    |
    +------> MongoDB: orders
    |
    +------> MongoDB: outbox (ORDER_CREATED, PENDING)
                         |
                         v
                 OutboxPublisher
                 (polls every 5 seconds)
                         |
                         v
                 Kafka: order-events
                         |
                         v
                 OrderEventConsumer
                         |
                         v
                 OpenSearch: orders index
```

The two topics have different purposes. Do not send GPS location payloads to `order-events`, or order lifecycle JSON to `delivery-locations`.

## 2. Prerequisites

- Docker Desktop (with its Linux container engine running).
- Java 17.
- Node.js and npm; Angular CLI compatible with the project.
- Python 3.11.
- Git.
- A MongoDB Atlas cluster that supports multi-document transactions.
- A Gemini API key for the AI service.
- An OpenRouteService API key for route simulation.

### Optional WSL 2 resource configuration

If Docker reports memory/resource problems on Windows, you can configure WSL 2. Create or edit `%UserProfile%\.wslconfig`:

```ini
[wsl2]
memory=4GB
processors=2
swap=2GB
localhostForwarding=true
```

Apply it from PowerShell:

```powershell
wsl --shutdown
```

Restart Docker Desktop afterwards. Adjust the values to the resources available on your machine.

## 3. Clone the repository

```powershell
git clone https://github.com/Srija2592/Order-Delivery-Tracker.git
cd Order-Delivery-Tracker
```

## 4. MongoDB Atlas

The main backend uses MongoDB Atlas. The Transactional Outbox writes the order document and its outbox event in one MongoDB transaction. Therefore, use an Atlas replica-set cluster (or another MongoDB deployment that supports transactions); a standalone MongoDB server is not sufficient.

1. Open the [MongoDB Atlas project](https://cloud.mongodb.com/v2/6778177b3a3c2a6560ccbc2e#/overview).
2. Open the cluster.
3. If Atlas has paused the cluster, select **Resume** and wait until its status is ready.
4. Check that your current IP is allowed in Network Access.
5. Obtain the application connection string and configure it locally.
6. Keep the database username/password out of Git.

Use your own connection string in the local configuration. Do not copy credentials into this guide or commit them to the repository.

## 5. Start Kafka and create topics

This guide assumes the local Kafka Docker container already exists and is named `kafka`.

Start Docker Desktop, then run:

```powershell
docker start kafka
docker ps
```

Check that Kafka responds and list topics:

```powershell
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

The project uses these topics:

| Topic | Purpose | Current local configuration |
|---|---|---|
| `delivery-locations` | Frequent GPS/location messages | Existing topic; inspect its partition count with `--describe` |
| `order-events` | Order lifecycle events from the outbox | 3 partitions, replication factor 1 |

Create a topic only if it does not already exist:

```powershell
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic delivery-locations --partitions 3 --replication-factor 1
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic order-events --partitions 3 --replication-factor 1
```

If Kafka reports that a topic already exists, that is expected; do not delete it just to rerun setup.

Inspect topic configuration and partition leaders:

```powershell
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic delivery-locations
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic order-events
```

List consumer groups:

```powershell
docker exec -it kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
```

Inspect the order event consumer group:

```powershell
docker exec -it kafka kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group order-events-consumer-group
```

### Kafka console testing

To inspect order lifecycle records without using the application consumer group:

```powershell
docker exec -it kafka kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-events --from-beginning --group order-events-inspection
```

Stop the console consumer with Ctrl+C. The `order-events` payloads are JSON event records. Avoid manually producing arbitrary text to this topic while the application consumer is running, because the consumer expects an `OrderCreatedEvent` JSON payload.

For a quick location-topic smoke test:

```powershell
docker exec -it kafka kafka-console-producer.sh --bootstrap-server localhost:9092 --topic delivery-locations
```

Type a test message only if you know the exact location message format expected by the location consumer. Stop with Ctrl+C.

> This is a single-broker local development setup. Replication factor 1 is not fault tolerant and is not a production Kafka configuration.

## 6. Start OpenSearch

The main backend connects to OpenSearch at `http://localhost:9200`. The current local development configuration disables the security plugin; use this only for local development, not for an internet-accessible or production deployment.

### First-time container creation

Run once:

```powershell
docker run -d --name opensearch -p 9200:9200 -p 9600:9600 -e "discovery.type=single-node" -e "DISABLE_SECURITY_PLUGIN=true" -e "OPENSEARCH_JAVA_OPTS=-Xms512m -Xmx512m" opensearchproject/opensearch:3.9.0
```

### Start an existing container

On later runs:

```powershell
docker start opensearch
docker ps
```

Verify the node:

```powershell
curl.exe http://localhost:9200
curl.exe http://localhost:9200/_cluster/health?pretty
```

The backend has an OpenSearch client configured in `OpenSearchConfig`. `OpenSearchHealthService` checks connectivity during application startup. `OrderIndexService` creates the `orders` index and mappings if the index does not exist.

### Verify the index and documents

```powershell
curl.exe http://localhost:9200/_cat/indices?v
curl.exe http://localhost:9200/orders?pretty
curl.exe "http://localhost:9200/orders/_mapping?pretty"
```

After creating an order and allowing the outbox publisher/consumer to process it, query the document using its order ID:

```powershell
curl.exe "http://localhost:9200/orders/_doc/ORDER_ID?pretty"
```

Replace `ORDER_ID` with the actual order ID returned by the application. Indexing is asynchronous: the order is saved first, then the publisher sends the event, and the consumer indexes it. If the document is not visible immediately, check the backend logs and retry the query after a short delay.

### Current index mapping

The `orders` index is initialized with these fields:

| Field | OpenSearch type |
|---|---|
| `eventId` | keyword |
| `orderId` | keyword |
| `status` | keyword |
| `truckId` | keyword |
| `creationDate` | date (`epoch_millis`) |
| `items` | keyword |
| `srcLat`, `srcLon`, `desLat`, `desLon` | double |

The consumer uses `orderId` as the OpenSearch document ID. Reprocessing an `ORDER_CREATED` event for the same order therefore writes to the same document ID rather than creating a second document.

## 7. Configure and run the Spring Boot backends

Both backend projects use Gradle wrappers. Credentials are deliberately read from environment variables; do not put real values in tracked \`application.properties\` files.

### Generate a fresh JWT signing key pair (one-time local setup)

The two Spring Boot services must use the same public/private RSA key pair so that the Delivery service can validate tokens issued by the main backend.

From the repository root, run this once after pulling the security cleanup:

\`\`\`powershell
openssl genpkey -algorithm RSA -out .\KafkaFullStack\src\main\resources\app.key -pkeyopt rsa_keygen_bits:2048
openssl pkey -in .\KafkaFullStack\src\main\resources\app.key -pubout -out .\KafkaFullStack\src\main\resources\app.pub
Copy-Item .\KafkaFullStack\src\main\resources\app.key .\Delivery\src\main\resources\app.key
Copy-Item .\KafkaFullStack\src\main\resources\app.pub .\Delivery\src\main\resources\app.pub
\`\`\`

The \`app.key\` and \`app.pub\` files are ignored by Git. Keep the private key private. Do not upload either key to GitHub. If OpenSSL is not available, install it or use an equivalent trusted RSA key-generation process that produces a PKCS#8 private key and an X.509 public key in PEM format.

### Main backend — port 8080

Open a new PowerShell terminal from the repository root and set the environment variables for that terminal:

\`\`\`powershell
$env:MONGODB_URI = "mongodb+srv://<db-user>:<db-password>@<cluster>/<database>?retryWrites=true&w=majority"
$env:MONGODB_DATABASE = "<database-name>"
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:KAFKA_SECURITY_PROTOCOL = "PLAINTEXT"
\`\`\`

For the local Docker Kafka broker, no SASL username/password is required. For a hosted Confluent Kafka cluster, use its bootstrap server and set the following instead (obtain fresh credentials from the provider):

\`\`\`powershell
$env:KAFKA_BOOTSTRAP_SERVERS = "<bootstrap-server>:9092"
$env:KAFKA_SECURITY_PROTOCOL = "SASL_SSL"
$env:KAFKA_SASL_MECHANISM = "PLAIN"
$env:KAFKA_SASL_JAAS_CONFIG = 'org.apache.kafka.common.security.plain.PlainLoginModule required username="<api-key>" password="<api-secret>";'
\`\`\`

Then build and run:

\`\`\`powershell
cd KafkaFullStack
.\gradlew.bat clean build
.\gradlew.bat bootRun
\`\`\`

### Delivery backend — port 8081

Open a second PowerShell terminal from the repository root. Set the same MongoDB, Kafka, and JWT key-file configuration, plus the OpenRouteService key:

\`\`\`powershell
$env:MONGODB_URI = "mongodb+srv://<db-user>:<db-password>@<cluster>/<database>?retryWrites=true&w=majority"
$env:MONGODB_DATABASE = "<database-name>"
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
$env:KAFKA_SECURITY_PROTOCOL = "PLAINTEXT"
$env:OPENROUTESERVICE_API_KEY = "<your-new-api-key>"
\`\`\`

For a hosted Kafka broker, set the same four Kafka variables shown in the Confluent example above.

Then run:

\`\`\`powershell
cd Delivery
.\gradlew.bat clean build
.\gradlew.bat bootRun
\`\`\`

### Environment variable reference

| Variable | Used by | Purpose |
|---|---|---|
| \`MONGODB_URI\` | Both backends | MongoDB Atlas connection string |
| \`MONGODB_DATABASE\` | Both backends | Database name |
| \`KAFKA_BOOTSTRAP_SERVERS\` | Both backends | Kafka bootstrap address; defaults to \`localhost:9092\` |
| \`KAFKA_SECURITY_PROTOCOL\` | Both backends | Defaults to \`PLAINTEXT\` for local Docker Kafka |
| \`KAFKA_SASL_MECHANISM\` | Both backends | Optional SASL mechanism for hosted Kafka |
| \`KAFKA_SASL_JAAS_CONFIG\` | Both backends | Optional SASL JAAS login configuration |
| \`OPENROUTESERVICE_API_KEY\` | Delivery backend | Route calculation API key |
| \`JWT_PUBLIC_KEY\` / \`JWT_PRIVATE_KEY\` | Both backends | Optional overrides for the default local classpath key files |

PowerShell environment variables apply only to the current terminal process and its child processes. Set them again in each backend terminal after opening a new terminal. For persistent local setup, use a local, untracked environment-loading method; never commit actual values.

### OpenSearch

The main backend connects to the local OpenSearch node at \`http://localhost:9200\`. Start the \`opensearch\` Docker container before starting the main backend. The index initializer and health check run during application startup.

### Important secret-handling rules

- Never commit MongoDB URIs/passwords, Kafka SASL credentials, OpenRouteService/Gemini API keys, JWT private keys, or local \`.env\` files.
- The committed \`app.key\` and \`app.pub\` files were removed. Generate a new pair locally as shown above.
- Rotate credentials that were previously committed or exposed. Removing a value from the latest version of a file does not remove it from earlier Git commits.
- The local Docker Kafka example uses PLAINTEXT and is intended only for localhost development.
- The OpenSearch Docker setup disables its security plugin; do not expose that node publicly or use this setting in production.

## 8. Run the Angular frontend

```powershell
cd delivery-tracker
npm install
npx ng serve
```

Open [http://localhost:4200](http://localhost:4200).

The frontend uses Leaflet for map rendering and STOMP over SockJS for live location updates.

## 9. Run the Python AI service

The AI service uses FastAPI, Google ADK, Gemini, Chroma, and sentence-transformer embeddings. It can answer delivery policy/FAQ questions using local documents and call the Spring Boot order APIs for live order information.

### Create the Gemini API key

1. Open [Google AI Studio API keys](https://aistudio.google.com/api-keys).
2. Create an API key.
3. Store it in the local `.env` file. Never commit the key.

### Prepare the Python environment (Windows PowerShell)

Run from the repository's `ai-service` directory:

```powershell
cd ai-service
py -3.11 -m venv .venv
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

If PowerShell activation is blocked, the execution-policy command above applies only to the current PowerShell process. Alternatively, invoke the virtual environment's Python directly without activating it.

Create `ai-service/.env`:

```dotenv
GEMINI_API_KEY=replace-with-your-key
GEMINI_MODEL=replace-with-a-supported-model-name
ORDER_SERVICE_URL=http://localhost:8080
```

`ORDER_SERVICE_URL` defaults to `http://localhost:8080` if omitted. The AI service needs the main backend running for authenticated live order tools. Policy/FAQ retrieval uses the documents in `ai-service/data/`.

### Start FastAPI

From the `ai-service` directory:

```powershell
python -m uvicorn app.main:app --reload
```

If the virtual environment is not activated:

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload
```

Use the standard two-hyphen `--reload` option.

Endpoints:
- Health: [http://127.0.0.1:8000/health](http://127.0.0.1:8000/health)
- Swagger UI: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- Chat: `POST http://127.0.0.1:8000/chat`

The chat request includes a message and the signed-in user's JWT access token. Use the interactive API docs to test with a valid token. On first startup, sentence-transformers may download its embedding model and Chroma may create its local index; allow time for this initial setup.

If startup reports `ModuleNotFoundError: No module named 'chromadb'`, install the requirements using the Python interpreter from this virtual environment:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

## 10. End-to-end verification

Start the dependencies and services in this order:

1. Docker Desktop.
2. MongoDB Atlas cluster (resume it if paused).
3. Kafka container and verify both topics.
4. OpenSearch container and verify `http://localhost:9200`.
5. Main Spring Boot backend (8080).
6. Delivery Spring Boot backend (8081).
7. Angular frontend (4200).
8. AI service (8000), when testing chat.

### Verify the order indexing pipeline

1. Submit an order through the application/API.
2. Confirm the order exists in MongoDB.
3. Inspect the MongoDB `outbox` collection. The event should initially be `PENDING`.
4. The scheduled publisher checks pending events every five seconds, publishes to `order-events`, then updates the outbox status to `PUBLISHED` after Kafka acknowledges the send.
5. Check the main backend logs for the publisher and consumer messages.
6. Query OpenSearch for the document using the order ID.

Example log sequence:

```text
Published event: <event-id>
Received ORDER_CREATED event for order: <order-id>
Order indexed successfully. Order ID: <order-id>
```

### Verify live location tracking

1. Start the Delivery backend.
2. Sign in as a delivery user and start a delivery/location simulation.
3. Confirm location messages are flowing through `delivery-locations`.
4. Confirm the main backend location consumer forwards updates over STOMP/WebSocket.
5. Confirm the Angular Leaflet map updates.

## 11. How the Transactional Outbox works

A common reliability problem occurs if an application writes an order to MongoDB and separately publishes an event to Kafka:

- If MongoDB succeeds but Kafka is unavailable, the order exists but downstream systems may never hear about it.
- If Kafka succeeds but the database write fails, consumers may process an event for an order that does not exist.

The outbox pattern stores the business change and the event-to-be-published in the same database transaction.

In this project:

1. `OrderService.createOrder()` saves the order.
2. It creates an `OrderCreatedEvent` and stores its JSON payload in the MongoDB `outbox` collection with status `PENDING`.
3. Both writes are covered by the MongoDB transaction.
4. `OutboxPublisher` polls pending records every five seconds and publishes them to `order-events`.
5. After Kafka acknowledges the send, the publisher changes the outbox status to `PUBLISHED`.
6. `OrderEventConsumer` deserializes the event and indexes it in OpenSearch.

### Delivery guarantees and current limitations

The current implementation is a learning/development version, not a fully production-hardened outbox:

- A crash after Kafka acknowledges a record but before MongoDB is updated can cause the event to be published again.
- The OpenSearch consumer uses the order ID as the document ID, which makes repeated indexing of the same order-created payload overwrite the same document.
- The publisher currently polls pending records without a distributed claim/lease. Running multiple application instances can therefore publish the same pending event.
- A production deployment should add bounded retries, a dead-letter topic and recovery process, safe multi-instance claiming, event versioning/ordering, monitoring, and explicit consumer idempotency.
- A dead-letter topic is not configured by the current code; do not assume failed events are automatically sent to a DLQ.

## 12. Troubleshooting

| Symptom | Checks |
|---|---|
| Kafka connection refused | Confirm Docker Desktop is running; run `docker ps`; run `docker start kafka`; verify `localhost:9092` and topic list |
| Topic already exists | Run `kafka-topics.sh --list`; do not recreate or delete an existing topic unnecessarily |
| Kafka partition has no leader | Inspect `--describe`, broker/container logs, and broker health; a preferred election cannot fix a broker that is unavailable |
| OpenSearch connection refused | Run `docker start opensearch`; inspect `docker logs opensearch`; check `curl.exe http://localhost:9200` |
| OpenSearch index missing | Check backend startup logs for `OrderIndexService`; check `/_cat/indices?v`; verify the index initializer ran |
| OpenSearch document not found immediately | Indexing is asynchronous; inspect publisher/consumer logs and retry the document GET |
| MongoDB transaction error | Confirm Atlas cluster is running and is a replica set; verify Network Access and connection string |
| AI service cannot import Chroma | Install `requirements.txt` with the active venv's Python |
| AI service cannot call order APIs | Confirm main backend is running at port 8080 and the request includes a valid access token |
| WebSocket/CORS error | Confirm the frontend origin is allowed in the backend WebSocket configuration |
| Leaflet marker icons missing | Verify marker image assets and their configured paths in the Angular app |

Useful container commands:

```powershell
docker ps -a
docker logs --tail 100 kafka
docker logs --tail 100 opensearch
docker start kafka
docker start opensearch
```

## 13. Useful local URLs

- Angular: [http://localhost:4200](http://localhost:4200)
- Main backend: [http://localhost:8080](http://localhost:8080)
- Delivery backend: [http://localhost:8081](http://localhost:8081)
- AI service Swagger: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
- OpenSearch node: [http://localhost:9200](http://localhost:9200)
- [MongoDB Atlas cluster](https://cloud.mongodb.com/v2/6778177b3a3c2a6560ccbc2e#/overview)
- [Google AI Studio API keys](https://aistudio.google.com/api-keys)

## 14. Key source files

| File | Purpose |
|---|---|
| `KafkaFullStack/.../Configuration/MongoTransactionConfig.java` | MongoDB transaction manager |
| `KafkaFullStack/.../Service/OrderService.java` | Saves order and outbox event in one transaction |
| `KafkaFullStack/.../Model/OutboxEvent.java` | Outbox record model |
| `KafkaFullStack/.../Repository/OutboxEventRepository.java` | Retrieves pending events |
| `KafkaFullStack/.../Kafka/OutboxPublisher.java` | Publishes pending events to Kafka |
| `KafkaFullStack/.../Kafka/OrderCreatedEvent.java` | Event payload contract |
| `KafkaFullStack/.../Kafka/OrderEventConsumer.java` | Consumes order events and indexes documents |
| `KafkaFullStack/.../Configuration/OpenSearchConfig.java` | OpenSearch Java client |
| `KafkaFullStack/.../Service/OrderIndexService.java` | Creates the orders index and mappings |
| `KafkaFullStack/.../Service/OpenSearchHealthService.java` | Checks OpenSearch connectivity |
| `ai-service/app/` | FastAPI application, ADK agents, tools, and retrieval services |
| `ai-service/data/` | Delivery FAQ and policy documents |

---

For the repository overview and quick start, see the [main README](../README.md).
