# Runtime image. The jar is built beforehand by Maven (see release.yml), so this stays small and fast.
FROM eclipse-temurin:25-jre-noble AS extract
WORKDIR /build
COPY target/shorty-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-noble
LABEL org.opencontainers.image.source="https://github.com/pgatzka/shorty" \
      org.opencontainers.image.description="Sorting-algorithm Shorts renderer and uploader"
# ffmpeg encodes the videos; fontconfig + DejaVu give Java2D real fonts in a headless JRE.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg fontconfig fonts-dejavu-core \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --create-home shorty \
    && mkdir -p /data/videos \
    && chown shorty:shorty /data/videos
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER shorty
ENV SHORTY_VIDEO_DIR=/data/videos \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
VOLUME /data/videos
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
