    # Use an official OpenJDK runtime as a parent image
    # Make sure the Java version matches your project (17 in your pom.xml)
    FROM eclipse-temurin:17-jdk-jammy

    # Set the working directory inside the container
    WORKDIR /app

    # Copy the Maven wrapper files (optional but good practice if you use mvnw)
    # COPY .mvn/ .mvn
    # COPY mvnw pom.xml ./

    # If not using wrapper, just copy pom.xml
    COPY pom.xml .

    # Download dependencies (this layer is cached if pom.xml doesn't change)
    # RUN ./mvnw dependency:go-offline 
    # If not using wrapper:
    # RUN mvn dependency:go-offline

    # Copy the rest of your application source code
    COPY src ./src

    # Package the application using Maven inside the container
    # RUN ./mvnw package -DskipTests
    # If not using wrapper:
    RUN mvn package -DskipTests

    # Argument to specify the JAR file path (useful if the version changes)
    ARG JAR_FILE=target/chatbot-0.0.1-SNAPSHOT.jar

    # Copy the built JAR file to the working directory
    COPY ${JAR_FILE} application.jar

    # Expose the port the application runs on (8080 for Spring Boot default)
    EXPOSE 8080

    # Command to run the application
    ENTRYPOINT ["java","-jar","/app/application.jar"]