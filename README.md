# TechCraftLauncher

<p align="center">
  <img src="https://github.com/InfiniteTechnicallyTeam.png" width="120">
</p>

<h3 align="center">
  一个面向 Minecraft 玩家与服务器管理者的现代化启动器
</h3>

<p align="center">
  <b>Developed by InfiniteTechnicallyTeam</b>
</p>

---

## 📖 项目介绍

**TechCraftLauncher (TCL)** 是由 **InfiniteTechnicallyTeam** 开发的 Minecraft 启动器项目。

目标是打造一个：

- 简洁
- 高性能
- 自动化
- 面向 Mod 玩家与服务器开发者

的 Minecraft 管理工具。

不仅提供游戏启动能力，还计划支持：

- ModPack 自动安装
- Mod 自动管理
- Minecraft 版本管理
- Java 环境管理
- 服务端生成
- 自动更新系统

让玩家可以更方便地管理自己的 Minecraft 环境。

---

# ✨ 特性

## 🎮 Minecraft 启动

支持 Minecraft 游戏实例管理：

- 多版本管理
- 自动生成启动参数
- Java 自动选择
- JVM 参数优化
- 游戏日志管理


---

## 📦 ModPack 支持

计划支持：

- Modrinth `.mrpack`
- 自动下载 Mod
- 自动安装依赖
- 配置文件同步


示例：

```
Modrinth Pack

        |
        v

TechCraftLauncher

        |
        v

完整 Minecraft 实例
```

---

## ☕ Java 管理

自动检测并管理 Java 环境：

支持：

| Minecraft版本 | Java |
|-|-|
| 1.12及以下 | Java 8 |
| 1.16 - 1.20.1 | Java 17 |
| 1.20.5+ | Java 21+ |

未来支持自动下载运行环境。

---

## 🖥️ 服务端工具链

TechCraftLauncher 计划提供：

- 客户端整合包转换服务端
- Forge Server 自动安装
- Fabric Server 自动安装
- 服务端文件生成
- 服务器配置管理


目标：

```
客户端 ModPack

        ↓

Server Builder

        ↓

Minecraft Server
```

---

# 🏗️ 项目结构

当前项目：

```
TechCraftLauncher

├── src
│   └── main
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle
```

未来规划：

```
TechCraftLauncher

├── launcher-core
│
├── launcher-ui
│
├── minecraft
│
├── auth
│
├── downloader
│
├── updater
│
└── server-builder
```

---

# 🔧 技术栈

## 开发语言

- Java

## 构建工具

- Gradle

## 支持平台

计划支持：

- Windows
- Linux
- macOS


---

# 🚀 开发计划

## Phase 1

- [x] 基础启动器框架
- [ ] Minecraft 启动核心
- [ ] 游戏实例管理


## Phase 2

- [ ] Modrinth Pack 支持
- [ ] 自动下载系统
- [ ] 自动更新


## Phase 3

- [ ] Forge/Fabric 服务端生成
- [ ] 服务器管理工具
- [ ] 云端同步


---

# 🤝 参与开发

欢迎提交：

- Issue
- Pull Request
- 功能建议


如果你喜欢这个项目，可以 Star 支持我们。

---

# 📜 License

本项目遵循项目许可证协议。

---

# 👥 关于团队

**InfiniteTechnicallyTeam**

专注于：

- Minecraft 工具开发
- Java 应用开发
- 游戏生态工具

官方网站：

> Coming Soon

GitHub：

https://github.com/InfiniteTechnicallyTeam