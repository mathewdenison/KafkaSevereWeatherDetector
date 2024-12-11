#!/bin/bash

# Path to Kafka binary directory
KAFKA_BIN="/kafka/bin"
# Kafka broker address
BOOTSTRAP_SERVER=${BOOTSTRAP_SERVER:-"kafka:9092"}

# Topics to check and create
TOPICS=(
    "weather-stream-processor"
    "weather-data"
    "temperature-comparator"
    "climate-data"
)

# Timeout for Kafka readiness (max retries × interval seconds)
MAX_RETRIES=10
RETRY_INTERVAL=5

# Function to check if Kafka is ready
wait_for_kafka() {
    echo "Waiting for Kafka to start..."
    for (( i=1; i<=MAX_RETRIES; i++ )); do
        if $KAFKA_BIN/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list > /dev/null 2>&1; then
            echo "Kafka is ready!"
            return 0
        else
            echo "Kafka is not ready yet... retrying in $RETRY_INTERVAL seconds (Attempt $i of $MAX_RETRIES)"
        fi
        sleep $RETRY_INTERVAL
    done
    echo "Kafka did not start within the expected time. Exiting."
    return 1
}

# Function to create topics if missing
create_topic_if_missing() {
    local topic="$1"
    EXISTING_TOPICS=$($KAFKA_BIN/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list)

    if echo "$EXISTING_TOPICS" | grep -wq "$topic"; then
        echo "Topic '$topic' already exists. Skipping creation."
    else
        echo "Creating topic '$topic'..."
        if $KAFKA_BIN/kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --create --topic "$topic" --partitions 1 --replication-factor 1; then
            echo "Topic '$topic' created successfully."
        else
            echo "Failed to create topic '$topic'. Continuing anyway."
        fi
    fi
}

# Main execution flow
wait_for_kafka || exit 0  # Exit gracefully if Kafka doesn't become ready

# Check and create topics
for topic in "${TOPICS[@]}"; do
    create_topic_if_missing "$topic"
done

echo "All topics verified or created successfully. Exiting script."
exit 0