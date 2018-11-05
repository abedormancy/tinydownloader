@if not exist bin mkdir bin
@javac -cp .;./lib/*; ./src/ga/uuid/tinydownload/*.java ./src/ga/uuid/tinydownload/util/*.java -encoding utf8 -d ./bin
@pause