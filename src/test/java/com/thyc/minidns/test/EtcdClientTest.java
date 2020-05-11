package com.thyc.minidns.test;

import com.thyc.minidns.EtcdClient;
import com.thyc.minidns.EtcdCluster;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * test
 *
 * @author wzm
 * @version 1.0.0
 * @date 2020/5/8 17:06
 **/
public class EtcdClientTest {
    /**
     * Enable TLS (test passed)
     */
    @Test
    public void testWithTls() {
        String certPath = "/etcd/ssl/cert/ca.pem";
        EtcdCluster etcdCluster = new EtcdCluster("192.168.1.111", 2379);
        List<EtcdCluster> etcdClusters = new ArrayList<>(1);
        etcdClusters.add(etcdCluster);
        EtcdClient etcdClient = new EtcdClient(etcdClusters, certPath);

        String ip = "192.168.1.120";
        String domain = "www.mytest.com";
        String differ = "";
        System.out.println(etcdClient.putRecord(domain, ip, differ));
        System.out.println(etcdClient.getRecord(domain, differ));
        System.out.println(etcdClient.deleteRecord(domain, differ));
        etcdClient.close();
    }

    /**
     * Not enabling TLS (test passed)
     */
    @Test
    public void testWithoutTls() {
        EtcdCluster etcdCluster = new EtcdCluster("192.168.1.111", 2379);
        List<EtcdCluster> etcdClusters = new ArrayList<>(3);
        etcdClusters.add(etcdCluster);
        EtcdClient etcdClient = new EtcdClient(etcdClusters);

        String ip = "192.168.1.120";
        String domain = "www.mytest.com";
        String differ = "";
        System.out.println(etcdClient.putRecord(domain, ip, differ));
        System.out.println(etcdClient.getRecord(domain, differ));
        System.out.println(etcdClient.deleteRecord(domain, differ));
        etcdClient.close();
    }

}
