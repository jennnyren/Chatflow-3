# Progressive Load Test Results

## Test Environment

| Component | Details            |
|---|--------------------|
| Server | AWS EC2 t3.micro   |
| Consumer | AWS EC2 t3.micro   |
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
| Consumer Threads | 4                    |
| DB Writer Threads | 4                    |
| HikariCP Pool Size | 10                   |
| RabbitMQ TTL | 3,600,000ms (1 hour) |
| RabbitMQ Max Queue | 100,000              |

### Results

| Metric | Value      |
|---|------------|
| Total Messages Sent | 500,000    |
| Successfully Written to DB | 500,000    |
| Write Throughput | 70 msg/sec |
| Total Duration | 96 min     |
| P95 Write Latency | 12 ms      |
| P99 Write Latency | 78 ms      |

---

## Test 2: Stress Test (1,000,000 Messages)

### Configuration

| Parameter | Value                |
|---|----------------------|
| Total Messages | 1,000,000            |
| Consumer Threads | 10                   |
| DB Writer Threads | 10                   |
| HikariCP Pool Size | 20                   |
| RabbitMQ TTL | 3,600,000ms (1 hour) |
| RabbitMQ Max Queue | 100,000              |
| EC2 Instance (Consumer) | t3.xlarge            |

### Results

| Metric | Value       |
|---|-------------|
| Total Messages Sent | 1,000,000   |
| Successfully Written to DB | 1,000,000   |
| Write Throughput | 130 msg/sec |
| Total Duration | 90 min      |
| P95 Write Latency | 20 ms       |
| P99 Write Latency | 156 ms      |

### Degradation Curve
Messages processed: Throughput

0- 100,000: 130

100,000 - 300,000: 95

300,000 - 600,000: 60

600,000 - 1,000,000: 32

---

## Test 3: Endurance Test (30 Minutes Sustained)

### Configuration

| Parameter | Value                             |
|---|-----------------------------------|
| Duration | 30 minutes                        |
| Target Rate | 80% of max throughput from Test 2 |
| Consumer Threads | 10                                |
| DB Writer Threads | 10                                |
| HikariCP Pool Size | 20                                |
| EC2 Instance (Consumer) | t3.xlarge                         |

### Target Rate Calculation

```
Max throughput = 130 msg/sec
Target rate (80%) = ~100 msg/sec
```

### Results Over Time

Time processed: Throughput

0- 5 (min): 130

5- 10 (min): 125

10- 15 (min): 126

15- 20 (min): 130

20- 25 (min): 131

25- 30 (min): 130