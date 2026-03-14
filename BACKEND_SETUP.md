# KafkaChat Backend — Setup Guide

Step-by-step guide to set up and run the KafkaChat backend (Spring Boot + Kafka + PostgreSQL).

---

## 1. Prerequisites

Choose one of the two paths below.

### Option A: Run everything with Docker (recommended)

| Requirement | Purpose |
|-------------|---------|
| **Docker** | [Install Docker](https://docs.docker.com/get-docker/) |
| **Docker Compose** | Usually included with Docker Desktop; [standalone install](https://docs.docker.com/compose/install/) if needed |

No Java or Maven required on your machine — the backend is built and run inside a container.

### Option B: Run the backend locally (for development)

| Requirement | Purpose |
|-------------|---------|
| **Java 17** | [Adoptium](https://adoptium.net/) or [Oracle JDK 17](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html) |
| **Maven 3.6+** | [Install Maven](https://maven.apache.org/install.html) |
| **Docker** (optional) | To run only PostgreSQL + Kafka via Compose; otherwise you need them installed elsewhere |

Check versions:

```bash
java -version   # should show 17.x
mvn -version   # should show 3.6 or higher
```

---

## 2. Get the project

Clone or download the KafkaChat repo and open a terminal in the **project root** (the folder that contains `docker-compose.yml` and `kafkachat-backend/`).

```bash
cd path/to/KafkaChat
```

---

## 3. Setup — Option A: Docker (full stack)

This runs PostgreSQL, Zookeeper, Kafka, Kafka UI, and the backend in containers.

### Step 1: Start all services

From the project root:

```bash
docker-compose up -d
```

First run will build the backend image (may take a few minutes). Later runs start quickly.

**Windows:** You can double-click `start-server.bat` instead — it runs `docker-compose up -d` and shows your IP.

### Step 2: Wait for services to be ready

- PostgreSQL and Kafka have healthchecks; the backend starts after they are healthy.
- Give the backend ~30–60 seconds on first start (DB migrations, Kafka connection).

Check that containers are running:

```bash
docker-compose ps
```

All services should show “Up” or “running”.

### Step 3: Verify the backend

- **Health:** Open in a browser or use curl:
  - `http://localhost:8080/actuator/health`
  - Expect JSON with `"status":"UP"`.
- **Kafka UI:** `http://localhost:8081` — you can inspect topics and consumer groups.

If the health endpoint returns UP, the backend is ready for the Android app.

---

## 4. Setup — Option B: Backend locally (infrastructure in Docker)

Use this if you want to run the Spring Boot app on your machine (e.g. for debugging or hot-reload) while keeping PostgreSQL and Kafka in Docker.

### Step 1: Start only infrastructure

From the project root:

```bash
docker-compose up -d postgres zookeeper kafka
```

Do **not** start the `app` service — you will run it with Maven.

### Step 2: Point the backend to localhost

When the backend runs on your host, it must reach PostgreSQL and Kafka on `localhost` (ports 5432 and 9092). The default `application.yml` uses hostnames `postgres` and `kafka`, which work only inside Docker.

**Option 2a — Environment variables (recommended)**

From the project root:

**Windows (PowerShell):**

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/chat_db"
$env:SPRING_DATASOURCE_USERNAME="rocky"
$env:SPRING_DATASOURCE_PASSWORD="rocky123"
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
cd kafkachat-backend
mvn spring-boot:run
```

**Windows (CMD):**

```cmd
set SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chat_db
set SPRING_DATASOURCE_USERNAME=rocky
set SPRING_DATASOURCE_PASSWORD=rocky123
set KAFKA_BOOTSTRAP_SERVERS=localhost:9092
cd kafkachat-backend
mvn spring-boot:run
```

**Linux / macOS:**

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/chat_db
export SPRING_DATASOURCE_USERNAME=rocky
export SPRING_DATASOURCE_PASSWORD=rocky123
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
cd kafkachat-backend
mvn spring-boot:run
```

**Option 2b — Local profile in `application.yml`**

Create or edit `kafkachat-backend/src/main/resources/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chat_db
    username: rocky
    password: rocky123
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      bootstrap-servers: localhost:9092
```

Run with the `local` profile:

```bash
cd kafkachat-backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Step 3: Verify

- Health: `http://localhost:8080/actuator/health` → `"status":"UP"`.
- Kafka UI (if you started it): `docker-compose up -d kafka-ui` then open `http://localhost:8081`.

---

## 5. Configuration reference

### Backend (`application.yml`)

| Setting | Default (Docker) | Description |
|--------|-------------------|-------------|
| `spring.datasource.url` | `jdbc:postgresql://postgres:5432/chat_db` | PostgreSQL JDBC URL. Use `localhost:5432` when running backend on host. |
| `spring.datasource.username` | `rocky` | DB user (must match `POSTGRES_USER` in Compose). |
| `spring.datasource.password` | `rocky123` | DB password (must match `POSTGRES_PASSWORD`). |
| `spring.kafka.bootstrap-servers` | `kafka:29092` | Kafka brokers. Use `localhost:9092` when running backend on host. |
| `server.port` | `8080` | HTTP port. |
| `jwt.secret` | (see file) | **Change in production.** |
| `jwt.expiration` | `86400000` (24h) | JWT validity in milliseconds. |

Environment variables override `application.yml` (e.g. in `docker-compose.yml` or your shell).

### Docker Compose (backend container)

The `app` service in `docker-compose.yml` sets:

- `KAFKA_BOOTSTRAP_SERVERS=kafka:29092`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/chat_db`
- `SPRING_DATASOURCE_USERNAME=rocky`
- `SPRING_DATASOURCE_PASSWORD=rocky123`

To use different DB or Kafka, change these in `docker-compose.yml` or add a `.env` file.

---

## 6. Ports summary

| Port | Service | Used by |
|------|---------|---------|
| **8080** | Backend (REST + WebSocket) | Android app, browser, ngrok |
| **5432** | PostgreSQL | Backend only |
| **9092** | Kafka (host) | Backend when running on host; 29092 used between containers |
| **8081** | Kafka UI | Optional; for debugging Kafka |
| **2181** | Zookeeper | Kafka only |

Ensure **8080** is free if you run the backend (Docker or Maven). For the Android app from another device, allow 8080 through your firewall or use ngrok.

---

## 7. Troubleshooting

### Backend won’t start (Docker)

- **“Connection refused” to PostgreSQL or Kafka:** Wait longer; the backend starts after DB and Kafka are healthy. Run `docker-compose logs app` to see errors.
- **Port 8080 already in use:** Stop whatever is using 8080, or change `ports: "8080:8080"` in `docker-compose.yml` (e.g. to `8082:8080`) and use port 8082 for the app URL.

### Backend won’t start (Maven, local)

- **“Connection refused” to PostgreSQL:** Ensure infrastructure is up: `docker-compose ps` and that you use `localhost:5432` in DB URL and env vars.
- **“Connection refused” to Kafka:** Use `localhost:9092` for `KAFKA_BOOTSTRAP_SERVERS` when the backend runs on the host; ensure `docker-compose up -d postgres zookeeper kafka` has completed.
- **“Could not find or load main class”:** From project root run `cd kafkachat-backend` then `mvn spring-boot:run` (not from the root).

### Health returns DOWN

- Check `docker-compose logs app` or the Maven console for exceptions (DB, Kafka, or port binding).
- Confirm PostgreSQL is healthy: `docker-compose exec postgres psql -U rocky -d chat_db -c 'SELECT 1'`.
- Open Kafka UI at `http://localhost:8081` and confirm the cluster is available.

### Android app can’t reach backend

- **Same machine / emulator:** Use `http://10.0.2.2:8080` (Android emulator) or `http://localhost:8080`.
- **Phone on same Wi‑Fi:** Use your PC’s IP, e.g. `http://192.168.1.10:8080`, and allow port 8080 in Windows Firewall.
- **Phone on different network:** Use [ngrok](https://ngrok.com/) — see README “Hosting with ngrok”.

---

## 8. Quick reference

| Goal | Command (from project root) |
|------|-----------------------------|
| Start full stack (Docker) | `docker-compose up -d` |
| Start only infra (for local backend) | `docker-compose up -d postgres zookeeper kafka` |
| Run backend locally | Set env vars (see §4), then `cd kafkachat-backend && mvn spring-boot:run` |
| Backend logs (Docker) | `docker-compose logs -f app` |
| Stop all | `docker-compose down` |
| Rebuild backend image | `docker-compose build app && docker-compose up -d` |
| Health check | `curl http://localhost:8080/actuator/health` |

For Docker details, see the main [README.md](README.md#docker).
