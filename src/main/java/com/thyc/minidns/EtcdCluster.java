package com.thyc.minidns;

/**
 * etcd node
 *
 * @author wzm
 * @version 1.0.0
 * @date 2020/5/8 17:21
 **/
public class EtcdCluster {
    private String ip;
    private Integer port;
    private Boolean tls;
    private String endpoint;

    public EtcdCluster(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
    }

    protected void setTls(Boolean tls) {
        this.tls = tls;
    }

    public String getEndpoint() {
        return endpoint;
    }

    protected void generateFormattedEndpoint() {
        String http = "http://";
        String https = "https://";
        if (tls) {
            this.endpoint = https + ip + ":" + port.toString();
        }else {
            this.endpoint = http + ip + ":" + port.toString();
        }
    }
}
