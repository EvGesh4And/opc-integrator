FROM maven:3.9.9-eclipse-temurin-21-alpine as builder
# ARG JAVA_PROXY
ARG JAVA_USER
ARG JAVA_PASSWD
COPY ./src ./src
COPY pom.xml pom.xml
#COPY settings-docker.xml /usr/share/maven/ref/
#RUN mvn -B -f /tmp/pom.xml -s /usr/share/maven/ref/settings-docker.xml dependency:resolve -Dmule.username=${JAVA_USER} -Dmule.password=${JAVA_PASSWD}
RUN mvn clean package -Dmule.username=${JAVA_USER} -Dmule.password=${JAVA_PASSWD}

FROM eclipse-temurin:21-jre-alpine
 ARG JAR_FILE

RUN addgroup -S spring && adduser -S spring -G spring 
USER spring:spring 

COPY --from=builder target/*.jar /opt/opc-integrator.jar 

WORKDIR /opt 

ENTRYPOINT ["java", "-jar", "opc-integrator.jar"] 
EXPOSE 45080 
