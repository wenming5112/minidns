# minidns
dns服务客户端（基于etcd添加解析记录）

# ETCD 单机部署测试

### 目录结构
```text
# 位于/home/dns目录下
.
├── .env
└── compose-etcd1.yaml
```

### 环境变量文件
```dotenv
############################################################
###### Global Setting
############################################################
# image version
ETCD_IMAGE_NAME=quay.io/coreos/etcd:v3.3.12
#ETCD_IMAGE_NAME=registry.cn-hangzhou.aliyuncs.com/coreos_etcd/etcd:v3
ETCD_API_VERSION=3

# etcd node 1
ETCD_NODE_1_DATA_DIR=/home/dns/etcd/node_1_data
ETCD_NODE_1_CONFIG_DIR=/home/dns/etcd/node_1_conf
ETCD_NODE_1_IP_ADDR=192.168.31.102
```

### compose-etcd1.yaml 启动文件

```yaml
version: '2'
networks:
  thyc_dns:
    driver: bridge

services:
  etcd_1:
    image: ${ETCD_IMAGE_NAME}
    container_name: etcd_1
    environment:
    - ETCDCTL_API=${ETCD_API_VERSION}
    #- TZ=CST-8
    #- LANG=zh_CN.UTF-8
    command:
    - "/usr/local/bin/etcd"
    - "--name"
    - "etcd_1"
    - "--data-dir"
    - "/etcd-data"
    - "--advertise-client-urls"
    - "http://0.0.0.0:2379"
    - "--listen-client-urls"
    - "http://0.0.0.0:2379"
    - "--listen-peer-urls"
    - "http://0.0.0.0:2380"
    - "--initial-advertise-peer-urls"
    - "http://0.0.0.0:2380"
    - "--initial-cluster-token"
    - "etcd-cluster"
    - "--initial-cluster"
    - "etcd_1=http://0.0.0.0:2380"
    - "--initial-cluster-state"
    - "new"
    volumes:
    - ${ETCD_NODE_1_DATA_DIR}:/etcd-data:rw
    #- ${ETCD_NODE_1_CONFIG_DIR}:/etc/etcd/etcd.conf:ro
    ports:
    - 2379:2379
    - 2380:2380
    networks:
    - thyc_dns
```

### 验证网络
```shell
# 使用ETCD的rest api新增
curl -L http://127.0.0.1:2379/v2/keys/foo -XPUT -d value="Hello foo"
# 使用ETCD的rest api查询
curl -L http://127.0.0.1:2379/v2/keys/foo
```