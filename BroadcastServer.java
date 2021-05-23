package asyncsocket;

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

public class BroadcastServer {
    //Client List !!!!!!if client is close, must delete channel
    public ArrayList<AsynchronousSocketChannel> list = new ArrayList<>();

    //IP List
    private List<SocketAddress>ips = new ArrayList<SocketAddress>();

    //create a socket channel and bind to local bind address
    AsynchronousServerSocketChannel serverSock;// =  AsynchronousServerSocketChannel.open().bind(sockAddr);
    AsynchronousServerSocketChannel serverSockMain;

    public BroadcastServer( String bindAddr, int bindPort ) throws IOException {
        serverSock =  AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(bindAddr, bindPort));
        serverSockMain =  AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(bindAddr, 19029));   

       //start to accept the connection from client
        serverSock.accept(serverSock, new CompletionHandler<AsynchronousSocketChannel,AsynchronousServerSocketChannel >() {
            @Override
            public void completed(AsynchronousSocketChannel sockChannel, AsynchronousServerSocketChannel serverSock ) {
                
                //a connection is accepted, start to accept next connection
                serverSock.accept( serverSock, this );

                try{
                    //Print IP Address
                    System.out.println( sockChannel.getLocalAddress().toString());

                    //Add To Client List
                    list.add(list.size(), sockChannel);

                }catch(IOException e) {
                    e.printStackTrace();
                }

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

                //Print IP Address
                try{
                    System.out.println( sockChannel.getLocalAddress());
                }catch(IOException e) {

                    e.printStackTrace();
                }

                //Add To Client List
                //list.add(list.size(), sockChannel);

                //start to read message from the client
                startRead( sockChannel );
                
            }

            @Override
            public void failed(Throwable exc, AsynchronousServerSocketChannel serverSockMain) {
                System.out.println( "fail to accept a connection");
            }
        } );
        
    }


    private static String getString(ByteBuffer buf){
        byte[] bytes = new byte[buf.remaining()]; // create a byte array the length of the number of bytes written to the buffer
        buf.get(bytes); // read the bytes that were written
        String packet = new String(bytes);
        return packet;
    }

    private void startRead( AsynchronousSocketChannel sockChannel ) {
        final ByteBuffer buf = ByteBuffer.allocate(2048);
        
        //read message from client
        sockChannel.read( buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel >() {

            /**
             * some message is read from client, this callback will be called
             */
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel  ) {

                //ipaddress
                String ipAdr = "";
                try{

                    //Print IPAdress
                    ipAdr = channel.getRemoteAddress().toString();
                    System.out.println(ipAdr);
                }catch(IOException e) {
                    e.printStackTrace();
                }

                buf.flip();
                //if client is close ,return
                if (buf.limit() == 0) return;

                //Print Message
                String msg = getString(buf);

                //time
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
                System.out.println(sdf.format(new Date()) + " " + buf.limit() + " client: " + ipAdr + " " + msg + "   " );


                //Send To All Client
                if (ipAdr.contains("19029")){
                    for(int i = 0; i < list.size(); i++){
                        startWrite(list.get(i), msg);
                    }
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
        ByteBuffer buf = ByteBuffer.allocate(2048);
        buf.put(message.getBytes());
        buf.flip();
        sockChannel.write(buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel >() {
            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel ) {
                //after message written
                //NOTHING TO DO
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println( "Fail to write the message to server");
            }
        });
    }
    public static void main( String[] args ) {
        try {
            new BroadcastServer( "0.0.0.0", 3575 );
            //new BroadcastServer( "0.0.0.0", 19029 );

            for(;;){
                Thread.sleep(10*1000);
            }
        } catch (Exception ex) {
            Logger.getLogger(BroadcastServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    } 
}
