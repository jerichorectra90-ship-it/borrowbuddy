FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# -B batch mode, -ntp hides the download progress spam but still shows real errors.
# maven-settings.xml points Maven to Google's mirror of Maven Central, bc Maven Central
# rate-limits shared cloud build servers like Render's (that's what broke the build).
# retryHandler makes maven retry a failed download instead of failing the build
ENV MVN_FLAGS="-B -ntp -s maven-settings.xml -Daether.connector.http.retryHandler.count=5"
COPY pom.xml maven-settings.xml ./
# pre-download libraries so they get cached. if it hiccups here, the package step below retries
RUN mvn $MVN_FLAGS dependency:go-offline || true
COPY src ./src
# up to 3 tries, 15s apart, in case a download times out
RUN for i in 1 2 3; do mvn $MVN_FLAGS clean package -DskipTests && exit 0; echo "Build try $i failed, retrying in 15s..."; sleep 15; done; exit 1

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/borrowbuddy-1.0.0.jar app.jar
EXPOSE 8080
# keeps spring boot under Render's free 512 MB ram limit. --web = always run the website here
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseSerialGC", "-Xss512k", "-jar", "app.jar", "--web"]
