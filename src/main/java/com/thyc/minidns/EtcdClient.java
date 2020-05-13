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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * etcd Client
 *
 * @author wzm
 * @version 1.0.0
 * @date 2020/5/8 16:53
 **/
public class EtcdClient {
    private List<EtcdCluster> etcdClusters;
    private String certPath;
    /**
     * etcd Client
     */
    private static Client client;
    /**
     * Charset
     */
    private static Charset charset = StandardCharsets.UTF_8;
    /**
     * Cache time
     */
    private static final Integer TTL = 600;
    /**
     * DNS name
     */
    private static final String DNS_NAME = "coredns";

    public EtcdClient(List<EtcdCluster> etcdClusters) {
        for (EtcdCluster etcd : etcdClusters) {
            etcd.setTls(false);
            etcd.generateFormattedEndpoint();
        }
        this.etcdClusters = etcdClusters;
        initClient();
    }

    public EtcdClient(List<EtcdCluster> etcdClusters, String certPath) {
        for (EtcdCluster etcd : etcdClusters) {
            etcd.setTls(true);
            etcd.generateFormattedEndpoint();
        }
        this.etcdClusters = etcdClusters;
        this.certPath = certPath;
        initClientWithTls();
    }

    public void close() {
        client.close();
    }

    /**
     * Initialize the etcd client to support clustering
     *
     * @author wzm
     * @date 2019/11/1 10:19
     */
    private void initClient() {
        String[] endpoints = new String[etcdClusters.size()];
        for (int i = 0; i < etcdClusters.size(); i++) {
            endpoints[i] = etcdClusters.get(i).getEndpoint();
        }
        client = Client.builder().endpoints(endpoints).build();
    }

    /**
     * Initialize the etcd client (TLS) to support clustering
     *
     * @author wzm
     * @date 2019/11/1 10:20
     */
    private void initClientWithTls() {
        String[] endpoints = new String[etcdClusters.size()];
        for (int i = 0; i < etcdClusters.size(); i++) {
            endpoints[i] = etcdClusters.get(i).getEndpoint();
        }
        try (InputStream is = getClass().getResourceAsStream(certPath)) {
            //还需要设置证书路径
            client = Client.builder()
                    .endpoints(endpoints)
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
     * New/modified
     *
     * @param key        域名
     * @param value      ip
     * @param difference 区别
     * @author wzm
     * @date 2019/11/1 9:45
     */
    public boolean putRecord(String key, String value, String difference) {
        try {
            KV kv = client.getKVClient();
            ByteSequence k = ByteSequence.from(formatKey(key, difference), charset);
            ByteSequence v = ByteSequence.from(formatValue(value, TTL), charset);
            // put the key-value
            kv.put(k, v).join();
            return kv.get(k).join().getCount() == 1;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * query
     *
     * @param key        key
     * @param difference differ（The same domain name may correspond to more than one IP）
     * @return java.util.Map
     * @author wzm
     * @date 2019/11/1 10:02
     */
    public Map<String, String> getRecord(String key, String difference) {
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
     * delete
     *
     * @param key        dns name
     * @param difference differ
     * @author wzm
     * @date 2019/11/1 10:17
     */
    public boolean deleteRecord(String key, String difference) {
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
     * Get the key from kv
     *
     * @param kv kv
     * @return java.lang.String
     * @author wzm
     * @date 2019/11/1 9:59
     */
    private static String getKeyFromKv(KeyValue kv) {
        return kv.getKey().toString(charset);
    }

    /**
     * Get the value from kv
     *
     * @param kv kv
     * @return java.lang.String
     * @author wzm
     * @date 2019/11/1 9:59
     */
    private static String getValueFromKv(KeyValue kv) {
        return kv.getValue().toString(charset);
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
    //A记录
    //etcdctl put /coredns/com/leffss/www '{"host":"1.1.1.1","ttl":10}'
    //AAAA记录
    //etcdctl put /coredns/com/leffss/www '{"host":"1002::4:2","ttl":10}'
    //CNAME记录
    //etcdctl put /coredns/com/leffss/www '{"host":"www.baidu.com","ttl":10}'
    //SRV记录
    //etcdctl put /coredns/com/leffss/www '{"host":"www.baidu.com","port":80,"ttl":10}'
    //TXT记录
    //etcdctl put /coredns/com/leffss/www '{"text":"This is text!","ttl":10}'
}
