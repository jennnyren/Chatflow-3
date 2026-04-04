# Progressive Load Test Results

## Test Environment

| Component | Details            |
|---|--------------------|
| Server | AWS EC2 t3.micro   |
| Consumer | AWS EC2 t3.large   |
| RabbitMQ | AWS EC2 t3.micro    |
| Database | AWS RDS PostgreSQL |
| Load Test Client | Local Mac          |
| AWS Region | us-east-1          |

---

## Test 1: Baseline (500,000 Messages)

### Configuration

| Parameter | Value                |
|---|----------------------|
| Total Messages | 500,000              |
| Consumer Threads | 20                    |
| DB Writer Threads | 10                    |
| HikariCP Pool Size | 40                   |
| RabbitMQ TTL | 3,600,000ms (1 hour) |
| RabbitMQ Max Queue | 100,000              |

### Results

| Metric | Value      |
|---|------------|
| Total Messages Sent | 500,000    |
| Successfully Written to DB | 500,000    |
| Write Throughput | 2040 msg/sec |
| Total Duration |  245s     |
| P95 Write Latency | 12 ms      |
| P99 Write Latency | 78 ms      |

---

## Test 2: Stress Test (1,000,000 Messages)

### Configuration

| Parameter | Value                |
|---|----------------------|
| Total Messages | 1,000,000            |
| Consumer Threads | 20                   |
| DB Writer Threads | 10                   |
| HikariCP Pool Size | 40                   |
| RabbitMQ TTL | 3,600,000ms (1 hour) |
| RabbitMQ Max Queue | 100,000              |
| EC2 Instance (Consumer) | t3.large            |

### Results

| Metric | Value       |
|---|-------------|
| Total Messages Sent | 1,000,000   |
| Successfully Written to DB | 1,000,000   |
| Write Throughput | 1394 msg/sec |
| Total Duration | 715s      |
| P95 Write Latency | 20 ms       |
| P99 Write Latency | 156 ms      |

### Degradation Curve
Messages processed: Throughput

0- 100,000: ~2000

100,000 - 300,000: ~1800

300,000 - 600,000: ~1200

600,000 - 1,000,000: ~1000

---

## Test 3: Endurance Test (30 Minutes Sustained)

### Configuration

| Parameter | Value                             |
|---|-----------------------------------|
| Duration | 30 minutes                        |
| Target Rate | 80% of max throughput from Test 2 |
| Consumer Threads | 20                                |
| DB Writer Threads | 10                                |
| HikariCP Pool Size | 40                                |
| EC2 Instance (Consumer) | t3.large                         |

### Target Rate Calculation

```
Max throughput = 1300 msg/sec
Target rate (80%) = ~1040 msg/sec
```

### Results Over Time

Time processed: Throughput

0- 5 (min): ~2000

5- 10 (min): ~1800

10- 15 (min): ~1300

15- 20 (min): ~800

20- 25 (min): ~600

25- 30 (min): ~600