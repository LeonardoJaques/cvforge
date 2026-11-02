# ===== Estágio de build =====
FROM maven:3.10.0-eclipse-temurin-27-alpine AS builder
WORKDIR /app
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-por tesseract-ocr-data-eng
COPY pom.xml .
COPY src/ ./src/
RUN mvn clean package -B

# ===== Estágio runtime =====
FROM eclipse-temurin:27-jre-alpine
WORKDIR /app
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-por tesseract-ocr-data-eng

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Profile docker via variável de ambiente
ENV SPRING_PROFILES_ACTIVE=docker

ENTRYPOINT ["java", \
    "-jar", \
    "/app/app.jar"]
