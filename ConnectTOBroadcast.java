package asyncsocket;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Steven
 */
public class  ConnectTOBroadcast{

    AtomicInteger messageWritten = new AtomicInteger( 0 );
    AtomicInteger messageRead = new AtomicInteger( 0 );

    //create a socket channel
    AsynchronousSocketChannel sockChannel = AsynchronousSocketChannel.open();
    AsynchronousSocketChannel sockChannelToBroadcast = AsynchronousSocketChannel.open();

    public ConnectTOBroadcast( String host, int port, final String message) throws IOException {

        //try to connect to the server side
        sockChannel.connect( new InetSocketAddress(host, port), sockChannel, new CompletionHandler<Void, AsynchronousSocketChannel >() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel channel ) {
                //start to read message
                startRead( channel );
                
                //write an message to server side
                //startWrite( sockChannelToBroadcast, message, messageWritten );
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println( "fail to connect to server");
            }
            
        });

        //try to connect to the server side
        sockChannelToBroadcast.connect( new InetSocketAddress(host, 19029), sockChannelToBroadcast, new CompletionHandler<Void, AsynchronousSocketChannel >() {
            @Override
            public void completed(Void result, AsynchronousSocketChannel channel ) {
                //start to read message
                //startRead( channel,messageRead );
                
                //write an message to server side
                //startWrite( channel, message, messageWritten );
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println( "fail to connect to server");
            }
            
        });
    }
   
    private void startRead( final AsynchronousSocketChannel sockChannel) {
        final ByteBuffer buf = ByteBuffer.allocate(2048);
        
        sockChannel.read( buf, sockChannel, new CompletionHandler<Integer, AsynchronousSocketChannel>(){

            @Override
            public void completed(Integer result, AsynchronousSocketChannel channel) {   
                //message is read from server
                messageRead.getAndIncrement();
                
                //print the message
                System.out.println( "Read message:" + new String( buf.array()) );

                startWrite( sockChannelToBroadcast,  new String( buf.array())  );
                startRead( sockChannel );
            }

            @Override
            public void failed(Throwable exc, AsynchronousSocketChannel channel) {
                System.out.println( "fail to read message from server");
            }
            
        });
        
    }
    private void startWrite( final AsynchronousSocketChannel sockChannel, final String message) {
        ByteBuffer buf = ByteBuffer.allocate(2048);
        buf.put(message.getBytes());
        buf.flip();
        messageWritten.getAndIncrement();
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
    
    public static void main( String...args ) {
        try {

            
            //for( int i = 0; i < 100; i++ ) {
            new ConnectTOBroadcast( "127.0.0.1", 19030, "echo test 19 ");
            //}
            for(;;){
                Thread.sleep(1000*10);
            }
   
        } catch (Exception ex) {
            Logger.getLogger(ConnectTOBroadcast.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
