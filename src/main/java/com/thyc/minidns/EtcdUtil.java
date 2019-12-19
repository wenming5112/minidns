package com.thyc.minidns;

import com.google.gson.JsonObject;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.grpc.netty.GrpcSslContexts;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * etcd 链接和操作工具，包括启动监听 操作etcd v3 版本协议，此操作不支持v2 版本协议。
 * v2版本的协议可以参考 https://www.cnblogs.com/laoqing/p/8967549.html
 */

public class EtcdUtil {
    /**
     * ETCD客户端
     */
    private static Client client;
    /**
     * 字符编码
     */
    private static Charset charset = Charset.forName("UTF-8");
    /**
     * 缓存时间
     */
    private static final Integer TTL = 600;
    /**
     * DNS名
     */
    private static final String DNS_NAME = "coredns";

    private static EtcdUtil etcdUtil = new EtcdUtil();

    public static void main(String[] args) {
        String etcdIp = "192.168.2.155";
        String etcdPort = "2379";
        String etcdCerPath = "/etcd/ssl/cert/ca.pem";

        String ip = "192.168.2.183";
        String domain = "www.myname2.com";
        String differ = "";
        // create client
        EtcdUtil.initClientWithTls(etcdIp, etcdPort, etcdCerPath);
        // put the key-value
        System.out.println("执行新增：" + put(domain, ip, differ));
        // get the CompletableFuture
        System.out.println("查询结果：" + get(domain, differ));
        // delete the key
        System.out.println("执行删除：" + delete(domain, differ));
        client.close();
    }

    /**
     * 初始化ETCD客户端
     *
     * @param ip   IP
     * @param port 端口
     * @author wzm
     * @date 2019/11/1 10:19
     */
    public static void initClient(String ip, String port) {
        client = Client.builder().endpoints(getLocation(ip, port, false)).build();
    }

    /**
     * 初始化ETCD客户端（TLS）
     *
     * @param ip       IP
     * @param port     端口
     * @param certPath 证书路径
     * @author wzm
     * @date 2019/11/1 10:20
     */
    public static void initClientWithTls(String ip, String port, String certPath) {
        try (InputStream is = etcdUtil.getClass().getResourceAsStream(certPath)) {
            //还需要设置证书路径
            client = Client.builder()
                    .endpoints(getLocation(ip, port, true))
                    .sslContext(
                            GrpcSslContexts
                                    .forClient()
                                    .trustManager(is)
                                    .build()
                    ).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 新增/修改
     *
     * @param key        域名
     * @param value      ip
     * @param difference 区别
     * @author wzm
     * @date 2019/11/1 9:45
     */
    public static boolean put(String key, String value, String difference) {
        try {
            KV kv = client.getKVClient();
            ByteSequence k = ByteSequence.from(formatKey(key, difference), charset);
            ByteSequence v = ByteSequence.from(formatValue(value, TTL), charset);
            // put the key-value
            kv.put(k, v).join();
            return kv.get(k).join().getCount() == 1;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 查询
     *
     * @param key        键
     * @param difference 区别（同一个域名可能对应多个ip）
     * @return java.util.Map
     * @author wzm
     * @date 2019/11/1 10:02
     */
    public static Map<String, String> get(String key, String difference) {
        try {
            Map<String, String> map = new HashMap<>(1);
            KV kvClient = client.getKVClient();
            ByteSequence k = ByteSequence.from(formatKey(key, difference), charset);
            // get the CompletableFuture
            CompletableFuture<GetResponse> getFuture = kvClient.get(k);
            // get the value from CompletableFuture
            GetResponse response = getFuture.get();
            List<KeyValue> keyValues = response.getKvs();
            for (KeyValue kv : keyValues) {
                map.put(getKeyFromKv(kv), getValueFromKv(kv));
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 删除
     *
     * @param key        域名
     * @param difference 区别
     * @author wzm
     * @date 2019/11/1 10:17
     */
    public static boolean delete(String key, String difference) {
        try {
            KV kvClient = client.getKVClient();
            CompletableFuture<GetResponse> getFeature = kvClient.get(ByteSequence.from(formatKey(key, difference), charset));
            GetResponse resp = getFeature.get();
            ByteSequence k = ByteSequence.from(formatKey(key, difference), charset);
            DeleteResponse deleteResponse = kvClient.delete(k).get();
            long f = deleteResponse.getDeleted();
            return f == resp.getKvs().size();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 从kv中获取键
     *
     * @param kv 键值对象
     * @return java.lang.String
     * @author wzm
     * @date 2019/11/1 9:59
     */
    private static String getKeyFromKv(KeyValue kv) {
        return kv.getKey().toString(charset);
    }

    /**
     * 从kv中获取值
     *
     * @param kv 键值对象
     * @return java.lang.String
     * @author wzm
     * @date 2019/11/1 9:59
     */
    private static String getValueFromKv(KeyValue kv) {
        return kv.getValue().toString(charset);
    }

    /**
     * 获取地址
     *
     * @param ip   ip
     * @param port duank
     * @param tls  是否开启tls
     * @return java.lang.String
     * @author wzm
     * @date 2019/11/1 10:00
     */
    private static String getLocation(String ip, String port, Boolean tls) {
        String http = "http://";
        String https = "https://";
        if (!tls) {
            return http + ip + ":" + port;
        }
        return https + ip + ":" + port;
    }

    private static String formatKey(String domainName, String difference) {
        String[] strings = domainName.split("\\.");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("/" + DNS_NAME);
        for (int i = 0; i < strings.length; i++) {
            String tmp = strings[strings.length - 1 - i];
            stringBuilder.append("/").append(tmp);
        }
        String str = stringBuilder.toString();
        if (difference == null || "".equals(difference.trim())) {
            return str;
        }
        return str + "/" + difference;
    }

    private static String formatValue(String host, int ttl) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("host", host);
        jsonObject.addProperty("ttl", ttl);
        return jsonObject.toString();
    }
}