FROM docker.1ms.run/maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY log-analysis-app/pom.xml log-analysis-app/pom.xml
RUN mvn -B -pl log-analysis-app -am dependency:go-offline

COPY log-analysis-app/src log-analysis-app/src
RUN mvn -B -pl log-analysis-app -am package -DskipTests

FROM docker.1ms.run/eclipse-temurin:21-jre

WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /workspace/log-analysis-app/target/log-analysis-app-*.jar /app/log-analysis-app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/log-analysis-app.jar"]
