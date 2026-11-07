# PatNest 直播推拉流（SRS 6）

商家直播的音视频由自建 [SRS](https://ossrs.net/) 承担：**机构推 RTMP，用户拉 HLS / HTTP-FLV**。
后端只负责直播间、地址下发、在线人数、弹幕与封禁，见 `docs/18-商家直播功能.md`。

```
机构 App/桌面端 (摄像头+麦克风) --RTMP:9201--> SRS --HLS:9202--> 小程序 / App / 桌面 / 后台
                                                --HTTP-FLV:9202--> 低延迟观看
```

## 一、端口

| 端口 | 协议 | 用途 |
|---|---|---|
| 9201 | TCP | RTMP 推流 / RTMP 播放 |
| 9202 | TCP | HLS（m3u8/ts）与 HTTP-FLV 播放 |
| 1985 | TCP | SRS 自带 HTTP API / 控制台（仅容器内，部署包不对外映射） |

> 云服务器要在安全组放行 **9201、9202**；`9202` 若被占用，改 `srs.conf` 的 `http_server.listen`
> 并同步 `LIVE_HLS_BASE` / `LIVE_FLV_BASE`。
> 注意：`9201` 在宿主机上是 RTMP，但 API 容器**内部**的 `APP_PORT` 也是 9201（不对外发布），
> 两者在不同网络命名空间里，不冲突；以后不要再把 API 的 9201 映射到宿主机。

**延迟**：HLS 已用 1s 切片（`hls_fragment 1`，约 2-3s），HTTP-FLV 更快（约 1-2s，App/桌面优先）；
再低需要上 WebRTC。调优细节见 `docs/18-商家直播功能.md` §十一。

## 二、启动

**方式 A：随主部署包一起启动（推荐）**

`deploy/docker/compose.yaml` 里已经内置 `srs` 服务（`app` profile），
`install.sh` / `install.ps1` 的 `up -d` 列表里也带上了它，所以**首次部署会自动启动**：

```bash
docker ps | grep patnest-srs      # 看到 Up 即可
```

**方式 B：单独起**

```bash
cd deploy/live
docker compose up -d
docker compose logs -f srs     # 看到 "Server started" 即可
```

> ⚠️ 已经部署过的旧包：后台「上传升级包」热更新只替换二进制/前端/迁移，**不会**新增容器。
> 需要手动补一次：把 `live/srs.conf` 放进部署目录，覆盖 `compose.yaml` 与安装脚本，
> 然后执行 `docker compose --profile app up -d srs`（`podman-compose` 同理）。

## 三、后端要配的环境变量

```dotenv
# server/patnestServer/.env，或部署包的 deploy/docker/.env
# 推流地址前缀（客户端软件内开播用）
LIVE_RTMP_BASE=rtmp://192.168.3.100:9201/live
# HLS / HTTP-FLV 播放前缀
LIVE_HLS_BASE=http://192.168.3.100:9202/live
LIVE_FLV_BASE=http://192.168.3.100:9202/live
```

不配时默认 `127.0.0.1:9201/9202`（本机联调）。改完重启后端生效；`GET /api/v1/live` 返回的
`play_hls` 应形如 `http://192.168.3.100:9202/live/<stream_key>.m3u8`。

## 四、机构开播

开播由 **App / 桌面端软件内完成**（Android 用 RootEncoder，桌面用 JavaCV/FFmpeg 采集摄像头），
不需要 OBS：

1. 机构端「开播」→ `POST /api/v1/sitter/live` 返回 `push_url` 与 `stream_key`
2. 客户端进入开播页后自动采集摄像头/麦克风并推到 `push_url`
3. 结束直播时离开页面（自动停推）+ `PUT /sitter/live/:id/stop`

**备选**：临时用 OBS 推流时，把 `push_url` 拆成服务器 `rtmp://<host>:9201/live` 与串流密钥
`<stream_key>` 填入即可。推流密钥等同于推流凭证，不要外泄。

## 五、验证

```bash
# 1. 推流是否在
curl -s http://127.0.0.1:1985/api/v1/streams | head -c 300

# 2. HLS 是否可拉
curl -I http://127.0.0.1:9202/live/<stream_key>.m3u8
```

## 六、常见问题

| 现象 | 原因 |
|---|---|
| 播放器 404 / m3u8 不存在 | 还没推流，或 `LIVE_HLS_BASE` 指错了主机/端口 |
| 推流成功但页面黑屏 | 浏览器跨域：HLS 需要后端/nginx 带 `Access-Control-Allow-Origin`（本配置的 `http_server` 已开 `crossdomain`） |
| 手机端只能看到封面 | 小程序 `<video>` 对 m3u8 支持有限，建议用 App（media3）或小程序基础库较新的版本 |
| 推流被拒绝 | `stream_key` 输错 / 直播间已被后台下播或封禁 |
| 9201 连不上但 9200 正常 | 安全组只放行了 9200；9201/9202 要单独放行 |
