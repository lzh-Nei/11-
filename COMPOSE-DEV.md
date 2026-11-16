# COMPOSE-DEV：8容器一键开发环境运维手册

> 对应 `compose-dev.yml`（服务：nginx / app / mysql / redis / rabbitmq / minio / ollama / ocr，
> 另含 2 个一次性 init 容器）。上游依据：06-部署框架 §二、03-01 §四；dev 端口决策 D-13。

## 一、一键启动与冒烟

```bash
cp .env.example .env      # 填值（见§三）
docker compose -f compose-dev.yml up -d
docker compose -f compose-dev.yml ps   # 8/8 healthy 即就绪
```

冒烟口径（G1）：

```bash
curl http://localhost:8082/api/health        # 期望 {"status":"UP"}（经nginx→app→actuator）
curl http://localhost:8080/actuator/health   # 期望 {"status":"UP"}（直连）
```

两段式依赖：`minio-init`（建 bucket）与 `ollama-init`（拉 7B 模型）需成功退出（exit 0）后 `app` 才放行启动。
ollama 模型卷持久化，断点续拉，仅首次慢（约 4.7GB）。

## 二、dev 端口表（D-13：宿主端口冲突统一改道，生产 W11 恢复标准端口）

| 服务 | 容器端口 | dev 宿主端口 | 改道原因 |
|------|:---:|:---:|----------|
| nginx | 80 / 443 | **8082 / 9443** | 宿主 8081 被 nexus 私服占用（W10-C2 改映射，D-32 裁决不停 nexus） |
| mysql | 3306 | **13306** | 宿主原生 MySQL 占 3306/33060 |
| redis | 6379 | **6380** | 宿主原生 Redis 占 6379 |
| rabbitmq | 5672 / 15672 | 5672 / 15672（直连） | 无冲突；15672=管理台 |
| minio | 9000 / 9001 | 9000 / 9001（直连） | 无冲突；9001=控制台 |
| ollama | 11434 | 11434（直连） | 无冲突 |
| ocr | 8868 | 8868（直连） | 无冲突；W6前为占位HTTP |
| app | 8080 | 8080（直连调试） | 标准链路经 nginx |

## 三、.env 填写指引（.env 已 gitignore，红线4：真实凭据不入库）

| 变量 | 必填 | 说明 |
|------|:---:|------|
| `MYSQL_PASSWORD` | ✓ | 容器 MySQL root 密码；**卷初始化后修改须走 ALTER（见§四坑3）** |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | ✓ | MinIO 凭据；bucket 初始化由 minio-init 完成（swe-hot/swe-cold/backup） |
| `JWT_SECRET` | ✓ | HS512 JWT 签名密钥，**≥64 字节**；缺失或过短应用启动即失败。⚠️ 仅 dev 弱密钥可用，生产另行下发（S-14 密钥零明文） |
| `OLLAMA_MODEL` | 可选 | 默认 qwen2.5:7b |
| `DEEPSEEK_API_KEY` / `SMTP_*` | 预留 | W6+ 启用（邮箱通道 A-06/A-07、LLM 云备选） |

## 四、坑位记录（防复发冷知识，运维问答素材）

1. **容器健康探针禁用 `localhost`**：容器内 `localhost` 先解析 IPv6 `::1`，而服务常仅绑 IPv4，
   探针报 Connection refused → 永远 starting/unhealthy。一律写 `127.0.0.1`。
2. **`CMD-SHELL` 是 sh(dash)，不支持 `/dev/tcp`**：`/dev/tcp/host/port` 是 bash 内建特性。
   bash 探针必须显式 `test: ["CMD", "bash", "-c", "(echo > /dev/tcp/127.0.0.1/8080) ..."]`，
   用 CMD-SHELL 包裹会在 dash 下静默失败。
3. **MySQL 卷初始化后，`MYSQL_ROOT_PASSWORD` 环境变量不再生效**：该变量仅在 datadir 为空时参与首次初始化。
   卷已初始化后改密码必须 `ALTER USER`；删卷重建则以 `.env` 当前值为准重新初始化（数据会清空，慎用）。

## 五、维护注记

- app 存储：`FILESTORE_BASEDIR=/data/storage`（独立卷 `app_storage`）。**该卷仅临时文件**——
  按边界要求，业务持久化必须走 MinIO `swe-hot/swe-cold`（03-01 §四），勿把正式文件落在本地卷。
- init 容器（minio-init / ollama-init）无 restart 策略，成功后保持 Exited(0) 属正常态。
- 宿主 Docker 数据盘位置迁移（C→D）后若发现镜像/卷清空，优先检查
  `<数据目录>\DockerDesktopWSL\disk\docker_data.vhdx` 是否为旧数据盘（GUI 改路径不自动迁移数据）。
