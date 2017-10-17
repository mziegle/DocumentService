FROM mykro/java8-jre:latest

WORKDIR run

COPY target/DocumentService-1.0-SNAPSHOT-jar-with-dependencies.jar .

COPY bill.pdf .

CMD java -jar DocumentService-1.0-SNAPSHOT-jar-with-dependencies.jar