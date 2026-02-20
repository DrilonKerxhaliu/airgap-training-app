FROM eclipse-temurin:17-jre

# App user
RUN useradd -ms /bin/bash airgap

WORKDIR /opt/airgap

# Copy jar
COPY target/airgap-service-1.0.1.jar app.jar

# Create data directories
RUN mkdir -p /opt/airgap/data && \
    chown -R airgap:airgap /opt/airgap

USER airgap

EXPOSE 8081

ENTRYPOINT ["java","-jar","app.jar"]
