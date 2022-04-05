# CS4262 - Distributed Systems
## Chat Server

### Build the executable file
- Required Java version - Java 17
- Required Maven version - Apache Maven 3.8.4

Execute the following commands in the terminal<br/>
`mvn clean install`

### Execute the executable file
- Go into the `target` folder<br/>
- Execute the following commands in the terminal<br/>
`java -jar chat-server-1.0-SNAPSHOT-jar-with-dependencies.jar [server name] "[path to the text file containing the configuration of servers]"`<br/><br/>
eg: `java -jar chat-server-1.0-SNAPSHOT-jar-with-dependencies.jar s2 "/home/dilanka_rathnasiri/Documents/chat-server/server_conf.txt"`