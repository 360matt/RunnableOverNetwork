# :sparkles: RunnableOverNetwork

Allows you to send class Runnable files via the socket, load them into the CloassLoader and have it executed on the server side.  
So you can debug an application [remotely] without restarting it.

⚠️ This project allow remote code execution, if you are not aware 
of the security risk of this library you should not use it.  
⚠️ This code enables Remote Code Execution (RCE).

**None of the Authors are responsible for any damage caused by the misuse of this library**

My Discord: ``Matteow#6953``

## Maven
```
<repositories>
    <repository>
        <id>bungeecord-repo</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.360matt</groupId>
    <artifactId>RunnableOverNetwork</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## :fire: Benefits ?
* Easy to use
* No need to relaunch your .jar
* Very light (27Kb only).
* very fast to the human eye.
* Can receive several clients at the same time.
* Each client has its own threads.
* Execution errors are caught and displayed.
* Support both data sending and retrieval

## :question: How it works ?
1. Your IDE has already compiled your classes before launching the client.
2. We retrieve the class file and send it to the network.
3. The server receives, loads it into its ClassLoader.
4. It checks the type of class and instantiates the Runnable.
  
# How to use ?
## Create server:
```java
final DynamicServer dynamicServer = new DynamicServer(5000, "password");
// Set to true to make function able to send/receive complex Objects
dynamicServer.setAllowUnsafeSerialisation(true);
new Thread(() -> {
    try {
        dynamicServer.listen(); // This method is blocking
    } catch (IOException e) {
        e.printStackTrace();
    }
}).start();
```
### Instantiate & Use:
```java
new Thread(() -> {
    try {
        DynamicServer server = new DynamicServer( PORT, PASSWORD );
        // with port
        
        DynamicServer server = new DynamicServer( new ServerSocket( PORT ), PASSWORD );
        // with a ServerSocket object
        server.listen();
        
        server.close();
        // close the server and disconnect clients.
    } catch (IOException e) {
        e.printStackTrace();
    }
}).start();
```

## Create client:
The client, most of the time will run locally, launched by your IDE (and your class-test)  
### Instantiate:
```java
final DynamicClient client = new DynamicClient( IP, PORT, USERNAME, PASSWORD);

final DynamicClient client = new DynamicClient( new Socket(), USERNAME, PASSWORD );
```
Note: The username can be empty.

### Use
```java
client.sendRunnable(OneClass.class).run();
// send a Class<? extends Runnable>
Runnable runnable = client.sendFile( new File("path_to_your_class"), "your.class.Name");

// Exec runnable
runnable.run();

client.close(); 
// close the client and its socket
```
