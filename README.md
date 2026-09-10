# 📡 无线信号探测器 (Radio Info App)

## 功能概述

基于 ARM64-v8A 架构的 Android 应用，**无需 root 权限**即可获取无线通信信息。

### 10个独立选项卡:

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

#### 6. 📈 流量监控
- 本机实时上下行速度
- 累计流量与历史曲线

#### 7. 📻 射频曲线
- 服务小区/邻区信号强度曲线
- LTE 频段与 NR-ARFCN 频率

#### 8. 🌐 局域网
- 本机 IP、默认网关
- 受 Android 权限限制的 ARP 邻居表

#### 9. 🔵 蓝牙
- 经典蓝牙与 BLE 附近设备扫描
- 广播名称、RSSI、协议类型和粗略距离估算
- 选择指定设备进行 RSSI 趋势定位，必要时使用加速度传感器提示移动方向
- 定位结果为近似判断，不提供 GPS 级精确位置或绝对方位
- 本机 BLE 服务广播
- 通过系统蓝牙分享器发送文件

#### 10. NFC
- 通过 NFC 推送或写入标签网页地址
- 通过 Android Application Record 指定并打开已安装应用
- 通过 NFC 写入文件 URI 到 NFC 标签；Android 9 兼容系统可尝试旧版 NFC 点对点推送
- 接收 NFC 信息时校验网页协议、应用包名和文件 URI，避免执行不安全内容

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
| NEARBY_WIFI_DEVICES | Android 13+ WiFi扫描 |
| BLUETOOTH_SCAN | 扫描附近蓝牙广播 |
| BLUETOOTH_CONNECT | 读取已发现设备信息 |
| BLUETOOTH_ADVERTISE | 广播 Radio Info App 服务标识 |

NFC 不需要额外运行时权限；设备需要具备 NFC 硬件并开启 NFC。Android 10+ 已移除系统 NFC 点对点推送，文件传输需使用可写 NFC 标签或系统分享功能。

应用不会联网上传数据，所有信息仅在设备本地显示；备份规则也不会备份应用数据。

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
输出: `app/build/outputs/apk/release/app-release-unsigned.apk`（仅用于后续签名，不能直接安装）。

发布或本地安装前，请使用 Android Studio 的 **Generate Signed App Bundle / APK**，或使用自己的受保护签名密钥完成 `zipalign` 和 `apksigner sign`。不要把密钥、密码或 unsigned APK 发布到 Release。

## 安装
```bash
adb install <signed-release.apk>
```
