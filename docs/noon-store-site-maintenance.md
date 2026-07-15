# Noon 店铺 / 站点维护配置

生产环境可通过外部 JSON 文件声明正在维护的 `owner/store/site`，让自动调度在调用 Noon 前跳过对应范围。

## 启用

1. 复制 `docs/noon-store-site-maintenance.example.json` 到 Jar 目录之外的运维路径。
2. 设置环境变量：

```text
NUONO_NOON_MAINTENANCE_SCOPE_FILE=/usr/local/workspace/nuono-next-prod/config/noon-store-site-maintenance.json
```

环境变量为空时功能关闭，调度行为与当前版本一致。配置文件不保存 Cookie、Token 或密码。

## 配置键

- `ownerUserId`：必填，必须为正数。
- `storeCode`：必填，与 `noon_pull_plan.store_code` 一致，比较时忽略大小写和首尾空格。
- `siteCode`：必填；写具体站点，或用 `*` 表示该店铺的全部站点。
- `enabled`：可选；只有明确写 `false` 才忽略该条。
- `effectiveFrom` / `effectiveTo`：可选，`yyyy-MM-dd`，按 `Asia/Shanghai` 且包含边界日期。
- `reason`：可选，仅供人工维护；运行日志不会输出该内容。

## 加载与失败行为

- 应用在当天第一次判断维护范围时读取文件；同一上海业务日只读取一次，跨日自动重新读取。
- 建议先写临时文件并原子替换正式文件，避免读取到写了一半的 JSON。
- 新文件缺失、版本错误或 JSON 无法解析时，保留上一次成功加载的配置，并记录不含敏感信息的错误日志。
- 首次启动时若配置路径无效且没有 last-known-good，门禁为空，不会误停全部店铺。

## 生效范围

- Noon Pull `SCHEDULED_DAILY`：Sales、Order、Finance、Product、Ads、Inventory、FBN Received。
- DP05 Public Detail 自动任务。
- 已创建但尚未执行或仍需轮询的 `SCHEDULED_DAILY` 任务也会被跳过，维护结束后可继续执行或按原生命周期收口。

以下不受该文件影响：人工任务、manual backfill、gap repair/backfill、OTP/店铺重新授权，以及没有 `owner/store/site` 业务键的 DP08–DP10。
