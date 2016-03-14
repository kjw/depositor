# depositor

The CrossRef Linking Console.

## Development

1) Run ClojureScript compilation automatically on any file save:

    lein cljsbuild auto dev

2) Run the server in dev mode:

    lein repl

3) Connect to the server in Emacs, open any project file and then:

    M-x cider-connect

## Test Production via Docker / Convox

    convox start
	
Then open up a browser to http://`your docker machine IP` .

## Production via Docker / Convox

    convox apps create depositor
	convox deploy

## Configuration Environment Variables

- SERVER_PORT
- SERVER_THREADS
- SERVER_QUEUE_SIZE
- SERVER_PATH
- TEST_DEPOSITS
- BROWSER_REPL
- API
- AUTH
- MAIN_JS
