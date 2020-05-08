package java;

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
    @Test
    public void test() {
        String certPath = "/etcd/ssl/cert/ca.pem";
        EtcdCluster etcdCluster = new EtcdCluster("192.168.1.111", 2379);
        List<EtcdCluster> etcdClusters = new ArrayList<>(3);
        etcdClusters.add(etcdCluster);
        EtcdClient etcdClient = new EtcdClient(etcdClusters, true, certPath);

        String ip = "192.168.1.183";
        String domain = "www.test.com";
        String differ = "";
        etcdClient. put(domain, ip, differ);
        etcdClient. get(domain, differ);
        etcdClient. delete(domain, differ);
        etcdClient.close();
    }
}
