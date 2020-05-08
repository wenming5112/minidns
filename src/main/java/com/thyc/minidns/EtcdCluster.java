package com.thyc.minidns;

/**
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
        this.endpoint = generateFormattedEndpoint(ip, port, tls);
    }

    protected void setTls(Boolean tls) {
        this.tls = tls;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void refreshEndpoint() {
        this.endpoint = generateFormattedEndpoint(ip, port, tls);
    }

    private String generateFormattedEndpoint(String ip, Integer port, Boolean tls) {
        String http = "http://";
        String https = "https://";
        if (tls) {
            return https + ip + ":" + port.toString();
        }
        return http + ip + ":" + port.toString();
    }
}
