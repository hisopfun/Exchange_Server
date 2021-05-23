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
import java.util.concurrent.atomic.AtomicInteger;

public class MatchServer {
    //BS=,price=,qty=
    public class order{
        String sysTime;
        String time;
        String stockNo;
        String BS;
        float price;
        int qty;
        String ipAdr;
        int sequence;

        public order(String nSysTime, String nTime, String nStockNo, String nBS, float nPrice, int nQty, String nIPAdr, int nSequence){
            sysTime = nSysTime;
            time = nTime;
            stockNo = nStockNo;
            BS = nBS;
            price = nPrice;
            qty = nQty;
            ipAdr = nIPAdr;
            sequence = nSequence;
        }

    }
    ArrayList<order> match = new ArrayList<order>();
    ArrayList<order> bid = new ArrayList<order>();
    ArrayList<order> ask = new ArrayList<order>();
    AtomicInteger ordTime = new AtomicInteger( 0 );

    //Client List
    private ArrayList<AsynchronousSocketChannel> list = new ArrayList<>();

    //IP List
    private List<SocketAddress>ips = new ArrayList<SocketAddress>();

    //Destination client
    AsynchronousSocketChannel clientChannel;

    //create a socket channel and bind to local bind address
    AsynchronousServerSocketChannel serverSock;// =  AsynchronousServerSocketChannel.open().bind(sockAddr);
    AsynchronousServerSocketChannel serverSockMain;

    public MatchServer( String bindAddr, int bindPort ) throws IOException {
        serverSock =  AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(bindAddr, bindPort));
        serverSockMain =  AsynchronousServerSocketChannel.open().bind(new InetSocketAddress("127.0.0.1", 19030));   

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
                    clientChannel = sockChannel;
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

    private order ParseOrder(String sysTime, String orderMsg, String ipAdr){
        orderMsg = orderMsg.replace("\n", "");
        String time = orderMsg.split(",")[0];
        String stockNo = orderMsg.split(",")[1];
        String bs = orderMsg.split(",")[2];
        float price = Float.parseFloat(orderMsg.split(",")[3]);
        int qty = Integer.parseInt(orderMsg.split(",")[4]);

        return new order(sysTime, time, stockNo, bs, price, qty, ipAdr, ordTime.incrementAndGet());
    }
//2021/05/23 15:55:14.763,TXF,B,10000.0,1,127.0.0.1
//2021/05/23 15:55:14.763,TXF,S,10000.0,1,127.0.0.1
    private void limit(order ord, ArrayList<order> lim){
        //B
        if (ord.BS.equals("B")){
            System.out.println("ord:" + ord.BS + " " + ord.qty + " " + ord.price);
            for(int i = 0; i < lim.size(); i++){
                if (ord.price > lim.get(i).price){
                    lim.add(i, ord);
                    break;
                }
                if (lim.size()-1 == i){
                    lim.add(i, ord);
                    break;
                }
            }
            if (lim.size() == 0)    lim.add(ord);
        }

        //S
        if (ord.BS.equals("S")){
            System.out.println("ord:" + ord.BS + " " + ord.qty + " " + ord.price);
            for(int i = 0; i < lim.size(); i++){
                if (ord.price <= lim.get(i).price){
                    lim.add(i, ord);
                    break;
                }
                if (lim.size()-1 == i){
                    lim.add(i, ord);
                    break;
                }
            }
            if (lim.size() == 0)    lim.add(ord);
        }

        //Print
        //System.out.println("limit:" + ord.BS + " " + ord.qty + " " + ord.price);
        for(int i = 0; i < lim.size(); i++) 
            System.out.println(lim.get(i).BS + lim.get(i).price);
    }

    private boolean trade( ArrayList<order> bid, ArrayList<order> ask, ArrayList<order> mat){
        if (bid.size() == 0 || ask.size() == 0) return false;
        if (bid.get(0).price > ask.get(0).price){
            mat.add(new order(bid.get(0).sysTime, "", bid.get(0).stockNo, bid.get(0).BS, bid.get(0).price
                , bid.get(0).qty, "", 0));
            reportClient(0, bid, "Mat report:" + bid.get(0).sysTime + "," + bid.get(0).stockNo+","+
                bid.get(0).BS+","+bid.get(0).qty+","+ask.get(0).price);
            reportClient(0, ask, "Mat report:" + ask.get(0).sysTime + "," + ask.get(0).stockNo+","+
                ask.get(0).BS+","+ask.get(0).qty+","+ask.get(0).price);
            bid.remove(0);
            ask.remove(0);
        }

        if (bid.get(0).price < ask.get(0).price){
            mat.add(new order(ask.get(0).sysTime, "", ask.get(0).stockNo, ask.get(0).BS, ask.get(0).price
                , ask.get(0).qty, "", 0));
            reportClient(0, bid, "Mat report:" + bid.get(0).sysTime + "," + bid.get(0).stockNo+","+
                bid.get(0).BS+","+bid.get(0).qty+","+bid.get(0).price);
            reportClient(0, ask, "Mat report:" + ask.get(0).sysTime + "," + ask.get(0).stockNo+","+
                ask.get(0).BS+","+ask.get(0).qty+","+bid.get(0).price);
            bid.remove(0);
            ask.remove(0);
        }

        if (bid.get(0).price == ask.get(0).price){
            mat.add(new order(ask.get(0).sysTime, "", ask.get(0).stockNo, ask.get(0).BS, ask.get(0).price
                , ask.get(0).qty, "", 0));
            reportClient(0, bid, "Mat report:" + bid.get(0).sysTime + "," + bid.get(0).stockNo+","+
                bid.get(0).BS+","+bid.get(0).qty+","+bid.get(0).price);
            reportClient(0, ask, "Mat report:" + ask.get(0).sysTime + "," + ask.get(0).stockNo+","+
                ask.get(0).BS+","+ask.get(0).qty+","+ask.get(0).price);
            bid.remove(0);
            ask.remove(0);
        }

        // //Buy
        // if (ord.BS.equals("B")){
        //     if (ord.price >= lim.get(0).price){
        //         ord.price = lim.get(0).price;
        //         ord.sysTime = getSysTime();
        //         //ord.ipAdr += lim.get(0).ipAdr;
        //         mat.add(ord);
        //         --lim.get(0).qty;
        //         if (lim.get(0).qty == 0){
        //             lim.removeMatNode(0, mat);
        //         }
        //         reportClient(0, mat, "Mat report:" + mat.get(mat.size()-1).sysTime + "," + mat.get(mat.size()-1).stockNo+","+
        //                 mat.get(mat.size()-1).BS+","+mat.get(mat.size()-1).qty);
        //         System.out.print("match:" + mat.get(mat.size()-1).BS + mat.get(mat.size()-1).price + " " + mat.get(mat.size()-1).ipAdr);
        //         //System.out.println("trade:" + match);
        //         return true;
        //     }
        // }

        // //Sell
        // if (ord.BS.equals("S")){
        //     if (ord.price <= lim.get(0).price){
        //         ord.price = lim.get(0).price;
        //         ord.sysTime = getSysTime();
        //         //ord.ipAdr += lim.get(0).ipAdr;
        //         mat.add(ord);
        //         --lim.get(0).qty;
        //         if (lim.get(0).qty == 0){
        //             lim.removeMatNode(0, mat);
        //         }
        //         System.out.println("match:" + mat.get(mat.size()-1).BS + mat.get(mat.size()-1).price + " " + mat.get(mat.size()-1).ipAdr);
        //         //System.out.println("trade:" + match);
        //         return true;
        //     }
        // }
        // //System.out.print("match:" + match);
        return false;
    }

    private String getSysTime(){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
        return sdf.format(new Date());
    } 

    private void reportClient(int n, ArrayList<order> lim, String msg){
        try{
            //Report to client
            for(int i = 0; i < list.size(); i++){
                if (list.get(i).getRemoteAddress().toString().contains(lim.get(n).ipAdr)){
                    System.out.println("Send To client:" + msg + lim.get(n).ipAdr);
                    startWrite(list.get(i), msg);
                }
            }
        }catch(Exception ex){

        }
    }

    // private void removeNode(int n, ArrayList<order> lim){

    //     lim.remove(n);
    // }

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

                //if client is close ,return
                buf.flip();
                if (buf.limit() == 0) return;

                //Print Message
                String msg = getString(buf);

                //time
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                sdf.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
                System.out.println(sdf.format(new Date()) + " " + buf.limit() + " client: "+ ipAdr + " " + msg);

                //
                order ord = null;
                try{
                    ord = ParseOrder(sdf.format(new Date()), msg, ipAdr);
                }catch(Exception ex){
                    startRead( channel );
                    return;
                }
                //2021/05/23 15:55:14.763,TXF,B,10000.0,1,127.0.0.1
                //2021/05/23 15:55:14.763,TXF,S,10000.0,1,127.0.0.1

                if (ord.BS.equals("B")) {
                    limit(ord, bid);
                    trade(bid, ask, match);
                }
                if (ord.BS.equals("S")) {
                    limit(ord, ask);
                    trade(bid, ask, match);
                }
                System.out.println("Blist:" + bid.size() + " Alist:" + ask.size());
                //Send To 
                //startWrite(clientChannel, msg);

                // echo the message
//------------------>                //startWrite( channel, buf );
                
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
            new MatchServer( "0.0.0.0", 3576 );

            for(;;){
                Thread.sleep(10*1000);
            }
        } catch (Exception ex) {
            Logger.getLogger(MatchServer.class.getName()).log(Level.SEVERE, null, ex);
        }
    } 
}
