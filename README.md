# 📡 无线信号探测器 (Radio Info App)

## 功能概述

基于 ARM64-v8A 架构的 Android 应用，**无需 root 权限**即可获取无线通信信息。

### 5个独立选项卡:

#### 1. 📱 SIM 状态
- SIM卡状态 (就绪/缺失/锁定)
- 运营商名称、代码、国家代码
- ICCID 序列号
- 电话号码
- 多SIM卡订阅信息
- eSIM/物理SIM 类型识别

#### 2. 📡 RAT 优先级
- 当前数据/语音网络类型 (2G/3G/4G/5G)
- 服务状态 (注册/漫游)
- PS域/CS域 注册详情
- 网络接入技术 (GSM/UMTS/LTE/NR)
- 检测到的基站 RAT 类型
- 首选网络模式

#### 3. 📶 WiFi 信道
- WiFi 开关状态
- 当前连接AP信息 (SSID/频段/信道/速率/RSSI)
- WiFi 标准 (802.11n/ac/ax/be)
- 扫描结果表格 (信道/SSID/频率/信号/安全)
- 信道占用统计 (2.4G/5G)

#### 4. 📻 频段状态
- 服务小区详细信息 (LTE/NR/GSM/WCDMA/CDMA)
- EARFCN/NRARFCN 频点信息
- 自动计算频段号 (Band 1-41 for LTE, Band n1-n261 for NR)
- 带宽信息
- 邻区频段信息
- 频段汇总统计

#### 5. 📊 信号强度
- RSRP/RSRQ/RSSNR (4G LTE)
- SS-RSRP/SS-RSRQ/SS-SINR (5G NR)
- CSI-RSRP/CSI-RSRQ/CSI-SINR (5G NR)
- dBm/ASU/Level 信号等级
- 信号质量评级 (⭐⭐⭐⭐⭐)
- 信号强度可视化条
- 服务小区/邻区排序

## 技术规格

- **架构**: ARM64-v8A (仅)
- **最低 Android**: 9.0 (API 28)
- **目标 Android**: 15 (API 35)
- **语言**: Kotlin
- **UI**: Material Design 3 + ViewPager2 + TabLayout
- **构建**: Gradle 8.5 + AGP 8.2.2
- **自刷新**: 每 2-5 秒自动更新

## 权限要求 (无需root)

| 权限 | 用途 |
|------|------|
| READ_PHONE_STATE | SIM卡状态、运营商信息 |
| ACCESS_FINE_LOCATION | 基站信息、WiFi扫描 |
| ACCESS_COARSE_LOCATION | 基站信息 |
| ACCESS_WIFI_STATE | WiFi状态查询 |
| CHANGE_WIFI_STATE | 触发WiFi扫描 |
| NEARBY_WIFI_DEVICES | Android 13+ WiFi扫描 |

## 构建方法

### 方式一: Android Studio
1. 用 Android Studio 打开 `RadioInfoApp` 文件夹
2. 等待 Gradle 同步完成
3. Build → Build Bundle(s) / APK(s) → Build APK(s)

### 方式二: 命令行
```bash
cd RadioInfoApp
./gradlew assembleRelease
```
输出: `app/build/outputs/apk/release/app-release-unsigned.apk`

## 安装
```bash
adb install app-release-unsigned.apk
```
