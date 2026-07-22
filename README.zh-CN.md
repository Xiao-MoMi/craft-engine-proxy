# CraftEngine Proxy

[English](README.md) | **简体中文**

[CraftEngine](https://github.com/Xiao-MoMi/craft-engine) 的配套插件，运行在你的 **Velocity** 或 **BungeeCord** 代理端上。

## 功能特性

### ✨ 资源包去重
如果没有这个插件，玩家每次切换后端服务器时都要重新下载资源包——即使资源包完全相同。本插件会记住每位玩家已经拥有的资源包，跳过重复下载，让服务器切换无缝衔接。

> [!IMPORTANT]
> 后端服务器需要 **CraftEngine 26.7.4+**。如果你启用了资源包混淆（obfuscation），请务必设置固定的**资源包种子（seed）**——否则每个服务器会生成不同的资源包，去重将无法生效。

### 💬 在代理端插件中使用 CraftEngine 标签
像 `<image:...>` 这样的 CraftEngine 标签只会在后端服务器上被解析。如果代理端插件（聊天、公告等）发送了这些标签，玩家看到的只是原始文本。本插件直接在代理端完成解析，让代理端插件也能使用与后端插件相同的标签。

## 环境要求

- **Velocity** 或 **BungeeCord / Waterfall** 代理端
- **Java 21+**
- 后端服务器已安装 CraftEngine
- 客户端版本 **1.20 → 26.2**

## 安装

1. 将对应你代理平台的 jar 文件放入代理端的 `plugins` 文件夹。
2. 重启代理端，无需任何配置。
