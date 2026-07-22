# CraftEngine Proxy

**English** | [简体中文](README.zh-CN.md)

A companion plugin for [CraftEngine](https://github.com/Xiao-MoMi/craft-engine), running on your **Velocity** or **BungeeCord** proxy.

## Features

### ✨ Resource Pack Deduplication
Without this plugin, players re-download the resource pack every time they switch backend servers — even if it's the same pack. This plugin remembers what each player already has and skips redundant downloads, making server switching seamless.

> [!IMPORTANT]
> Requires **CraftEngine 26.7.4+** on backend servers. If you enable resource pack obfuscation, make sure to set a fixed **resource pack seed** — otherwise each server generates a different pack and deduplication won't work.

### 💬 CraftEngine Tags in Proxy Plugins
CraftEngine tags like `<image:...>` only get translated on backend servers. If a proxy plugin (chat, announcements, etc.) sends them, players see raw text. This plugin translates them right on the proxy, so proxy plugins can use the same tags as backend plugins.

## Requirements

- **Velocity** or **BungeeCord / Waterfall** proxy
- **Java 21+**
- CraftEngine installed on backend servers
- Client versions **1.20 → 26.2**

## Installation

1. Drop the jar for your proxy platform into the proxy's `plugins` folder.
2. Restart the proxy. No configuration needed.