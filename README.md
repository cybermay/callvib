# 拨通震动 (CallVib)

拨出电话**对方真正接通**的那一刻，轻轻震动一下。来电不震。

Android 没有一个「接通」回调 —— `CALL_STATE_OFFHOOK` 在**拨号瞬间**就回调（对方还在响铃），
所以很多同类 App 会变成「拨号就震」。这个 App 用两条独立通路拿到真正的接通时刻：

| 通路 | 信号 | 需要的授权 |
|---|---|---|
| ① 主 | 拨号器通话通知上的**计时器**：`EXTRA_SHOW_CHRONOMETER == true` 且 `notification.when > 0` | 通知使用权 |
| ② 备 | 通话界面上的**通话时长**文字（`m:ss` / `h:mm:ss`，≥2 秒才算） | 无障碍 |

`notification.when` 就是 `Call.Details.getConnectTimeMillis()`：未接通时它是 `0`，
接通后才变成真实时刻；拨号器也只在接通后才对通话通知 `setUsesChronometer(true)`。
两条通路谁先命中就震，**同一次通话只震一次**。

## 状态

- 实测机型：Sony Xperia 1 II（XQ-AT52）/ Android 12 / Google 拨号器
- 其它 ROM / 拨号器未经实测：若主通路不成立，App 内置日志会打印每一条通话通知的
  `chrono` / `when` / 标题 / 正文，按真实取值调整 `CallNotifListener` 的判据即可。

## 安装与授权

1. 装 APK，打开 App
2. 点「① 授权电话状态」→ 授予电话状态权限
3. 点「② 开启通知使用权」→ 在系统设置里勾选「拨通震动」（**这一项是接通判定的关键**）
4. 可选：点「③ 开启无障碍」，开第二条通路（更稳，但不是必需）
5. 拨一通电话，回来点「导出日志到下载目录」可把现场日志存到 `Download/callvib-log.txt`

震动为单次 70ms、振幅 120 —— 拿在手里刚好能感觉到。要改力度，改
`VibService.java` 里的 `PATTERN` / `AMPS` 两行即可。

## 构建

需要 JDK 17+ 与 Android SDK（compileSdk 37）。仓库带 Gradle wrapper：

```bash
# 未签名（产物在 app/build/outputs/apk/release/app-release-unsigned.apk）
./gradlew assembleRelease

# 签名：keystore 用环境变量给，不要提交进仓库
export KEYSTORE=/path/to/your.jks KS_PASS=*** KS_ALIAS=*** KEY_PASS=***
./gradlew assembleRelease
```

也可以完全不用 Gradle，直接 `aapt2 + javac + d8 + apksigner`（约 30 行脚本，无第三方依赖）：

```bash
BT=$ANDROID_HOME/build-tools/37.0.0
JAR=$ANDROID_HOME/platforms/android-37.0/android.jar
$BT/aapt2 compile --dir app/src/main/res -o res.zip
$BT/aapt2 link -o base.apk -I "$JAR" --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 31 --target-sdk-version 31 res.zip
javac -source 17 -target 17 -classpath "$JAR" -d obj $(find app/src/main/java -name '*.java')
$BT/d8 --min-api 31 --lib "$JAR" --output dex $(find obj -name '*.class')
(cd dex && zip -q ../base.apk classes.dex)
$BT/zipalign -f 4 base.apk aligned.apk
$BT/apksigner sign --ks "$KEYSTORE" --out callvib.apk aligned.apk
```

## 权限说明

| 权限 | 用途 |
|---|---|
| `READ_PHONE_STATE` | 区分来电/去电、判断当前是否在通话中 |
| `VIBRATE` | 震动 |
| `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` | 常驻服务与状态通知 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |

App 不联网、不收集任何数据；日志只写在应用私有目录 + 手动导出时才写 `Download/`。

## License

MIT
