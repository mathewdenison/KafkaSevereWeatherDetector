# Use an official OpenJDK image as the base for building
FROM openjdk:17-jdk-slim AS build

# Set the working directory inside the container for building
WORKDIR /app

# Copy the entire project into the container
COPY . .

# Build the application using Maven
RUN ./mvnw clean package -DskipTests

# Use an official OpenJDK image as the runtime
FROM openjdk:17-slim AS runtime

# Install necessary tools and download Spark
RUN apt-get update && apt-get install -y wget tar bash curl && \
    wget https://archive.apache.org/dist/spark/spark-3.5.3/spark-3.5.3-bin-hadoop3.tgz && \
    tar -xzf spark-3.5.3-bin-hadoop3.tgz && \
    mv spark-3.5.3-bin-hadoop3 /spark && \
    rm spark-3.5.3-bin-hadoop3.tgz && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Set Spark environment variables
ENV SPARK_HOME=/spark
ENV PATH=$SPARK_HOME/bin:$SPARK_HOME/sbin:$PATH

# Install Kafka for producing/consuming messages
RUN wget https://archive.apache.org/dist/kafka/2.8.0/kafka_2.13-2.8.0.tgz && \
    tar -xzf kafka_2.13-2.8.0.tgz && \
    mv kafka_2.13-2.8.0 /kafka && \
    rm kafka_2.13-2.8.0.tgz

# Set working directory inside the container for running
WORKDIR /app

# Copy the built JAR file from the builder step
COPY --from=build /app/target/backend-1.0.0.jar /app/backend-1.0.0.jar

# Copy the topic creation script into the image
COPY create-topics.sh /app/create-topics.sh

# Add execution permissions for the script
RUN chmod +x /app/create-topics.sh

# Expose necessary ports
EXPOSE 8080 7077 6066 4040 9000

# Start Kafka, then Spark, then run the application
ENTRYPOINT ["/bin/bash", "-c", "/app/create-topics.sh && exec java -jar backend-1.0.0.jar"]