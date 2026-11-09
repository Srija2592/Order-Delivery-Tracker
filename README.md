# Order Delivery Tracker

A full-stack delivery tracking application built with **Java 17, Spring Boot, Angular, MongoDB, Apache Kafka, OpenSearch, and FastAPI/Gemini**.

The project supports order creation and assignment, live delivery-location tracking on a Leaflet map, asynchronous order-event indexing, and an AI assistant for order support and delivery-policy questions.

## Architecture

### Live delivery tracking

```text
Delivery backend
    -> Kafka: delivery-locations
    -> Main backend location consumer
    -> STOMP / WebSocket
    -> Angular + Leaflet map
```

### Order events and search indexing

```text
Create order
    -> OrderService (@Transactional)
       -> MongoDB orders collection
       -> MongoDB outbox collection (ORDER_CREATED / PENDING)
    -> Scheduled OutboxPublisher
    -> Kafka: order-events
    -> OrderEventConsumer
    -> OpenSearch: orders index
```

The two Kafka topics have separate responsibilities:
- `delivery-locations`: frequent GPS/location messages used by the real-time map.
- `order-events`: order lifecycle events used by downstream consumers, currently the OpenSearch indexer.

### Services

| Component | Technology | Local port | Purpose |
|---|---|---:|---|
| Main backend | Spring Boot, Java 17, Spring Data MongoDB, Spring Kafka | 8080 | Auth, users, order creation and order APIs |
| Delivery backend | Spring Boot, Java 17, Kafka | 8081 | Delivery assignment and location simulation |
| Frontend | Angular, Leaflet, STOMP/SockJS | 4200 | Customer/delivery UI and live map |
| AI service | FastAPI, Google ADK, Gemini, Chroma | 8000 | Conversational support, live order tools, FAQ/policy retrieval |
| Kafka | Docker | 9092 | Event broker |
| OpenSearch | Docker | 9200 | Order indexing and search foundation |
| MongoDB | MongoDB Atlas | Cloud | Orders, users, and outbox persistence |

## Features

- JWT-based authentication and role-based user/delivery workflows.
- Create and view orders; assign orders to delivery personnel.
- Live delivery-location streaming through Kafka and STOMP/WebSocket.
- Leaflet map for real-time location visualization.
- Route simulation using OpenRouteService.
- MongoDB Transactional Outbox for reliable persistence of order changes and event intent.
- Scheduled publisher sends pending outbox events to Kafka topic `order-events`.
- Kafka consumer indexes order-created events in OpenSearch.
- OpenSearch `orders` index with explicit field mappings.
- FastAPI AI service with Google ADK tools for live order support.
- Chroma retrieval over delivery policy and FAQ documents.

## Technology stack

- **Backend:** Java 17, Spring Boot, Spring Data MongoDB, Spring Kafka, Spring Security, WebSocket/STOMP, Gradle.
- **Frontend:** Angular, TypeScript, Angular Material, Leaflet, SockJS, STOMP.
- **Messaging/search:** Apache Kafka, OpenSearch Java client.
- **Data:** MongoDB Atlas.
- **AI:** Python 3.11+, FastAPI, Google ADK, Gemini, Chroma, sentence-transformers.

## Repository structure

```text
Order-Delivery-Tracker/
├── KafkaFullStack/       # Main Spring Boot backend (8080)
├── Delivery/             # Delivery Spring Boot backend (8081)
├── delivery-tracker/     # Angular frontend (4200)
├── ai-service/           # FastAPI + Google ADK service (8000)
└── docs/
    └── DEVELOPMENT_GUIDE.md
```

## Quick start (Windows PowerShell)

### 1. Clone

```powershell
git clone https://github.com/Srija2592/Order-Delivery-Tracker.git
cd Order-Delivery-Tracker
```

### 2. Start infrastructure

Start Docker Desktop, then start the existing local containers:

```powershell
docker start kafka
docker start opensearch
docker ps
```

If a container has not been created yet, follow the first-time setup commands in the [Local Development Guide](docs/DEVELOPMENT_GUIDE.md).

Check Kafka topics:

```powershell
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Create topics only if they do not already exist:

```powershell
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic delivery-locations --partitions 3 --replication-factor 1
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic order-events --partitions 3 --replication-factor 1
```

Check OpenSearch:

```powershell
curl.exe http://localhost:9200
```

### 3. Resume MongoDB Atlas if paused

Open the [MongoDB Atlas project](https://cloud.mongodb.com/v2/6778177b3a3c2a6560ccbc2e#/overview), open the cluster, and choose **Resume** if the cluster is paused. Wait until it is ready. Confirm Network Access and your local connection string.

### 4. Run the main backend

```powershell
cd KafkaFullStack
.\gradlew.bat bootRun
```

### 5. Run the delivery backend

In a separate terminal:

```powershell
cd Delivery
.\gradlew.bat bootRun
```

### 6. Run Angular

In a separate terminal:

```powershell
cd delivery-tracker
npm install
npx ng serve
```

Open [http://localhost:4200](http://localhost:4200).

### 7. Run the AI service (optional)

Create a Gemini API key in [Google AI Studio](https://aistudio.google.com/api-keys). From the `ai-service` directory:

```powershell
py -3.11 -m venv .venv
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Create `.env` in `ai-service`:

```dotenv
GEMINI_API_KEY=your-gemini-api-key
GEMINI_MODEL=your-supported-gemini-model
ORDER_SERVICE_URL=http://localhost:8080
```

Start FastAPI:

```powershell
python -m uvicorn app.main:app --reload
```

If activation is unavailable, run:

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload
```

AI API docs: [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs).

## Transactional Outbox and OpenSearch

When an order is created, `OrderService.createOrder()` writes the order and an `ORDER_CREATED` event to MongoDB in the same transaction. The outbox record starts as `PENDING`.

The scheduled `OutboxPublisher` polls pending events every five seconds, publishes each payload to `order-events`, and marks it `PUBLISHED` after Kafka acknowledges the send. `OrderEventConsumer` reads the JSON event and indexes it in OpenSearch. The `OrderIndexService` creates the `orders` index and mappings when missing.

Useful verification commands:

```powershell
# Topics
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# Topic details
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic order-events

# OpenSearch index and mapping
curl.exe http://localhost:9200/_cat/indices?v
curl.exe "http://localhost:9200/orders/_mapping?pretty"

# Fetch an indexed order
curl.exe "http://localhost:9200/orders/_doc/ORDER_ID?pretty"
```

Indexing is asynchronous. Check the Spring Boot logs if the document is not visible immediately.

### Current implementation notes

- MongoDB transactions require Atlas/replica-set transaction support.
- The current outbox publisher is a basic scheduled poller; it does not yet implement distributed event claiming for multiple backend instances.
- A crash after Kafka acknowledges an event but before the outbox status is updated can lead to republishing.
- The consumer uses `orderId` as the OpenSearch document ID, so repeated indexing of the same create event overwrites the same document.
- A production-ready version should add bounded retries, safe multi-instance claiming, event versioning/ordering, explicit idempotency, monitoring, and a dead-letter topic/recovery flow. A DLQ is not currently configured.

## API overview

The main backend exposes authentication and user/order APIs under `/api/auth` and `/api/user`. The delivery backend exposes delivery assignment and location APIs under `/api/deliveries`. See the controllers and each service's local API configuration for the exact route/security details.

The AI service exposes:
- `GET /health`
- `POST /chat`
- Interactive Swagger docs at `/docs`

## Security

- Never commit MongoDB credentials, Kafka credentials, Gemini/OpenRouteService API keys, JWT private keys, or local `.env` files.
- Use environment variables or a secrets manager for local/deployed configuration.
- Rotate credentials that have previously been committed or exposed, then remove the hardcoded values from tracked configuration and Git history as appropriate.
- The documented OpenSearch container disables its security plugin for local-only development. Do not expose it publicly or use that setting in production.

## Detailed documentation

See **[docs/DEVELOPMENT_GUIDE.md](docs/DEVELOPMENT_GUIDE.md)** for:
- Environment setup and Windows PowerShell commands.
- Kafka topic management and console tools.
- OpenSearch Docker startup, index verification, and mappings.
- MongoDB Atlas resume/setup.
- Transactional Outbox design and delivery guarantees.
- AI service virtual environment, Gemini key, and FastAPI startup.
- End-to-end verification and troubleshooting.

## License

See [LICENSE](LICENSE).
