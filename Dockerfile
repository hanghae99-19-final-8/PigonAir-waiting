FROM openjdk:17
WORKDIR /app
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 9010
CMD ["java","-jar","app.jar"]