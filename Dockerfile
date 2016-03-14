FROM clojure

COPY . /app
WORKDIR /app

RUN lein cljsbuild once production