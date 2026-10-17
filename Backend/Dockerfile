FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-noble
WORKDIR /app
RUN groupadd --system --gid 10001 simpissuehub \
    && useradd --system --uid 10001 --gid simpissuehub --home-dir /app --shell /usr/sbin/nologin simpissuehub
COPY --from=build /workspace/target/simp-issue-hub-backend-*.jar /app/app.jar
RUN mkdir -p /app/data && chown -R simpissuehub:simpissuehub /app
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
