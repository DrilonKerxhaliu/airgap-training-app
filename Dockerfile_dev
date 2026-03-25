FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre

RUN useradd -ms /bin/bash airgap
WORKDIR /opt/airgap

COPY --from=build /build/target/*.jar app.jar

RUN mkdir -p /opt/airgap/data && \
    chown -R airgap:airgap /opt/airgap

USER airgap
EXPOSE 8081

ENTRYPOINT ["java","-jar","app.jar"]