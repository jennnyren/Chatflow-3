#!/bin/bash

RABBITMQ_USER="admin"
RABBITMQ_PASS="yourpassword"
VHOST="/"
EXCHANGE="chat.exchange"
BASE_URL="http://localhost:15672/api"
AUTH="-u $RABBITMQ_USER:$RABBITMQ_PASS"

echo "Creating topic exchange: $EXCHANGE"
curl -s -o /dev/null $AUTH -X PUT "$BASE_URL/exchanges/%2F/$EXCHANGE" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "topic",
    "durable": true,
    "auto_delete": false
  }'

# Create queues room.1 through room.20
for i in $(seq 1 20); do
  QUEUE="room.$i"
  ROUTING_KEY="room.$i"

  echo "Creating queue: $QUEUE"
  curl -s -o /dev/null $AUTH -X PUT "$BASE_URL/queues/%2F/$QUEUE" \
    -H "Content-Type: application/json" \
    -d '{
      "durable": true,
      "arguments": {
        "x-message-ttl": 600000,
        "x-max-length": 100000,
        "x-overflow": "reject-publish"
      }
    }'

  echo "Binding $QUEUE to $EXCHANGE with routing key $ROUTING_KEY"
  curl -s -o /dev/null $AUTH -X POST "$BASE_URL/bindings/%2F/e/$EXCHANGE/q/$QUEUE" \
    -H "Content-Type: application/json" \
    -d "{
      \"routing_key\": \"$ROUTING_KEY\"
    }"
done

echo "Done! All queues and bindings created."