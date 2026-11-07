# The fake Google for the local stack: WireMock + one extension that signs ID tokens. Dev only.
FROM maven:3.9-eclipse-temurin-21 AS build
COPY extension /src
RUN mvn -q -B -f /src/pom.xml package

FROM wiremock/wiremock:3.13.1
COPY --from=build /src/target/fake-google-extension.jar /var/wiremock/extensions/
