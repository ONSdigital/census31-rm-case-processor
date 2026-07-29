FROM mirror.gcr.io/library/eclipse-temurin:21-jre-alpine

ARG JAR_FILE=census-rm-caseprocessor*.jar
CMD ["/opt/java/openjdk/bin/java", "-jar", "/opt/census-rm-caseprocessor.jar"]
COPY healthcheck.sh /opt/healthcheck.sh

# Create a system group and user without forcing UID/GID
RUN addgroup --system caseprocessor && \
    adduser --system --ingroup caseprocessor caseprocessor

USER caseprocessor

COPY target/$JAR_FILE /opt/census-rm-caseprocessor.jar


