# KafkaChat

A real-time chat application powered by Apache Kafka, with a Spring Boot backend and an Android client.

## Overview

KafkaChat delivers real-time messaging using Kafka for message streaming, WebSockets for live updates, and JWT for authentication. Messages are persisted in PostgreSQL and streamed through Kafka topics.

## Architecture

- **Backend** (`kafkachat-backend`): Spring Boot 3.2, Java 17 — REST API, WebSockets, Kafka producer/consumer, JWT auth, PostgreSQL
- **Android app** (`app`): Kotlin, Android (minSdk 29) — Retrofit, OkHttp, WebSocket client, Room, Hilt
- **Infrastructure**: Docker Compose — PostgreSQL, Zookeeper, Kafka, Kafka UI

## Prerequisites

- **Docker & Docker Compose** — to run backend and infrastructure
- **Java 17** — for building/running the backend locally (optional)
- **Maven** — for backend build (optional)
- **Android Studio** — for building and running the Android app
- **Node/Gradle** — project uses Gradle for the Android module

**→ Full backend setup:** See **[BACKEND_SETUP.md](BACKEND_SETUP.md)** for a step-by-step guide (Docker and local run, configuration, troubleshooting).

## Quick Start

### 1. Start the backend and services (Docker)

From the project root:

```bash
docker-compose up -d
```

Or on Windows, use the helper script:

```bash
start-server.bat
```

This starts:

| Service    | Port | Description        |
|-----------|------|--------------------|
| Backend   | 8080 | REST API & WebSocket |
| PostgreSQL| 5432 | Database           |
| Kafka     | 9092 | Message broker     |
| Kafka UI  | 8081 | Kafka management UI |
| Zookeeper | 2181 | Kafka coordination |

### 2. Backend URL

- **REST/WebSocket (from host):** `http://localhost:8080`
- **WebSocket endpoint:** `ws://localhost:8080/ws`
- From another device on your network, use your machine’s IP (e.g. `http://<your-ip>:8080`). Ensure port 8080 is allowed through the firewall.
- For a **public URL** (e.g. to use the app over the internet or from another network), see [Hosting with ngrok](#hosting-with-ngrok).

### 3. Run the Android app

1. Open the project in Android Studio (open the `KafkaChat` folder).
2. Build and run the `app` module on a device or emulator.
3. In the app, set the server URL to your backend (e.g. `http://<your-ip>:8080`).
4. Register/login and start chatting.

## Hosting with ngrok

Use [ngrok](https://ngrok.com/) to expose your local backend to the internet. This is useful when:

- Testing the Android app on a physical device that isn’t on the same Wi‑Fi (e.g. mobile data or another network)
- Sharing a demo without opening your home/office firewall
- Getting a public HTTPS URL (ngrok provides TLS by default)

### Setup

1. **Install ngrok**  
   Download from [ngrok.com/download](https://ngrok.com/download) or install via package manager. Sign up for a free account and run `ngrok config add-authtoken <your-token>` once.

2. **Start your backend**  
   Ensure the app is running (e.g. `docker-compose up -d` so the backend is on port 8080).

3. **Create a tunnel to port 8080**

   ```bash
   ngrok http 8080
   ```

4. **Use the ngrok URL in the app**  
   ngrok will show a public URL like `https://abc123.ngrok-free.app`.

   - In the Android app, set the **server URL** to that base URL, e.g. `https://abc123.ngrok-free.app` (no trailing slash).
   - The app will use the same host for REST and WebSockets (e.g. `wss://abc123.ngrok-free.app/ws`).

### Notes

- **Free tier:** The URL changes each time you restart ngrok. Update the server URL in the app after each restart.
- **Paid plans:** You can get a fixed subdomain so the URL stays the same.
- **CORS:** The backend’s CORS config should already allow your app; if you use a custom frontend, add your ngrok domain to allowed origins.
- **WebSocket:** ngrok supports WebSockets; no extra flags needed for `ngrok http 8080`.

## Docker

The backend and all infrastructure run via **Docker Compose**. You need [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) installed.

### What runs in Docker

| Container   | Image / Build              | Role |
|------------|----------------------------|------|
| **postgres** | `postgres:15-alpine`     | Database for users, chats, messages. Data is stored in a named volume `postgres_data`. |
| **zookeeper** | `confluentinc/cp-zookeeper:7.5.0` | Coordination for Kafka. |
| **kafka**  | `confluentinc/cp-kafka:7.5.0` | Message broker. Accessible on host as `localhost:9092`. |
| **kafka-ui** | `provectuslabs/kafka-ui:latest` | Web UI for Kafka at `http://localhost:8081`. |
| **app**    | Built from `kafkachat-backend/Dockerfile` | Spring Boot backend (REST + WebSocket). |

The backend container waits for Kafka to start and PostgreSQL to be healthy before starting.

### Backend image (Dockerfile)

The backend is built with a **multi-stage Dockerfile** in `kafkachat-backend/`:

1. **Build stage:** `maven:3.8.5-openjdk-17` — copies `pom.xml`, downloads dependencies, copies source, runs `mvn package -DskipTests`.
2. **Run stage:** `eclipse-temurin:17-jdk-jammy` — copies the JAR from the build stage, installs `curl` for healthchecks, runs `java -jar app.jar` on port 8080.

To build only the backend image (without starting anything):

```bash
docker build -t kafkachat-backend:latest ./kafkachat-backend
```

### Compose commands

| Command | Description |
|---------|-------------|
| `docker-compose up -d` | Start all services in the background. |
| `docker-compose up -d postgres zookeeper kafka` | Start only infrastructure (no backend); use this if you run the backend locally with Maven. |
| `docker-compose logs -f` | Stream logs from all services. |
| `docker-compose logs -f app` | Stream only backend logs. |
| `docker-compose ps` | List running containers and ports. |
| `docker-compose down` | Stop and remove containers (volumes are kept; DB data persists). |
| `docker-compose down -v` | Stop and remove containers and volumes (deletes DB data). |
| `docker-compose build app && docker-compose up -d` | Rebuild the backend image and restart the stack. |

### Environment variables (backend container)

The backend container gets its config from `docker-compose.yml`:

- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/chat_db`
- `SPRING_DATASOURCE_USERNAME=rocky`
- `SPRING_DATASOURCE_PASSWORD=rocky123`

Override these in `docker-compose.yml` or via a `.env` file if you need different settings.

### Volumes

- **postgres_data:** PostgreSQL data directory. Survives `docker-compose down`; removed only with `docker-compose down -v`.

## Project structure

```
KafkaChat/
├── app/                    # Android app (Kotlin)
│   └── src/main/...
├── kafkachat-backend/      # Spring Boot backend (Java)
│   ├── src/main/java/com/kafkachat/
│   │   ├── config/         # Kafka, WebSocket, Security, CORS
│   │   ├── controller/     # Auth, User, Chat
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   ├── service/        # KafkaProducer, KafkaConsumer, Message, User, Chat
│   │   └── util/           # JWT
│   └── src/main/resources/
│       └── application.yml
├── BACKEND_SETUP.md        # Step-by-step backend setup guide
├── docker-compose.yml      # Postgres, Zookeeper, Kafka, Kafka UI, backend
├── kafkachat-backend/
│   └── Dockerfile          # Multi-stage build for backend image
├── start-server.bat        # Windows quick start for Docker
├── build.gradle.kts        # Root Gradle (Android)
└── settings.gradle.kts
```

## Configuration

### Backend (`application.yml`)

- **Database:** PostgreSQL — URL, user, password (defaults match `docker-compose`).
- **Kafka:** `KAFKA_BOOTSTRAP_SERVERS` (default `kafka:29092` for Docker).
- **JWT:** Secret and expiration in `jwt` section — **change the secret in production.**

Override via environment variables when using Docker (as in `docker-compose.yml`).

### Android app

Configure the server base URL in the app (e.g. in login/settings) to point to your backend (host IP or `localhost` for emulator).

## Building locally

### Backend (without Docker)

1. Start only infrastructure:  
   `docker-compose up -d postgres zookeeper kafka`
2. From `kafkachat-backend/`:  
   `mvn spring-boot:run`  
   (Ensure `KAFKA_BOOTSTRAP_SERVERS` and DB URL are correct for your environment.)

### Android

In Android Studio: **Build → Make Project**, then run the `app` configuration.

## Useful commands

- **Docker:** See [Docker](#docker) for `docker-compose` commands (logs, stop, rebuild).
- **Backend (local):** From `kafkachat-backend/`, run `mvn spring-boot:run` (with infrastructure already up).

## Tech stack

| Layer    | Technologies |
|----------|--------------|
| Backend  | Spring Boot 3.2, Spring Kafka, Spring WebSocket, Spring Security, JWT (jjwt), JPA/Hibernate, PostgreSQL |
| Android  | Kotlin, Retrofit, OkHttp, nv-websocket-client, Room, Hilt, Coroutines |
| Messaging| Apache Kafka, Confluent platform 7.5 |

## License

This project is provided as-is for development and learning.
