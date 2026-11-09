# Order Delivery Tracker

A full-stack real-time order delivery tracking system built with **Spring Boot**, **Apache Kafka**, **MongoDB**, and **Angular**.

Users can place orders, delivery personnel can assign and fulfill them, and everyone can track orders live on a map with real-time location updates.

---

## Architecture Overview

```
┌─────────────────────┐     Kafka (delivery-locations)     ┌─────────────────────┐
│   KafkaFullStack    │ ◄─────────────────────────────────► │      Delivery        │
│   (Port 8080)       │                                     │   (Port 8081)       │
│                     │                                     │                     │
│  • Auth (JWT)       │                                     │  • Assign orders    │
│  • Create orders    │                                     │  • Live location    │
│  • Track orders     │                                     │    simulation       │
│  • User management  │                                     │  • OpenRouteService │
└──────────┬──────────┘                                     └──────────┬──────────┘
           │                                                           │
           │              MongoDB Atlas (KafkaFullStack)               │
           └───────────────────────────┬───────────────────────────────┘
                                       │
                          ┌────────────▼────────────┐
                          │   delivery-tracker      │
                          │   (Angular - Port 4200) │
                          │                         │
                          │  • Login / Signup       │
                          │  • Order tracking map   │
                          │  • Delivery dashboard   │
                          │  • Real-time updates    │
                          └─────────────────────────┘
```

### Event-driven order indexing (in progress)

The main backend is being extended with a transactional outbox for order lifecycle events. The intended flow is:

```
Order creation
     |
     v
OrderService (@Transactional)
     |
     +----> MongoDB orders collection
     |
     +----> MongoDB outbox collection (ORDER_CREATED, PENDING)
                                      |
                                      v
                              Outbox Publisher
                                      |
                                      v
                              Kafka: order-events
                                      |
                                      v
                         Future search/indexing consumer
                                      |
                                      v
                              OpenSearch (planned)
```

The existing `delivery-locations` topic remains dedicated to frequent live-location updates and WebSocket delivery. The `order-events` topic is separate and intended for order lifecycle events such as `ORDER_CREATED`, `ORDER_ASSIGNED`, and `ORDER_STATUS_UPDATED`.

**Implementation status:** The MongoDB transaction configuration, order creation service, and outbox document/repository have been added. The scheduled publisher, Kafka consumer, and OpenSearch indexing integration are subsequent implementation steps; do not assume they are active until implemented and tested.

---

## Tech Stack

### Backend
- **Java 17** + **Spring Boot 3.0.11**
- **Spring Security** + JWT (OAuth2 Resource Server)
- **Spring Data MongoDB**
- **Spring Kafka**
- **Lombok** + **MapStruct**
- **OpenRouteService API** (route simulation)

### Frontend
- **Angular 15**
- **Angular Material**
- **Leaflet** + Marker Clustering (maps)
- **STOMP over SockJS** (WebSocket)
- **ngx-toastr** + **ngx-webstorage**

### Infrastructure
- **Apache Kafka** (topics: `delivery-locations`, `order-events` for the outbox event pipeline)
- **MongoDB Atlas** (replica set; multi-document transactions used by the outbox write)
- **OpenSearch** (planned for order search/indexing)

---

## Project Structure

```
Order-Delivery-Tracker/
├── KafkaFullStack/          # Main backend (Auth + User Orders) - Port 8080
│   └── src/main/java/kafka/KafkaFullStack/
│       ├── Configuration/   # MongoTransactionConfig
│       ├── Controller/      # AuthController, DeliveryController, UserController
│       ├── Dto/
│       ├── Kafka/           # DeliveryProducer, DeliveryLocationConsumer, order events
│       ├── Mapper/
│       ├── Model/           # Order, User, Role, RefreshToken, OutboxEvent
│       ├── Repository/      # OrderRepository, OutboxEventRepository
│       ├── Security/
│       └── Service/         # OrderService
│
├── Delivery/                # Delivery personnel backend - Port 8081
│   └── src/main/java/Delivery/Delivery/
│       ├── Configuration/
│       ├── Controller/      # DeliveryController (assign + location update)
│       ├── Dto/
│       ├── Kafka/
│       ├── Model/
│       └── Repository/
│
└── delivery-tracker/        # Angular frontend - Port 4200
    └── src/app/
        ├── auth/            # Login, Signup, AuthGuard
        ├── delivery-orders/ # Delivery agent dashboard
        ├── header/
        ├── home/
        ├── models/
        ├── profile/
        ├── services/        # auth, delivery, user services
        ├── tracker/         # Live map tracking
        ├── unauthorized-page/
        └── update/
```

---

## Features

### User Role (`ROLE_USER`)
- Register / Login
- Create new orders (with destination lat/lon)
- View own orders
- Live track order location on map
- View distance to destination

### Delivery Role (`ROLE_DELIVERY`)
- View unassigned orders
- Assign orders to self
- Start delivery simulation (live location updates via Kafka)
- View assigned orders

### System
- JWT-based authentication with refresh tokens
- Real-time location streaming via Kafka
- Route simulation using OpenRouteService
- Haversine distance calculation
- Role-based route guards on frontend
- Transactional outbox write for order creation (MongoDB order + outbox event committed together)

---

## Prerequisites

- **Java 17+**
- **Node.js 16+** & **npm**
- **Docker** (if running the local Kafka container described below)
- **Apache Kafka** reachable at `localhost:9092`
- **MongoDB Atlas** replica set (or another MongoDB replica set / sharded deployment that supports transactions)
- **OpenRouteService** API key (free tier available)

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/Srija2592/Order-Delivery-Tracker.git
cd Order-Delivery-Tracker
```

### 2. Start Kafka and create topics

Make sure Kafka is running and reachable at `localhost:9092`.

The project uses two separate topics:
- `delivery-locations`: frequent delivery location updates used by the live tracking flow.
- `order-events`: order lifecycle events published from the transactional outbox (publisher implementation is a subsequent step).

If Kafka is running in Docker with a container named `kafka`, list existing topics:

```cmd
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Create the topics if they do not already exist:

```cmd
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic delivery-locations --partitions 1 --replication-factor 1
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --create --topic order-events --partitions 3 --replication-factor 1
```

If you installed Kafka directly, use the `kafka-topics.sh` command from your Kafka installation's `bin` directory instead of `docker exec`.

To verify a topic:

```cmd
docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic order-events
```

For a single-node local Kafka broker, replication factor `1` is appropriate for development. Use a production-appropriate replication factor and broker configuration in deployed environments.

### 3. Configure Backend Services

Update the MongoDB URI and other properties if needed:

**KafkaFullStack** → `src/main/resources/application.properties`  
**Delivery** → `src/main/resources/application.properties`

```properties
spring.data.mongodb.uri=mongodb+srv://<user>:<password>@cluster0.xxxxx.mongodb.net/KafkaFullStack?retryWrites=true&w=majority
spring.kafka.bootstrap-servers=localhost:9092
server.port=8080   # or 8081 for Delivery service
```

> **MongoDB transaction requirement:** The transactional outbox writes the order and its outbox event in one transaction. MongoDB multi-document transactions require a replica set or sharded cluster; a standalone MongoDB server is not sufficient. In the main backend, `MongoTransactionConfig` provides a `MongoTransactionManager`, and `OrderService.createOrder()` is annotated with `@Transactional`.

> **Note:** The Delivery service also requires an OpenRouteService API key:
> ```properties
> openrouteservice.api.key=YOUR_API_KEY
> ```

Do not commit database passwords, Kafka credentials, JWT private keys, or API keys. Supply secrets through environment variables or a secrets manager.

### 4. Run Backend Services

```bash
# Terminal 1 - Main Backend
cd KafkaFullStack
./gradlew bootRun

# Terminal 2 - Delivery Service
cd Delivery
./gradlew bootRun
```

### 5. Run Frontend

```bash
cd delivery-tracker
npm install
ng serve
```

Open [http://localhost:4200](http://localhost:4200)

---

## API Endpoints (Overview)

### KafkaFullStack (Port 8080)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/api/auth/signup` | Register user | Public |
| POST | `/api/auth/login` | Login | Public |
| POST | `/api/user/orders` | Create order | `ROLE_USER` |
| GET | `/api/user/orders/username/{username}` | Get user orders | `ROLE_USER` |
| GET | `/api/user/track/{orderId}` | Track order + distance | Public/User |
| GET | `/api/user/orders/{orderId}` | Get order details | `ROLE_USER` |

### Delivery Service (Port 8081)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/deliveries/orders/non-delivered` | List unassigned orders | Public |
| GET | `/api/deliveries/orders/{username}` | Get assigned orders | `ROLE_DELIVERY` |
| PUT | `/api/deliveries/assign/{orderId}/{username}` | Assign order | `ROLE_DELIVERY` |
| PUT | `/api/deliveries/location` | Start location simulation | `ROLE_DELIVERY` |

---

## Order Lifecycle

1. **User** creates an order → status: `In Transit`.
2. **KafkaFullStack OrderService** saves the order and an `ORDER_CREATED` outbox event in the same MongoDB transaction. The outbox event starts with status `PENDING`.
3. **Outbox publisher (planned)** will publish pending lifecycle events to Kafka topic `order-events` and then mark them `PUBLISHED`.
4. **Delivery agent** assigns the order → status: `Shipped`.
5. **Delivery agent** starts location update → Kafka streams coordinates every ~5 seconds on `delivery-locations`.
6. Frontend receives location updates via WebSocket/STOMP and updates the Leaflet map.
7. When destination is reached → status: `Delivered`.

---

## Transactional Outbox

The outbox pattern prevents the order database write and event publication intent from becoming two unrelated operations.

When an order is created, `OrderService.createOrder()` performs these operations under `@Transactional`:

1. Saves the order in the `orders` collection.
2. Creates an `ORDER_CREATED` event containing the order details.
3. Saves the event in the `outbox` collection with status `PENDING`.

The `MongoTransactionConfig` class configures Spring Data MongoDB's `MongoTransactionManager`. Both writes commit or roll back together, provided the MongoDB deployment supports transactions.

The next component is an outbox publisher that polls pending events and sends them to Kafka. Because a process can fail after Kafka acknowledges a message but before MongoDB records it as published, duplicate publication is possible. Downstream consumers should therefore be idempotent. Publisher retry handling, safe concurrent claiming, a dead-letter strategy, and the consumer are follow-up implementation work.

---

## Roles

```java
public enum Role {
    USER,       // Customers who place & track orders
    DELIVERY,   // Delivery personnel
    ADMIN       // Reserved
}
```

---

## Frontend Routes

| Path | Component | Guard |
|------|-----------|-------|
| `/` | Home | - |
| `/login` | Login | - |
| `/signup` | Signup | - |
| `/tracker` | Live Order Tracker | `ROLE_USER` |
| `/delivery-order` | Delivery Dashboard | `ROLE_DELIVERY` |
| `/profile` | User Profile | - |
| `/unauthorized` | Unauthorized Page | - |

---

## Important Notes

- Default source location for new orders is set to **Hyderabad** (17.4065, 78.4772).
- Location simulation uses the **OpenRouteService Directions API**.
- Kafka message format for location updates:
  ```
  orderId:srcLat:srcLon:curLat:curLon:desLat:desLon:status:timestamp
  ```
- JWT public/private keys are expected in the classpath (`app.pub` / `app.key`).
- The live location topic (`delivery-locations`) and lifecycle event topic (`order-events`) serve different purposes; do not merge their message formats or responsibilities.

---

## Future Improvements

- [ ] Complete and test the scheduled Outbox Publisher
- [ ] Add Kafka consumer for order lifecycle events
- [ ] Add OpenSearch indexing and order search API
- [ ] Add bounded retries, idempotency, event versioning, and dead-letter handling
- [ ] Add Admin dashboard
- [ ] Real GPS integration instead of simulation
- [ ] Push notifications
- [ ] Order history & analytics
- [ ] Docker Compose setup (Kafka + MongoDB + services)
- [ ] Unit & integration tests

---

## Author

**Srija2592**

---

## License

This project is open source and available under the [MIT License](LICENSE).
