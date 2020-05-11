package com.thyc.minidns.test;

import com.thyc.minidns.EtcdClient;
import com.thyc.minidns.EtcdCluster;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wzm
 * @version 1.0.0
 * @date 2020/5/8 17:06
 **/
public class EtcdClientTest {
    /**
     * 启用TLS（测试通过）
     */
    @Test
    public void testWithTls() {
        String certPath = "/etcd/ssl/cert/ca.pem";
        EtcdCluster etcdCluster = new EtcdCluster("192.168.1.111", 2379);
        List<EtcdCluster> etcdClusters = new ArrayList<>(1);
        etcdClusters.add(etcdCluster);
        EtcdClient etcdClient = new EtcdClient(etcdClusters, true, certPath);

        String ip = "192.168.1.120";
        String domain = "www.mytest.com";
        String differ = "";
        System.out.println(etcdClient.put(domain, ip, differ));
        System.out.println(etcdClient.get(domain, differ));
        System.out.println(etcdClient.delete(domain, differ));
        etcdClient.close();
    }

    /**
     * 不启用TLS（测试通过）
     */
    @Test
    public void testWithoutTls() {
        EtcdCluster etcdCluster = new EtcdCluster("192.168.1.111", 2379);
        List<EtcdCluster> etcdClusters = new ArrayList<>(3);
        etcdClusters.add(etcdCluster);
        EtcdClient etcdClient = new EtcdClient(etcdClusters, false);

        String ip = "192.168.1.183";
        String domain = "www.test.com";
        String differ = "";
        System.out.println(etcdClient.put(domain, ip, differ));
        System.out.println(etcdClient.get(domain, differ));
        System.out.println(etcdClient.delete(domain, differ));
        etcdClient.close();
    }


}
