FROM eclipse-temurin:17-jdk
EXPOSE 8080

COPY build/libs/*.jar ./
CMD java -jar ph-ee-connector-channel-1.0.0-SNAPSHOT.jar
