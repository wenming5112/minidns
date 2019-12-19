package com.thyc.minidns;

/**
 * @author wzm
 * @version 1.0.0
 * @date 2019/10/29 16:50
 **/

import org.xbill.DNS.*;

import java.io.IOException;
import java.net.*;
import java.util.Iterator;
import java.util.List;

public class UDPServer {
    private static DatagramSocket socket;

    public UDPServer() {
        //设置socket，监听端口53
        try {
            socket = new DatagramSocket(53);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        System.out.println("Starting。。。。。。\n");
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);
                //输出客户端的dns请求数据
                InetAddress sourceIpAddr = request.getAddress();
                int sourcePort = request.getPort();
                System.out.println("\nsourceIpAddr = " + sourceIpAddr.toString() + "\nsourcePort = " + sourcePort);
                //分析dns数据包格式
                Message indata = new Message(request.getData());
                System.out.println("\nindata = " + indata.toString());
                Record question = indata.getQuestion();
                System.out.println("question = " + question);
                String domain = indata.getQuestion().getName().toString();
                System.out.println("domain = " + domain);
                //解析域名
                InetAddress answerIpAddr = Address.getByName(domain);
                Message outdata = (Message)indata.clone();
                //由于接收到的请求为A类型，因此应答也为ARecord。查看Record类的继承，发现还有AAAARecord(ipv6)，CNAMERecord等
                Record answer = new ARecord(question.getName(), question.getDClass(), 64, answerIpAddr);
                outdata.addRecord(answer, Section.ANSWER);
                //发送消息给客户端
                byte[] buf = outdata.toWire();
                DatagramPacket response = new DatagramPacket(buf, buf.length, sourceIpAddr, sourcePort);
                socket.send(response);
            } catch (SocketException e) {
                System.out.println("SocketException:");
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("IOException:");
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
//        UDPServer udpServer = new UDPServer();
//        udpServer.start();

        //query();
        //transferZone();
        addRR();

    }

    static void query() throws UnknownHostException, TextParseException {
        // 远端的
        Resolver resolver = new SimpleResolver("192.168.2.117");
        resolver.setPort(53);
        // 先本地解析，本地没有就去远端
        // 获取a记录
        Lookup lookup = new Lookup("www.test.com",Type.A);
        lookup.setResolver(resolver);
        Cache cache=new Cache();
        lookup.setCache(cache);
        //执行解析
        lookup.run();
        //判断是否查询到结果
        if(lookup.getResult()==Lookup.SUCCESSFUL){
            String[] results=cache.toString().split("\\n");
            for(String result:results){
                System.out.println(result);
            }
        }
    }

    static void addRR() throws Exception {
        Name zone = Name.fromString("test.com.");
        Name host = Name.fromString("host", zone);
        Update update = new Update(zone, DClass.IN);
        Record record = new ARecord(host, DClass.IN, 3600, InetAddress.getByName("192.0.0.2"));
        update.add(record);
        Resolver resolver = new SimpleResolver("114.114.114.114");
        resolver.setPort(53);
        TSIG tsig = new TSIG("test_key", "epYaIl5VMJGRSG4WMeFW5g==");
        resolver.setTSIGKey(tsig);
        resolver.setTCP(true);
        Message response = resolver.send(update);
        System.out.println(response);
    }

    static void transferZone() throws Exception {
        ZoneTransferIn xfr = ZoneTransferIn.newAXFR(new Name("test.com."), "192.168.36.54", null);
        List records = xfr.run();
        Message response = new Message();
        response.getHeader().setFlag(Flags.AA);
        response.getHeader().setFlag(Flags.QR);
        // response.addRecord(query.getQuestion(),Section.QUESTION);
        Iterator it = records.iterator();
        while (it.hasNext()) {
            response.addRecord((Record) it.next(), Section.ANSWER);
        }
        System.out.println(response);
    }
}