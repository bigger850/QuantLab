FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline -q || true
COPY src ./src
RUN mvn -B clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseG1GC"
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=8080"]
