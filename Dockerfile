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

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh && \
    mkdir -p /opt/airgap/data

EXPOSE 8081

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]