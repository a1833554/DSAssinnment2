Compile:
javac *java

Run: 
Start the server:
java AggregationServer

Send weather data (replace weatherDataFile with location of weather data file):
java ContentServer localhost 4567 <weatherDataFile>

Get the Weather data from server:
java GETClient localhost 4567 <OptionalContentServerID>

Automated testing was not implemented as could not get JUnit operationable. 

Data is only stored by ContentServer for 30 seconds.



