# ChatFlow System Overview

## Table of Contents
- [System Overview](#system-overview)
- [Client](#client)
- [Load Balancer](#load-balancer)
- [Server](#server)
- [Message Queue](#message-queue)
- [Consumer](#consumer)
- [Database](#database)
- [Improvements](#improvements)

---

## System Overview

ChatFlow is a distributed real-time chat system built for high-throughput message delivery. Clients connect via WebSocket to one of several server instances sitting behind a load balancer. Each server validates incoming messages and publishes them to RabbitMQ. A pool of consumer instances reads from RabbitMQ, deduplicates messages using Redis, broadcasts them back to connected clients via an internal HTTP endpoint, and persists them to PostgreSQL.

```
Client(s)
   │  WebSocket ws://
   ▼
Load Balancer (AWS ALB)
   │
   ├── Server Instance 1  ──┐
   ├── Server Instance 2  ──┤── chat.exchange (RabbitMQ topic exchange)
   └── Server Instance N  ──┘         │
                                       │  routing key: room.<id>
                              ┌────────┴────────────────────┐
                              │   room.1 ... room.20 queues  │
                              └────────┬────────────────────┘
                                       │
                              Consumer (ConsumerPool)
                                  │          │
                              Redis       DbWriterPool
                             (dedup)    (PostgreSQL RDS)
                                  │
                          POST /internal/broadcast
                                  │
                              Server(s) → WebSocket clients
```

**Infrastructure (AWS us-east-1)**

| Component | Instance |
|-----------|----------|
| Server | EC2 t3.micro |
| Consumer | EC2 t3.micro / t3.xlarge |
| RabbitMQ | EC2 t3.micro |
| Database | RDS PostgreSQL |
| Load Balancer | AWS ALB |

---

## Client

The load-test client (`server-2/client/`) simulates concurrent users sending chat messages over WebSocket.

### Key design choices

- **Connection pool** (`ConnectionPool`): maintains one persistent WebSocket connection per room. Reconnects automatically on failure and tracks reconnect count.
- **Two-phase test**: a warmup phase (25 threads × 200 messages) precedes the main phase (64 threads × ~500,000 messages total) to prime JIT and connection pools.
- **Retry workers** (`RetryWorker`): 4 dedicated threads drain a `retryQueue` for messages that failed on first attempt.
- **Message generator** (`MessageGenerator`): produces `MessageRound` objects — each round is 1 JOIN + N TEXT + 1 LEAVE — over 20 rooms.
- **Metrics** (`MetricsCollector`): captures per-message latency, message type distribution, and throughput. Writes results to CSV and prints p50/p95/p99 latencies.

### Message format

```json
{
  "userId": "user-42",
  "username": "alice",
  "message": "hello world",
  "timestamp": "2026-01-31T00:00:00Z",
  "messageType": "TEXT"
}
```

Message types: `JOIN`, `TEXT`, `LEAVE`.

---

## Load Balancer

An AWS Application Load Balancer (`chatflow-lb-1658318388.us-east-1.elb.amazonaws.com`) distributes WebSocket connections across server instances on port 8080. ALB sticky sessions are not required because all servers share the same RabbitMQ exchange — any server can accept any client.

---

## Server

The server (`server-2/server/`) runs two listeners on the same process:

| Listener | Port | Protocol | Purpose |
|----------|------|----------|---------|
| `ChatWebSocketServer` | 8080 | WebSocket | Accept client connections, publish to RabbitMQ |
| `HttpServerManager` | 8081 | HTTP (Jetty) | `/internal/broadcast`, `/health`, `/metrics` |

### WebSocket server (`ChatWebSocketServer`)

- Clients connect to `ws://host:8080/chat/<roomId>`. The room ID is extracted from the URI path.
- A `ConcurrentHashMap<WebSocket, String>` maps each open connection to its room.
- On message receive:
  1. Deserialize JSON → `ChatMessage`
  2. Validate via `MessageValidator`
  3. Wrap in a `MessageEnvelope` (adds `messageId` UUID, `serverId`, `clientIp`, `timestamp`)
  4. Publish to RabbitMQ exchange `chat.exchange` with routing key `room.<roomId>`, `deliveryMode=2` (persistent)
- A pooled `RabbitMQConnectionManager` provides pre-created channels to avoid per-message connection overhead.

### RabbitMQ connection manager

- Maintains one AMQP connection and a fixed-size `BlockingQueue<Channel>` pool.
- `borrowChannel()` / `returnChannel()` provide thread-safe channel access.
- Connects with exponential backoff (up to 5 attempts: 1 s → 2 s → 4 s → 8 s → 16 s).
- `reconnect()` is `synchronized` to prevent multiple threads from reconnecting simultaneously.

### Internal broadcast endpoint (`BroadcastServlet`)

`POST /internal/broadcast`

Called by the consumer to push a processed message back to connected clients. Iterates the `roomMapping` and calls `WebSocket.send()` for every open connection in the target room. Returns `{ "sent": N, "failed": M, "roomId": "..." }`.

---

## Message Queue

RabbitMQ runs on a dedicated EC2 instance and is configured via the setup script (`consumer-v3/scripts/setup-queues.sh`).

### Topology

| Element | Name | Type | Config |
|---------|------|------|--------|
| Exchange | `chat.exchange` | topic, durable | — |
| Queues | `room.1` … `room.20` | durable | TTL 600 s, max-length 100,000, overflow: reject-publish |
| Bindings | `room.<id>` → `room.<id>` queue | routing key match | — |

- **Topic exchange** allows routing by room without the server knowing queue names directly.
- **Durable queues** survive RabbitMQ restarts; messages are published with `deliveryMode=2`.
- **`x-overflow: reject-publish`** applies backpressure to the server when a queue is full rather than silently dropping messages.

---

## Consumer

The consumer (`consumer-v3/`) is a standalone Java process that reads from RabbitMQ, deduplicates, broadcasts, and persists.

### ConsumerPool

`ConsumerPool` distributes the 20 rooms across N consumer threads using round-robin assignment:

```
10 rooms / 4 threads → thread-1:[r1,r5,r9], thread-2:[r2,r6,r10], thread-3:[r3,r7], thread-4:[r4,r8]
```

Each thread owns its rooms for the lifetime of the pool, ensuring in-order delivery within a room.

### ConsumerThread

Each `ConsumerThread`:
1. Opens a dedicated AMQP connection and channel.
2. Sets `basicQos(1)` — one unacked message at a time per thread, ensuring fair dispatch and ordering.
3. Calls `basicConsume` on each assigned room queue with **manual ack**.
4. On each delivery:
   - Deserialize JSON → `ChatMessage`
   - Check `DeduplicationService.isDuplicate()` (Redis); skip and ack if duplicate
   - Call `RoomManager.process()` → broadcast + DB write
   - On `ACK`: mark seen in Redis, ack the message
   - On `NACK`: nack with requeue (transient error, retry)
   - On `DISCARD`: ack and drop (permanent error, e.g. malformed)
5. On connection loss, retries every 5 seconds.

### DeduplicationService

Uses Redis with key `seen:<messageId>` and a 24-hour TTL.

- `isDuplicate()` checks key existence before processing.
- `markSeen()` uses `SET NX EX` (atomic set-if-not-exists) — safe for concurrent consumer threads.
- On Redis failure, defaults to **allow through** (better to process a duplicate than to drop a message).

### WebSocketBroadcaster

After processing, the consumer POSTs to the server's `/internal/broadcast` endpoint:

```json
{ "roomId": "5", "message": "<serialized ChatMessage JSON>" }
```

Errors are classified as retryable (network timeout, HTTP 5xx) or non-retryable (serialization failure).

### DbWriterPool

A `DbWriterPool` of N threads drains a `LinkedBlockingQueue<ChatMessage>` and writes to PostgreSQL via `MessageRepository`. Latency percentiles (p50/p95/p99) are tracked via a `ConcurrentLinkedQueue<Long>` of samples.

Configuration (from `config.properties`):

| Parameter | Value |
|-----------|-------|
| Consumer threads | 10 |
| DB writer threads | 10 |
| DB writer queue capacity | 500,000 |
| HikariCP pool size | 20 |
| Redis dedup TTL | 86,400 s (24 h) |
| RabbitMQ reconnect delay | 5,000 ms |

---

## Database

PostgreSQL on AWS RDS. Schema defined in `database/setup.sql`.

### Tables

**`messages`** — primary message store

| Column | Type | Notes |
|--------|------|-------|
| `message_id` | VARCHAR(36) PK | UUID assigned by server |
| `room_id` | VARCHAR(255) NOT NULL | |
| `user_id` | VARCHAR(255) NOT NULL | |
| `username` | VARCHAR(255) NOT NULL | |
| `message` | TEXT | |
| `message_type` | VARCHAR(10) | `JOIN`, `TEXT`, or `LEAVE` |
| `timestamp` | TIMESTAMPTZ NOT NULL | |
| `server_id` | VARCHAR(255) | which server instance handled it |
| `client_ip` | VARCHAR(45) | |

**`user_room_activity`** — per-user per-room aggregate

| Column | Type | Notes |
|--------|------|-------|
| `user_id` | VARCHAR(255) PK | composite PK |
| `room_id` | VARCHAR(255) PK | composite PK |
| `last_activity` | TIMESTAMPTZ NOT NULL | |
| `message_count` | INT | default 0 |

### Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| `idx_messages_room_time` | `(room_id, timestamp)` | Room history queries |
| `idx_messages_user_time` | `(user_id, timestamp)` | User history queries |
| `idx_messages_timestamp` | `(timestamp)` | Time-range scans |
| `idx_messages_type` | `(message_type, timestamp)` | Filter by event type |

### Load test results

| Test | Messages | Threads | Throughput | P95 latency | P99 latency |
|------|----------|---------|------------|-------------|-------------|
| Baseline | 500,000 | 4 consumer / 4 DB writer | 70 msg/s | 12 ms | 78 ms |
| Stress | 1,000,000 | 10 consumer / 10 DB writer | 130 msg/s (peak) | 20 ms | 156 ms |
| Endurance (30 min) | sustained | 10 / 10 | ~130 msg/s stable | — | — |

Throughput under the stress test degraded from 130 msg/s at 0–100K messages down to 32 msg/s at 600K–1M, consistent with RDS I/O saturation on the t3.micro tier.

---

## Improvements

### Throughput & scalability

- **Batch DB writes**: group inserts using `INSERT ... VALUES (...), (...), (...)` (or JDBC `addBatch`) to reduce per-row overhead and saturate RDS IOPS more efficiently. Current implementation writes one row per DB writer thread iteration.
- **Upgrade RDS instance**: the stress-test degradation curve shows RDS t3.micro as the bottleneck. Moving to a `db.t3.medium` or `db.m6g.large` with Provisioned IOPS would sustain higher throughput at scale.
- **Scale consumer horizontally**: run multiple consumer processes across EC2 instances. RabbitMQ's per-queue round-robin dispatch handles load balancing automatically; the Redis dedup layer prevents double-processing.
- **Increase RabbitMQ queue TTL and depth**: the current 600 s TTL and 100K max-length cap can cause message loss during consumer restarts. Increase based on observed consumer lag.

### Reliability & correctness

- **Persistent Redis**: replace the in-memory Redis instance with a replicated Redis cluster or ElastiCache to ensure dedup state survives restarts.
- **Dead-letter queue**: configure a DLQ (`x-dead-letter-exchange`) on each room queue so permanently unprocessable messages are captured rather than requeued indefinitely.
- **Idempotent DB writes**: use `INSERT ... ON CONFLICT (message_id) DO NOTHING` to make DB writes safe to retry without duplicating rows.
- **Outbox pattern**: write to DB and broadcast atomically using a transactional outbox to eliminate the race between broadcast success and DB write failure.

### Observability

- **Structured logging**: replace `System.out.println` in the server with SLF4J/Logback, consistent with the consumer's logging approach.
- **Metrics endpoint**: the `/metrics` HTTP endpoint exists but is not yet populated with consumer-side stats (lag, dedup hit rate, broadcast latency). Wire `ConsumerMetrics` into a shared metrics registry exposed there.
- **Queue depth alerting**: add CloudWatch alarms on RabbitMQ queue depth to detect consumer lag before messages expire.

### Architecture

- **Consumer-to-client broadcast path**: the current design requires the consumer to POST back to the originating server, creating a tight coupling. A pub/sub layer (e.g. Redis Pub/Sub or a second RabbitMQ fanout exchange) would allow any server to receive the broadcast, making server instances fully stateless.
- **WebSocket sticky sessions on ALB**: if the broadcast-back approach is retained, enable ALB sticky sessions so the consumer can reliably route the broadcast to the correct server instance.
- **Message history API**: add a REST endpoint backed by the `messages` table to support chat history on reconnect.
