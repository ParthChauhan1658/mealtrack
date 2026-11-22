# syntax=docker/dockerfile:1
# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && mv target/mealtrack-*.jar app.jar

# ---- run ----
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 mealtrack
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER mealtrack
EXPOSE 8080
# Sized for small free-tier containers (512 MB).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"
ENTRYPOINT ["java", "-jar", "app.jar"]
