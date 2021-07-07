# :sparkles: RunnableOverNetwork

Allows you to send class Runnable files via the socket, load them into the CloassLoader and have it executed on the server side.  
So you can debug an application [remotely] without restarting it.

/!\\ This project allow remote code execution, if you are not aware 
of the security risk of this library you should not use it /!\\

**None of the Authors are responsible for any damage caused by the misuse of this library**

My Discord: ``Matteow#6953``

## :fire: Benefits ?
* No need to relaunch your .jar
* Very light (12Kb only).
* very fast to the human eye.
* Can receive several client at the same time.
* Each client has its own threads.
* Execution errors are caught and displayed.

## :question: How it works ?
1. Your IDE has already compiled your classes before launching the client.
2. We retrieve the class file and send it to the network.
3. The server receives, loads it into its ClassLoader.
4. It checks the type of class and instantiates the Runnable.
  
# How to use ?
## Create server:
You must instantiate your server in a thread so as not to block the rest of your program.   
### Instantiate & Use:
```java
new Thread(() -> {
    try {
        DynamicServer server = new DynamicServer( PORT );
        // with port
        
        DynamicServer server = new DynamicServer( new ServerSocket( PORT ) );
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
final DynamicClient client = new DynamicClient(ip, port);

final DynamicClient client = new DynamicClient( new Socket() );
```

### Use
```java
client.sendRunnable(OneClass.class);
// send a Class<? extends Runnable>
Runnable runnable = client.sendFile( new File("path_to_your_class"), "your.class.Name");

// Exec runnable
runnable.run();

client.close(); 
// close the client and its socket
```
