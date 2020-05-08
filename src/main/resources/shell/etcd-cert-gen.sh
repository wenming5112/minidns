#!/usr/bin/env bash

# 文档在Window下编辑过，需要转换为Unix格式。
# 安装工具: yum install -y dos2unix
# 然后执行命令: dos2unix ./etcd-cert-gen.sh

# 下载curl工具
yum install -y curl
# 下载cfssl工具
curl -L https://pkg.cfssl.org/R1.2/cfssl_linux-amd64 -o /usr/local/bin/cfssl
curl -L https://pkg.cfssl.org/R1.2/cfssljson_linux-amd64 -o /usr/local/bin/cfssljson
curl -L https://pkg.cfssl.org/R1.2/cfssl-certinfo_linux-amd64 -o /usr/local/bin/cfssl-certinfo

chmod +x /usr/local/bin/cfssl*

# IP_ADDR=192.168.1.111,192.168.1.112,192.168.1.113
IP_ADDR=127.0.0.1,192.168.1.111

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
        "size": 2048
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

# 向CA机构申请：证书注册 (中国，湖南省，长沙市), 提供服务的IP
# Organization Name, Common Name
# -----------------------
cat > server-csr.json <<EOF
{
    "CN": "etcd",
    "hosts": [
    "\${IP_ADDR}\"
    ],
    "key": {
        "algo": "rsa",
        "size": 2048
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
cfssl gencert -ca=ca.pem -ca-key=ca-key.pem -config=ca-config.json -profile=server server-csr.json | cfssljson -bare server
