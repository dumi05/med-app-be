# primo stage di build, uso un immagine maven e jdk completo per compilare il progetto
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app
# il copy del pom + la run successiva servono principalmente per sfruttare la cache di docker,
# se non cambio le dipendenze pom, Docker riusa la cache del download delle dipendenze anche quando modifico solo codice sorgente, build molto più veloce
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# questo secondo stage usa una immagine minimale come la jre, più leggera
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/med-app-be-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]