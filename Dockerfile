FROM eclipse-temurin:17-jre

# App user
RUN useradd -ms /bin/bash airgap

# Use a local-friendly working directory
WORKDIR /app

# Copy jar
COPY target/airgap-service-1.0.1.jar app.jar

# Create data directories and set permissions
RUN mkdir -p /app/data && \
    mkdir -p /app/tmp && \
    chown -R airgap:airgap /app && \
    chmod -R 755 /app

USER airgap

EXPOSE 8081

ENTRYPOINT ["java","-jar","app.jar"]
