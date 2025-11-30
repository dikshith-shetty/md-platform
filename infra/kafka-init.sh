#!/bin/bash
# Wait for Kafka to be available
echo "Waiting for Kafka to start..."
sleep 15

echo "Creating topics..."
/opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
    --topic md-bidask.normalized \
    --partitions 5 \
    --bootstrap-server kafka:9092
echo "Topic creation complete."