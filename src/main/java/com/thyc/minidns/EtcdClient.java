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
 * etcd 客户端
 *
 * @author wzm
 * @version 1.0.0
 * @date 2020/5/8 16:53
 **/
public class EtcdClient {
    private List<EtcdCluster> etcdClusters;
    private String certPath;
    /**
     * ETCD客户端
     */
    private static Client client;
    /**
     * 字符编码
     */
    private static Charset charset = StandardCharsets.UTF_8;
    /**
     * 缓存时间
     */
    private static final Integer TTL = 600;
    /**
     * DNS name
     */
    private static final String DNS_NAME = "coredns";

    public EtcdClient(List<EtcdCluster> etcdClusters, Boolean tls) {
        for (EtcdCluster etcd : etcdClusters) {
            etcd.setTls(tls);
            etcd.generateFormattedEndpoint();
        }
        this.etcdClusters = etcdClusters;
        initClient();
    }

    public EtcdClient(List<EtcdCluster> etcdClusters, Boolean tls, String certPath) {
        for (EtcdCluster etcd : etcdClusters) {
            etcd.setTls(tls);
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
     * 初始化ETCD客户端，支持集群
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
     * 初始化ETCD客户端（TLS），支持集群
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
     * 新增/修改
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
     * 查询
     *
     * @param key        键
     * @param difference 区别（同一个域名可能对应多个ip）
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
     * 删除
     *
     * @param key        域名
     * @param difference 区别
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
