## Monitoring — `/monitoring` Metrics Collection Scripts
(for reference)

### 1. RDS Metrics (CloudWatch)

```bash
#!/bin/bash
# monitor_rds.sh
# Collects RDS metrics from CloudWatch every 60 seconds

DB_IDENTIFIER="chatflow-db"
REGION="us-east-1"
START_TIME=$(date -u -v-1H '+%Y-%m-%dT%H:%M:%SZ')  # last 1 hour
END_TIME=$(date -u '+%Y-%m-%dT%H:%M:%SZ')

echo "=== RDS Metrics: $DB_IDENTIFIER ==="
echo "Period: $START_TIME to $END_TIME"
echo ""

echo "--- CPU Utilization ---"
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/RDS \
  --metric-name CPUUtilization \
  --dimensions Name=DBInstanceIdentifier,Value=$DB_IDENTIFIER \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 60 \
  --statistics Average Maximum \
  --output table

echo "--- Database Connections ---"
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/RDS \
  --metric-name DatabaseConnections \
  --dimensions Name=DBInstanceIdentifier,Value=$DB_IDENTIFIER \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 60 \
  --statistics Average Maximum \
  --output table

echo "--- Write IOPS ---"
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/RDS \
  --metric-name WriteIOPS \
  --dimensions Name=DBInstanceIdentifier,Value=$DB_IDENTIFIER \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 60 \
  --statistics Average Maximum \
  --output table

echo "--- Read IOPS ---"
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/RDS \
  --metric-name ReadIOPS \
  --dimensions Name=DBInstanceIdentifier,Value=$DB_IDENTIFIER \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 60 \
  --statistics Average Maximum \
  --output table

echo "--- Free Storage Space ---"
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/RDS \
  --metric-name FreeStorageSpace \
  --dimensions Name=DBInstanceIdentifier,Value=$DB_IDENTIFIER \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 60 \
  --statistics Average Minimum \
  --output table

echo "--- Write Latency ---"
aws cloudwatch get-metric-statistics \
  --region $REGION \
  --namespace AWS/RDS \
  --metric-name WriteLatency \
  --dimensions Name=DBInstanceIdentifier,Value=$DB_IDENTIFIER \
  --start-time $START_TIME \
  --end-time $END_TIME \
  --period 60 \
  --statistics Average Maximum \
  --output table
```

### 2. PostgreSQL Query Metrics

Run directly on RDS via psql (SSH tunnel or EC2):

```bash
#!/bin/bash
# monitor_postgres.sh
# Collects live PostgreSQL metrics

PGHOST="chatflow-db.crtgzfzmeycw.us-east-1.rds.amazonaws.com"
PGUSER="postgres"
PGDATABASE="postgres"

echo "=== PostgreSQL Live Metrics ==="
echo ""

psql -h $PGHOST -U $PGUSER -d $PGDATABASE << EOF

-- Active connections
\echo '--- Active Connections ---'
SELECT count(*), state
FROM pg_stat_activity
GROUP BY state;

-- Queries per second (approximate)
\echo '--- Table Stats (messages) ---'
SELECT
  relname,
  n_tup_ins AS inserts,
  n_tup_upd AS updates,
  n_tup_del AS deletes,
  n_live_tup AS live_rows,
  n_dead_tup AS dead_rows
FROM pg_stat_user_tables
WHERE relname IN ('messages', 'user_room_activity');

-- Index usage
\echo '--- Index Usage ---'
SELECT
  indexrelname,
  idx_scan AS index_scans,
  idx_tup_read AS tuples_read,
  idx_tup_fetch AS tuples_fetched
FROM pg_stat_user_indexes
WHERE relname = 'messages'
ORDER BY idx_scan DESC;

-- Lock waits
\echo '--- Lock Waits ---'
SELECT count(*) AS waiting_queries
FROM pg_stat_activity
WHERE wait_event_type = 'Lock';

-- Buffer hit ratio
\echo '--- Buffer Hit Ratio ---'
SELECT
  sum(heap_blks_hit) / (sum(heap_blks_hit) + sum(heap_blks_read)) * 100 AS buffer_hit_ratio
FROM pg_statio_user_tables;

-- Current message count
\echo '--- Message Count ---'
SELECT COUNT(*) AS total_messages FROM messages;

-- Messages per room
\echo '--- Messages Per Room ---'
SELECT room_id, COUNT(*) as count
FROM messages
GROUP BY room_id
ORDER BY room_id;

EOF
```

### 3. EC2 Instance Metrics

Run on consumer or server EC2 during the test:

```bash
#!/bin/bash
# monitor_ec2.sh
# Collects EC2 resource usage every 10 seconds

INTERVAL=10
DURATION=1800  # 30 minutes
LOGFILE="ec2_metrics_$(date +%Y%m%d_%H%M%S).csv"

echo "timestamp,cpu_percent,mem_used_mb,mem_free_mb,disk_used_gb,disk_free_gb" > $LOGFILE

echo "Collecting EC2 metrics every ${INTERVAL}s for ${DURATION}s..."
echo "Output: $LOGFILE"

END=$((SECONDS + DURATION))
while [ $SECONDS -lt $END ]; do
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    CPU=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | cut -d'%' -f1)
    MEM_USED=$(free -m | awk 'NR==2{print $3}')
    MEM_FREE=$(free -m | awk 'NR==2{print $4}')
    DISK_USED=$(df -BG / | awk 'NR==2{print $3}' | tr -d 'G')
    DISK_FREE=$(df -BG / | awk 'NR==2{print $4}' | tr -d 'G')

    echo "$TIMESTAMP,$CPU,$MEM_USED,$MEM_FREE,$DISK_USED,$DISK_FREE" >> $LOGFILE
    echo "[$TIMESTAMP] CPU: ${CPU}% | Mem Used: ${MEM_USED}MB | Disk Free: ${DISK_FREE}GB"

    sleep $INTERVAL
done

echo "Done! Metrics saved to $LOGFILE"
```

### 4. RabbitMQ Queue Depth Monitor

Run on RabbitMQ EC2 during the test:

```bash
#!/bin/bash
# monitor_rabbitmq.sh
# Monitors queue depths every 5 seconds

RABBITMQ_USER="admin"
RABBITMQ_PASS="rabbitmq"
INTERVAL=5
LOGFILE="rabbitmq_metrics_$(date +%Y%m%d_%H%M%S).csv"

echo "timestamp,room,messages_ready,messages_unacked,consumers" > $LOGFILE

echo "Monitoring RabbitMQ queues every ${INTERVAL}s..."
echo "Output: $LOGFILE"

while true; do
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    for i in $(seq 1 20); do
        QUEUE="room.$i"
        RESULT=$(curl -s -u $RABBITMQ_USER:$RABBITMQ_PASS \
            "http://localhost:15672/api/queues/%2F/$QUEUE" | \
            python3 -c "
import sys, json
d = json.load(sys.stdin)
print(d.get('messages_ready',0), d.get('messages_unacknowledged',0), d.get('consumers',0))
")
        READY=$(echo $RESULT | awk '{print $1}')
        UNACKED=$(echo $RESULT | awk '{print $2}')
        CONSUMERS=$(echo $RESULT | awk '{print $3}')
        echo "$TIMESTAMP,$QUEUE,$READY,$UNACKED,$CONSUMERS" >> $LOGFILE
    done
    echo "[$TIMESTAMP] Queues sampled"
    sleep $INTERVAL
done
```

### 5. Message Throughput Monitor

Run on consumer EC2 — polls DB count every 10 seconds to calculate throughput:

```bash
#!/bin/bash
# monitor_throughput.sh
# Calculates actual DB write throughput

PGHOST="chatflow-db.crtgzfzmeycw.us-east-1.rds.amazonaws.com"
PGUSER="postgres"
PGDATABASE="postgres"
INTERVAL=10
LOGFILE="throughput_$(date +%Y%m%d_%H%M%S).csv"

echo "timestamp,total_messages,messages_since_last,throughput_per_sec" > $LOGFILE

LAST_COUNT=0

echo "Monitoring DB write throughput every ${INTERVAL}s..."

while true; do
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    CURRENT_COUNT=$(psql -h $PGHOST -U $PGUSER -d $PGDATABASE -t -c "SELECT COUNT(*) FROM messages;" | tr -d ' ')
    DELTA=$((CURRENT_COUNT - LAST_COUNT))
    THROUGHPUT=$(echo "scale=2; $DELTA / $INTERVAL" | bc)

    echo "$TIMESTAMP,$CURRENT_COUNT,$DELTA,$THROUGHPUT" >> $LOGFILE
    echo "[$TIMESTAMP] Total: $CURRENT_COUNT | +$DELTA in ${INTERVAL}s | ${THROUGHPUT} msg/sec"

    LAST_COUNT=$CURRENT_COUNT
    sleep $INTERVAL
done
```

### How to Run During Tests

```bash
# Terminal 1 - RDS CloudWatch metrics (local Mac)
chmod +x monitor_rds.sh
./monitor_rds.sh

# Terminal 2 - PostgreSQL live metrics (via SSH tunnel)
chmod +x monitor_postgres.sh
./monitor_postgres.sh

# Terminal 3 - EC2 resource usage (on consumer EC2)
chmod +x monitor_ec2.sh
./monitor_ec2.sh

# Terminal 4 - RabbitMQ queue depth (on RabbitMQ EC2)
chmod +x monitor_rabbitmq.sh
./monitor_rabbitmq.sh

# Terminal 5 - DB write throughput (on consumer EC2)
chmod +x monitor_throughput.sh
./monitor_throughput.sh
```


