FROM java

RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
RUN mv lein /bin/lein
RUN chmod a+x /bin/lein
ENV LEIN_ROOT 1
RUN lein version

ADD ./ /remboard/
WORKDIR /remboard

RUN lein uberjar

EXPOSE 8080

CMD ["java", "-jar" "target/retroboard-standalone.jar"]