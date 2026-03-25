FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre

RUN groupadd -g 1001 airgap && \
    useradd -m -u 1001 -g airgap -s /bin/bash airgap

WORKDIR /opt/airgap

COPY --from=build /build/target/*.jar app.jar

# Create runtime dirs (IMPORTANT)
RUN mkdir -p /opt/airgap/runtime && \
    mkdir -p /opt/airgap/downstream && \
    mkdir -p /opt/airgap/upstream && \
    chown -R airgap:airgap /opt/airgap

# Run as non-root
USER airgap

EXPOSE 8081

ENTRYPOINT ["java","-jar","/opt/airgap/app.jar"]