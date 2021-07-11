package asyncsocket;

import java.io.FileWriter;   
import java.lang.Object;
import java.util.concurrent.*;
import java.util.*;
import java.io.*;
import java.util.ArrayList;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.String;
//import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.List;
import java.text.DateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class BroadcastServer {
    //log
    Logger loggerErr = Logger.getLogger("Err");  
    Logger loggerIP = Logger.getLogger("IPList");  

    //IP List
    private List<SocketAddress>ips = new ArrayList<SocketAddress>();

    //create a socket channel and bind to local bind address
    AsynchronousServerSocketChannel serverSock;// =  AsynchronousServerSocketChannel.open().bind(sockAddr);
    AsynchronousServerSocketChannel serverSockMain;

    //server msg
    String msg = "";

    //IPAddr
    String IP = "0.0.0.0";
    int port = 55555;

    class Client{
        ArrayBlockingQueue<String> msgQueue = new ArrayBlockingQueue<String>(1024);
        AsynchronousSocketChannel channel;
    }

    // Create and main list of active clients based on their host name / ip address
    ConcurrentHashMap<String, Client> activeClients = new ConcurrentHashMap<>();


    public BroadcastServer( String bindAddr, int bindPort ) throws IOException {
        serverSock =  AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(bindAddr, bindPort));
        serverSockMain =  AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(IP, port));   

       //start to accept the connection from client
        serverSock.accept(serverSock, new CompletionHandler<AsynchronousSocketChannel,AsynchronousServerSocketChannel >() {
            @Override
            public void completed(AsynchronousSocketChannel sockChannel, AsynchronousServerSocketChannel serverSock ) {
                
                //a connection is accepted, start to accept next connection
                serverSock.accept( serverSock, this );
 
                //ipaddress
                String ipAdr = getChannelIp(sockChannel);
                System.out.println( ipAdr);

                //send msg when client connect
                //startWrite(sockChannel, msg);
                log(loggerIP, ipAdr + " accept");

                //start to read message from the client
                startRead( sockChannel );
                
            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel serverSock) {
                System.out.println( "fail to accept a connection");
            }
        } );

       //start to accept the connection from client
        serverSockMain.accept(serverSockMain, new CompletionHandler<AsynchronousSocketChannel,AsynchronousServerSocketChannel >() {

            @Override
            public void completed(AsynchronousSocketChannel sockChannel, AsynchronousServerSocketChannel serverSockMain ) {
                //a connection is accepted, start to accept next connection
                serverSockMain.accept( serverSockMain, this );

                //clear msg
                msg = "";

                //Print IP Address
                try{
                    System.out.println( sockChannel.getLocalAddress());
                }catch(IOException e) {

                    e.printStackTrace();
                }

                //start to read message from the client
                startRead( sockChannel );
                
            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel serverSockMain) {
                System.out.println( "fail to accept a connection");
            }
        } );
        

        log_init(loggerErr, "Err");
        log_init(loggerIP, "IPList");
    }


    private static String getString(ByteBuffer buf){
        byte[] bytes = new byte[buf.remaining()]; // create a byte array the length of the number of bytes written to the buffer
        buf.get(bytes); // read the bytes that were written
        String packet = new String(bytes);
        return packet;
    }

    private String getChannelIp(AsynchronousSocketChannel channel){

        //ipaddress
        String ipAdr = "";
        try{

            //Print IPAdress
            ipAdr = channel.getRemoteAddress().toString();
        }catch(IOException e) {
            e.printStackTrace();
        }

        return ipAdr;
    }

    private void startRead( AsynchronousSocketChannel sockChannel ) {
        final ByteBuffer buf = ByteBuffer.allocate(1024);
        
        //read message from client
        sockChannel.read( buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel >() {

            /**
             * some message is read from client, this callback will be called
             */
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel  ) {

                //ipaddress
                String ipAdr = getChannelIp(channel);

                buf.flip();
                //if client is close ,return
                if (buf.limit() == 0) return;

                //Print Message
                msg = getString(buf);

    
                //time
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
                System.out.println(sdf.format(new Date()) + " " + buf.limit() + " client: " + ipAdr + " \'" + msg + "\'   " );


                //Send To All Client
                try{
                    if (channel.getLocalAddress().toString().contains(String.valueOf(port))){
                        for(ConcurrentHashMap.Entry<String, Client> entry : activeClients.entrySet()){

                            //send queue msg
                            entry.getValue().msgQueue.offer(msg);
                            while(entry.getValue().msgQueue.size() > 0){
                                startWrite(entry.getValue().channel, entry.getValue().msgQueue.poll());
                            }
                        }          
                    }else if (msg.contains("getOP")){
                        // add map
                        // message received
                        activeClients.put(ipAdr, new Client());
                        activeClients.get(ipAdr).channel = channel;
                        System.out.println(sdf.format(new Date()) + ipAdr + " add to map:" + "," + msg);
                        log(loggerIP, ipAdr + " add to map:" + "," + msg);
                    }

                }catch(IOException e) {
                    e.printStackTrace();
                }

                // echo the message
                //startWrite( channel, buf );
                
                //start to read next message again
                startRead( channel );
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel ) {
                System.out.println( "fail to read message from client");
            }
        });
    }
        


    private void startWrite( final AsynchronousSocketChannel sockChannel, final String message) {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        buf.put(message.getBytes());
        buf.flip();

        try{
            sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel >() {
                @Override
                public void completed(Integer result, AsynchronousSocketChannel channel ) {
                    //after message written
                    //NOTHING TO DO
                }

                @Override
                public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                    System.out.println( "Fail to write the message to server");

                    //ipaddress
                    String ipAdr = getChannelIp(channel);
                    System.out.println( "remove:"+ ipAdr);
                    activeClients.remove(ipAdr);
                    log(loggerIP, ipAdr + " remove map");
                }
            });
        }catch(java.nio.channels.WritePendingException e){
            //ipaddress

            String ipAdr = getChannelIp(sockChannel);
            activeClients.get(ipAdr).msgQueue.offer(message);
            System.out.println(e.getMessage());
            log(loggerErr, ipAdr + "Exception:" + e.getMessage());
        }
    }

    public void log_init(Logger logger, String fileName) {  

        logger = Logger.getLogger(fileName);  
        FileHandler fh;  

        try {  

            // This block configure the logger with handler and formatter  
            fh = new FileHandler(fileName + ".log", true);  
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();  
            fh.setFormatter(formatter);  

        } catch (SecurityException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  

    }

    public void log(Logger logger, String msg) {  

        try {  
            logger.info(msg); 
        } catch (SecurityException e) {  
            e.printStackTrace();  
        } 

    }



    public static void main( String[] args ) {
        try {

            new BroadcastServer( "0.0.0.0", 3578 );
            //new BroadcastServer( "0.0.0.0", 19029 );

            for(;;){
                Thread.sleep(10*1000);
            }
        } catch (Exception ex) {
            Logger.getLogger(BroadcastServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    } 
}

