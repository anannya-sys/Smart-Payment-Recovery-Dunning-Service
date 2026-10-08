# ---- Stage 1: build the jar with Maven (dependencies cached in their own layer) ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Stage 2: small runtime image with just the JRE and the jar ----
FROM eclipse-temurin:17-jre
WORKDIR /app
# Run as a non-root user.
RUN useradd --system --uid 1001 appuser
COPY --from=build /workspace/target/subscription-recovery-service-*.jar app.jar
USER appuser
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Kolkata"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
