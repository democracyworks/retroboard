FROM clojure:lein-2.5.0

RUN mkdir -p /usr/src/retroboard
WORKDIR /usr/src/retroboard

COPY project.clj /usr/src/retroboard/
RUN lein deps

COPY . /usr/src/retroboard

RUN lein uberjar

EXPOSE 80

CMD ["java", "-jar", "target/retroboard-standalone.jar", "80"]
