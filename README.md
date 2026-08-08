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
- **Apache Kafka** (topic: `delivery-locations`)
- **MongoDB Atlas**

---

## Project Structure

```
Order-Delivery-Tracker/
├── KafkaFullStack/          # Main backend (Auth + User Orders) - Port 8080
│   └── src/main/java/kafka/KafkaFullStack/
│       ├── Configuration/
│       ├── Controller/      # AuthController, DeliveryController, UserController
│       ├── Dto/
│       ├── Kafka/           # DeliveryProducer, DeliveryLocationConsumer
│       ├── Mapper/
│       ├── Model/           # Order, User, Role, RefreshToken
│       ├── Repository/
│       ├── Security/
│       └── Service/
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

---

## Prerequisites

- **Java 17+**
- **Node.js 16+** & **npm**
- **Apache Kafka** running on `localhost:9092`
- **MongoDB Atlas** account (or local MongoDB)
- **OpenRouteService** API key (free tier available)

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/Srija2592/Order-Delivery-Tracker.git
cd Order-Delivery-Tracker
```

### 2. Start Kafka

Make sure Kafka is running locally:

```bash
# Example with Kafka in KRaft mode or Zookeeper
# Create the required topic
kafka-topics.sh --create --topic delivery-locations --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

### 3. Configure Backend Services

Update the MongoDB URI and other properties if needed:

**KafkaFullStack** → `src/main/resources/application.properties`  
**Delivery** → `src/main/resources/application.properties`

```properties
spring.data.mongodb.uri=mongodb+srv://<user>:<password>@cluster0.xxxxx.mongodb.net/KafkaFullStack?retryWrites=true&w=majority
spring.kafka.bootstrap-servers=localhost:9092
server.port=8080   # or 8081 for Delivery service
```

> **Note:** The Delivery service also requires an OpenRouteService API key:
> ```properties
> openrouteservice.api.key=YOUR_API_KEY
> ```

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

1. **User** creates an order → status: `In Transit`
2. **Delivery agent** assigns the order → status: `Shipped`
3. **Delivery agent** starts location update → Kafka streams coordinates every ~5 seconds
4. Frontend receives updates via WebSocket/STOMP and updates the Leaflet map
5. When destination is reached → status: `Delivered`

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

---

## Future Improvements

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
