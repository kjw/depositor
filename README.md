# depositor

The CrossRef Linking Console.

## Development

1) Run ClojureScript compilation automatically on any file save:

    lein cljsbuild auto dev

2) Run the server in dev mode:

    lein repl

3) Connect to the server in Emacs, open any project file and then:

    M-x cider-connect

## Production Deployment

1) Install system dependencies (first time only):

  - Java 7
  - runit
  - Leiningen

2) Compile the Javascript for production:

    lein cljsbuild once production

3) Prepare the project for runit:

    lein with-profile +production runit
    cd target
	./commit.sh

4) Restart or start the server:

    sv restart depositor

## Configuration Environment Variables

- SERVER_PORT
- SERVER_THREADS
- SERVER_QUEUE_SIZE
- BROWSER_REPL
- API
- MAIN_JS
