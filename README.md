# minidns
dns服务客户端（基于etcd添加解析记录）

## etcd + coredns 单机部署

### 目录结构
```text
# 位于/home/dns目录下
coredns_stand-alone
.
├── compose-coredns.yaml
├── coredns
│   └── conf
│       └── Corefile
├── etcd
│   ├── certs
│   │   ├── ca-config.json
│   │   ├── ca.csr
│   │   ├── ca-csr.json
│   │   ├── ca-key.pem
│   │   ├── ca.pem
│   │   ├── server.csr
│   │   ├── server-csr.json
│   │   ├── server-key.pem
│   │   └── server.pem
│   └── data
└── etcd-cert-gen.sh
```

### 执行流程
```shell
# 请执行按照前文提到的目录结构来存放这些文件
# 服务部署文件在当前项目的demo文件夹下
# Docker和Docker-compose的安装就略过
# 格式化脚本使用dos2unix
# 由于脚本文件在Windows下编辑过，其换行符与Unix不同
# 安装dos2unix
yum install -y dos2unix
# 脚本格式化
dos2unix etcd-cert-gen.sh
# 进入项目目录
cd coredns_stand-alone
# 脚本赋权
chmod +x etcd-cert-gen.sh
# 执行脚本，生成etcd的tls证书
./etcd-cert-gen.sh
# 启动coredns容器
docker-compose -f ./compose-coredns.yaml up -d
```

### .env
```env
############################################################
######                 Global Setting                 ######
############################################################
COMPOSE_PROJECT_NAME=coredns

# etcd
# etcd uses gcr.io/etcd-development/etcd as a primary container registry, and quay.io/coreos/etcd as secondary.
# ETCD_IMAGE_NAME=registry.cn-hangzhou.aliyuncs.com/coreos_etcd/etcd:v3
ETCD_IMAGE_NAME=quay.io/coreos/etcd:v3.3.20
ETCD_API_VERSION=3
ETCD_DATA_DIR=./etcd/data
ETCD_CERT_DIR=./etcd/certs

# coredns
COREDNS_IMAGE_NAME=coredns/coredns:1.6.9
COREDNS_CONF_DIR=./coredns/conf
```

### compose-coredns.yaml
```yaml
version: '3'

services:
  # etcd service
  etcd0:
    image: ${ETCD_IMAGE_NAME}
    container_name: etcd0
    restart: always
    environment:
    - ETCDCTL_API=${ETCD_API_VERSION}
    - TZ=CST-8
    - LANG=zh_CN.UTF-8
    command:
    - "/usr/local/bin/etcd"
    # 成员
    - "--name=etcd0"
    - "--data-dir=/etcd-data"
    - "--advertise-client-urls=https://0.0.0.0:2379"
    - "--listen-client-urls=https://0.0.0.0:2379"
    #- "--listen-peer-urls=https://0.0.0.0:2380"
    # 集群
    #- "--initial-advertise-peer-urls=https://0.0.0.0:2380"
    #- "--initial-cluster-token=etcd-cluster"
    #- "--initial-cluster"
    #- "etcd0=https://0.0.0.0:2380"
    #- "--initial-cluster-state=new"
    # 安全
    - "--trusted-ca-file=/etcd-certs/ca.pem"
    - "--cert-file=/etcd-certs/etcd.pem"
    - "--key-file=/etcd-certs/etcd-key.pem"
    #- "--peer-trusted-ca-file=/etcd-certs/etcd-root-ca.pem"
    #- "--peer-cert-file=/etcd-certs/etcd.pem"
    #- "--peer-key-file=/etcd-certs/etcd-key.pem"
    # 日志
    - "--debug=true"
    volumes:
    - ${ETCD_DATA_DIR}:/etcd-data:rw
    - ${ETCD_CERT_DIR}:/etcd-certs:ro
    ports:
    - 2379:2379
    - 2380:2380
  
  # coredns service
  coredns:
    image: ${COREDNS_IMAGE_NAME}
    container_name: coredns
    restart: always
    network_mode: host
    depends_on:
    - etcd0
    command: -conf /etc/coredns/Corefile
    volumes:
    - ${COREDNS_CONF_DIR}:/etc/coredns:ro
    - ${ETCD_CERT_DIR}:/etcd-certs:ro

```

### Corefile
```text
.:53 {
    # 监听tcp和udp的53端口
    # 配置启用etcd插件,后面可以指定域名,例如 etcd test.com {}
    etcd {
        # 启用存根区域功能。 stubzone仅在位于指定的第一个区域下方的etcd树中完成
        stubzones
        # etcd里面的路径。默认为/coredns，以后所有的dns记录就是存储在该存根路径底下
        path /coredns
        # etcd访问地址，多个空格分开
        endpoint https://127.0.0.1:2379
        # upstream设置要使用的上游解析程序解决指向外部域名的在etcd（认为CNAME）中找到的外部域名。
        upstream 114.114.114.114:53 8.8.8.8:53 8.8.4.4:53 /etc/resolv.conf
        # 如果区域匹配但不能生成记录，则将请求传递给下一个插件
        fallthrough
        # 可选参数，etcd认证证书设置
        # 格式: tls CERT KEY CACERT
        tls /etcd-certs/etcd.pem /etcd-certs/etcd-key.pem /etcd-certs/ca.pem
        # 指定访问etcd用户名和密码（根据实际情况使用）
        # credentials USERNAME PASSWORD
    }
    # 健康
    health
    # 监控插件
    prometheus
    # 缓存时间
    cache 160
    # 自动加载时间间隔
    reload 6s
    # 负载均衡，开启DNS记录轮询策略
    loadbalance
    # 上面etcd未查询到的请求转发给设置的DNS服务器解析
    forward . 8.8.8.8:53 8.8.4.4:53 /etc/resolv.conf
    # 打印日志
    log
    # 输出错误
    errors
}

```

### etcd-cert-gen.sh
```shell
#!/usr/bin/env bash

# 文档在Window下编辑过，需要转换为Unix格式。
# 安装工具: yum install -y dos2unix
# 然后执行命令: dos2unix ./etcd-cert-gen.sh

CFSSL_FILE="/usr/local/bin/cfssl"
CFSSL_JSON_FILE="/usr/local/bin/cfssljson"
CFSSL_CERTINFO_FILE="/usr/local/bin/cfssl-certinfo"

# 下载curl工具
# -----------------------
yum install -y curl

# 下载cfssl工具
# -----------------------
if [[ ! -f "$CFSSL_FILE" ]]; then
  curl -L https://pkg.cfssl.org/R1.2/cfssl_linux-amd64 -o ${CFSSL_FILE}
  chmod +x ${CFSSL_FILE}
  echo "------> cfssl has been installed successfully !! <------"
else
  echo "------> cfssl has already installed !! <------"
fi

if [[ ! -f "$CFSSL_JSON_FILE" ]]; then
  curl -L https://pkg.cfssl.org/R1.2/cfssljson_linux-amd64 -o ${CFSSL_JSON_FILE}
  chmod +x ${CFSSL_JSON_FILE}
  echo "------> cfssljson has been installed successfully !! <------"
else
  echo "------> cfssljson has already installed !! <------"
fi

if [[ ! -f "$CFSSL_CERTINFO_FILE" ]]; then
  curl -L https://pkg.cfssl.org/R1.2/cfssl-certinfo_linux-amd64 -o ${CFSSL_CERTINFO_FILE}
  chmod +x ${CFSSL_CERTINFO_FILE}
  echo "------> cfssl-certinfo has been installed successfully !! <------"
else
  echo "------> cfssl-certinfo has already installed !! <------"
fi

# 创建证书目录
# -----------------------
mkdir -p ./etcd/certs
cd  ./etcd/certs

# CA机构配置：有效期10年
# -----------------------
cat > ca-config.json <<EOF
{
  "signing": {
    "default": {
      "expiry": "87600h"
    },
    "profiles": {
      "server": {
         "expiry": "87600h",
         "usages": [
            "signing",
            "key encipherment",
            "server auth",
            "client auth"
        ]
      },
      "client": {
         "expiry": "87600h",
         "usages": [
            "signing",
            "key encipherment",
            "server auth",
            "client auth"
        ]
      }
    }
  }
}
EOF

# CA机构配置:　机构名称Comman Name，所在地Country国家, State省, Locality市
# -----------------------
cat > ca-csr.json <<EOF
{
    "CN": "etcd CA",
    "key": {
        "algo": "rsa",
        "size": 4096
    },
    "names": [
        {
            "C": "CN",
            "L": "HuNan",
            "O": "thyc",
            "ST": "ChangSha"
        }
    ]
}
EOF

# 如果是ETCD集群的话就直接在下面的hosts中添加IP或者域名。
# 向CA机构申请：证书注册 (中国，湖南省，长沙市), 提供服务的IP
# Organization Name, Common Name
# -----------------------
cat > server-csr.json <<EOF
{
    "CN": "etcd",
    "hosts": [
    "127.0.0.1",
    "192.168.1.111",
    "etcd0"
    ],
    "key": {
        "algo": "rsa",
        "size": 4096
    },
    "names": [
        {
            "C": "CN",
            "L": "HuNan",
            "O": "thyc",
            "ST": "ChangSha"
        }
    ]
}
EOF

# 使用定义好的配置初始化CA
cfssl gencert -initca ca-csr.json | cfssljson -bare ca -

# 生成服务器证书
cfssl gencert -ca=ca.pem -ca-key=ca-key.pem -config=ca-config.json -profile=server server-csr.json | cfssljson -bare etcd

```

## etcd client(java)



## 集群部署