# 接口加密与前端对接

[文档首页](../README.md) / [接口安全](README.md)

完整配置、多设备、CBC/GCM、明密文兼容和安全边界。

## 本页导航

- [是否接管接口加解密](#是否接管接口加解密)
- [完整主配置](#完整主配置)
- [配置项与默认值](#配置项与默认值)
- [接口注解](#接口注解)
- [响应加密规则](#响应加密规则)
- [默认 CBC 前端对接](#默认-cbc-前端对接)
- [多设备选择](#多设备选择)
- [明文排除路径](#明文排除路径)
- [可选 AES-GCM](#可选-aes-gcm)
- [手动使用](#手动使用)
- [安全边界与限制](#安全边界与限制)

## 是否接管接口加解密

**不配置任何设备密钥，框架就不做接口加密解密。** 可以直接省略整个 `yulinlin.security.crypto` 配置，无需额外的关闭开关；JWT 签名密钥不属于这里的设备密钥。

- `devices` 未配置、为空，或所有设备的 `key` 都为 null / 空白：不注册设备密钥管理器、设备解析器、加解密路径规则及请求/响应 Advice。
- 没有密钥时，即使标注 `@ApiCrypto`，框架也不会读取、解密或改写正文，不附加加密响应头；请求和响应交给正常 MVC 流程，业务可自行实现加解密。
- 不接管不等于“任意密文都能直接进入 Controller”。业务未提供解密逻辑时，JSON 转换器仍可能拒绝密文。正常 HTTP 大小限制仍由 Web 容器和业务配置负责。
- 配置了任意一个非空设备密钥，就启用框架加解密；此时 `devices.unknown`、所有已声明设备的 key、CBC IV 和算法配置必须合法，错误配置启动时报错。
- 配置了密钥时，`enabled=false` 仍为兼容模式：请求可明文或密文，响应明文；它不是完全关闭。
- 登录、权限、限流不受设备密钥有无影响。业务手工创建的加密组件由业务自己管理；这里控制的是框架自动配置。配置在启动时判断，修改后需重启。

密钥全部缺失会按“不接管”处理，部署时应核对环境变量和配置来源；需要强制加密的项目应确保密钥确实存在，不能只依赖 `@ApiCrypto` 注解。

## 完整主配置

正式项目推荐使用多设备配置。下面完整展示设备请求头、未知设备兜底、明文路径、请求大小以及 Web、Android、iOS 独立密钥：

```yaml
yulinlin:
  security:
    crypto:
      # true：严格加密；false：兼容明文/密文请求，响应统一明文
      enabled: true

      # 客户端通过该请求头选择设备密钥
      device-header: Device-Type

      # 请求和响应都跳过加解密
      # 自定义该列表会替换默认值，因此需要保留 /monitor/**
      exclude-paths:
        - /monitor/**
        - /actuator/health
        - /public/**
        - /upload/**
        - /download/**

      # 同时限制收到的密文和解密后的 JSON
      max-request-size: 1MB

      devices:
        web:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_WEB_KEY}
          iv: ${YULINLIN_SECURITY_WEB_IV}

        android:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_ANDROID_KEY}
          iv: ${YULINLIN_SECURITY_ANDROID_IV}

        ios:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_IOS_KEY}
          iv: ${YULINLIN_SECURITY_IOS_IV}

        # 固定保留名：请求头缺失、为空或未匹配任何设备时统一使用
        unknown:
          algorithm: AES/CBC/PKCS5Padding
          key: ${YULINLIN_SECURITY_UNKNOWN_KEY}
          iv: ${YULINLIN_SECURITY_UNKNOWN_IV}

    limit:
      enabled: true
      # 本机最多保留多少个“接口 + IP/设备 ID”计数器
      maximum-size: 50000
      ip-header: X-Real-IP
      device-id-header: Device-Id
      # 只有 TCP 连接来源属于这里时，框架才信任 ip-header
      trusted-proxies:
        - 127.0.0.1
        - "::1"
```

环境变量示例：

```properties
YULINLIN_SECURITY_WEB_KEY=MIGfMA0GCSqGSIb3
YULINLIN_SECURITY_WEB_IV=5efd3f6060e20765

YULINLIN_SECURITY_ANDROID_KEY=1234567890abcdef
YULINLIN_SECURITY_ANDROID_IV=abcdef1234567890

YULINLIN_SECURITY_IOS_KEY=fedcba0987654321
YULINLIN_SECURITY_IOS_IV=0123456789abcdef

YULINLIN_SECURITY_UNKNOWN_KEY=abcdef1234567890
YULINLIN_SECURITY_UNKNOWN_IV=1234567890abcdef
```

示例值仅用于说明长度。生产密钥必须由环境变量或密钥管理系统提供，不能提交到仓库。

## 配置项与默认值

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 配置设备密钥后控制严格加密；false 时注解接口接受明文或密文，响应统一明文；无密钥时不接管 |
| `device-header` | `Device-Type` | 客户端设备类型请求头；同时回写到加密响应 |
| `exclude-paths` | `/monitor/**` | 同时跳过请求解密和响应加密的 Spring 路径模式 |
| `max-request-size` | `1MB` | 密文与解密后正文的大小上限 |
| `devices.<name>.algorithm` | `AES/CBC/PKCS5Padding` | 当前设备的 AES 算法 |
| `devices.<name>.key` | 无 | 必填；CBC 接受 16、24 或 32 字节 |
| `devices.<name>.iv` | 无 | CBC 必填且必须是 16 字节 |
| `devices.<name>.key-encoding` | `UTF8` | 默认字符密钥不需要填写；GCM 示例使用 `BASE64` |
| `devices.<name>.iv-encoding` | `UTF8` | 默认字符 IV 不需要填写 |

启用框架加解密后，配置中的设备名会去除首尾空白并转换为小写，只允许小写字母、数字、下划线和短横线，最大长度为 32。HTTP 请求未携带设备头、设备头为空、格式异常或设备名未配置时，统一使用保留设备 `devices.unknown`。配置任意设备密钥后，`devices.unknown` 也是必填项；未配置任何密钥则完全不接管。框架不支持外层 `algorithm`、`key`、`iv`，也不需要 `default-device` 或额外的 fallback 属性。框架不自动暴露单设备 `AesCrypto` Bean；有设备密钥时手动操作统一使用 `DeviceCryptoManager`，无密钥时该管理器也不会自动注册。

CBC 参数必须满足：

```text
algorithm = AES/CBC/PKCS5Padding
key       = 16、24 或 32 字节
iv        = 16 字节
encoding  = UTF-8
cipher    = 标准 Base64
padding   = PKCS5/PKCS7
```

建议 key 和 IV 使用 ASCII 字符，避免中文 UTF-8 字符占多个字节。Java 的 `PKCS5Padding` 与常见 JavaScript AES 实现的 `Pkcs7` 在 AES 分组下兼容。

## 接口注解

需要框架加密的业务接口先配置设备密钥，再使用 `@ApiCrypto`。它可以放在 Controller 类或具体方法上，方法配置优先于类配置。无密钥时不接管；有密钥时全局 `enabled` 控制严格加密模式：

| 全局配置 | 接口注解 | 请求 | 响应 |
| --- | --- | --- | --- |
| 没有任何设备密钥 | 任意注解 | 框架不处理 | 框架不处理 |
| 有密钥，`enabled=true` | `@ApiCrypto` / `@ApiCrypto(true)` | 必须传整个 JSON 加密后的 Base64 密文 | 加密 |
| 有密钥，`enabled=true` | `@ApiCrypto(false)` | 普通 JSON 原样读取；非 JSON body 仍尝试解密 | 明文 |
| 有密钥，`enabled=false` | `@ApiCrypto(true/false)` | 普通 JSON 或整个 body 密文都支持 | 明文 |
| 任意值 | 没有注解 | security 完全不处理 | 明文 |
| 任意值 | 命中 `exclude-paths` | security 完全不处理 | 明文 |

有设备密钥时，`enabled=false` 是兼容模式，设备密钥管理器和请求 Advice 仍会注册，以便继续解密尚未升级的旧客户端；此时 `@ApiCrypto(true)` 不再拒绝明文请求，响应也不会加密。已经发送密文的客户端和已经切换明文的客户端可以并行迁移。完全交给业务处理时，移除所有设备密钥即可，不保留随机占位密钥。

业务 JSON 接口推荐统一使用 `@PostMapping + @RequestBody`。请求 Advice 只处理 body，不加密 query 参数和 path 变量。

```java
package demo.api;

import com.yulinlin.security.annotation.ApiCrypto;
import com.yulinlin.starter.domain.R;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @ApiCrypto
    @PostMapping("/api/user/detail")
    public R<UserVO> detail(@RequestBody UserDetailPo request) {
        return R.ok(userService.detail(request.getId()));
    }

    @ApiCrypto(false)
    @PostMapping("/api/user/public-detail")
    public R<UserVO> publicDetail(@RequestBody UserDetailPo request) {
        return R.ok(userService.detail(request.getId()));
    }
}
```

`@ApiCrypto(false)` 是兼容模式，不是完全关闭请求处理。框架把 body 转成 UTF-8 字符串：忽略首尾空白判断后，以 `{` 或 `[` 开头时直接交给 Jackson；其他内容按当前设备的 Base64 密文解密。它不会为了判断明文而预解析 JSON，因此明文请求只反序列化一次。错误 Base64 或解密失败返回 400，不会再次降级为明文。

### 响应加密规则

- 普通 Java 对象：整个对象序列化后加密，响应 JSON 是一个 Base64 字符串。
- `R<E>`：只加密非 null 的 `data`，保留 `code`、`msg`、`ok` 和 `timestamp`，并设置 `crypt=true`。
- 返回值或 `R.data` 是 `String` 时，直接加密该字符串的 UTF-8 字节，不先增加 JSON 双引号；其他类型仍先序列化为 JSON。
- `R.data == null`：不生成无意义密文，`crypt` 保持 `false`。
- `ResponseVo<E>`：仅保留废弃兼容，行为与 `R<E>` 相同。

因此客户端必须按接口约定选择解密方法：字符串使用 `decryptText()`，对象、集合、数字和布尔值使用 `decryptJson()`。密文自身不能可靠判断原始 Java 类型。

`R<E>` 加密响应示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "ok": true,
  "crypt": true,
  "data": "mFbzvANmZJZQj/3nB4zKpA==",
  "timestamp": 1792070400000
}
```

## 默认 CBC 前端对接

请求仍使用 `Content-Type: application/json`。推荐 body 直接发送未经 JSON 双引号包裹的标准 Base64 密文；为兼容浏览器请求链路，服务端也接受 Axios 添加一层 JSON 双引号、`+` 被表单处理转换为空格，以及密文中混入回车、换行或制表符的情况：

```http
POST /api/user/detail HTTP/1.1
Content-Type: application/json
Device-Type: web

N4H0Qf4H0m4YqQPG4Q3XWg==
```

以下 Axios 实际报文也能兼容，服务端会先移除最外层双引号再进行 Base64 解码：

```text
"N4H0Qf4H0m4YqQPG4Q3XWg=="
```

服务端加密响应会返回实际设备和算法：

```http
Device-Type: web
X-Yulinlin-Crypto: AES/CBC/PKCS5Padding
```

下面使用浏览器原生 Web Crypto API，与默认 CBC、UTF-8 key/IV 和标准 Base64 对齐。Web Crypto 的 AES-CBC 使用 PKCS7 填充，与服务端 `PKCS5Padding` 兼容：

```javascript
const encoder = new TextEncoder();
const decoder = new TextDecoder();

function bytesToBase64(bytes) {
  let binary = "";
  for (const value of new Uint8Array(bytes)) binary += String.fromCharCode(value);
  return btoa(binary);
}

function base64ToBytes(value) {
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}

async function importCbcKey(keyText) {
  const bytes = encoder.encode(keyText);
  if (![16, 24, 32].includes(bytes.length)) throw new Error("AES key length is invalid");
  return crypto.subtle.importKey("raw", bytes, {name: "AES-CBC"}, false,
      ["encrypt", "decrypt"]);
}

function cbcIv(ivText) {
  const bytes = encoder.encode(ivText);
  if (bytes.length !== 16) throw new Error("AES-CBC IV must contain 16 bytes");
  return bytes;
}

async function encryptJson(value, keyText, ivText) {
  const key = await importCbcKey(keyText);
  const encrypted = await crypto.subtle.encrypt(
      {name: "AES-CBC", iv: cbcIv(ivText)},
      key,
      encoder.encode(JSON.stringify(value)));
  return bytesToBase64(encrypted);
}

async function decryptText(ciphertext, keyText, ivText) {
  const key = await importCbcKey(keyText);
  const plaintext = await crypto.subtle.decrypt(
      {name: "AES-CBC", iv: cbcIv(ivText)},
      key,
      base64ToBytes(ciphertext.trim()));
  return decoder.decode(plaintext);
}

async function decryptJson(ciphertext, keyText, ivText) {
  return JSON.parse(await decryptText(ciphertext, keyText, ivText));
}

const deviceType = "web";
const key = "MIGfMA0GCSqGSIb3";
const iv = "5efd3f6060e20765";
const body = await encryptJson({id: "U1001"}, key, iv);

const response = await fetch("/api/user/detail", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Device-Type": deviceType
  },
  body
});

const responseBody = await response.json();
const result = responseBody?.crypt === true
    ? {...responseBody, data: await decryptJson(responseBody.data, key, iv)}
    : typeof responseBody === "string"
        ? await decryptText(responseBody, key, iv)
        : responseBody;
```

上例接口返回的是 `R<UserVO>`，所以 `data` 使用 `decryptJson()`；如果接口返回 `R<String>`，对应代码应改为：

```javascript
const result = responseBody?.crypt === true
    ? {...responseBody, data: await decryptText(responseBody.data, key, iv)}
    : responseBody;
```

跨域调用时，业务 CORS 配置必须允许请求头 `Device-Type`，并暴露响应头 `Device-Type` 和 `X-Yulinlin-Crypto`。密钥不得直接硬编码在公开源码中；上述浏览器代码只用于说明协议对接。

## 多设备选择

请求头决定当前使用哪组设备密钥：

```http
Device-Type: web
Device-Type: android
Device-Type: ios
```

| 请求头 | 最终设备 |
| --- | --- |
| 缺失或空值 | `unknown` |
| `web`、`android` 等已配置名称 | 对应设备 |
| 未配置的名称 | `unknown` |
| 不符合设备名格式的值 | `unknown` |

设备名匹配不区分大小写并忽略首尾空白。响应回写实际解析后的设备类型，客户端应优先读取响应 `Device-Type`，再选择对应密钥解密。设备请求头只负责选密钥，不是身份认证信息，不能仅凭该值判断调用方可信。

## 明文排除路径

`exclude-paths` 是服务端控制的完整排除列表，支持 Spring 路径模式。命中后，即使接口存在 `@ApiCrypto(true)`，请求体也不解密，响应体也不加密：

```yaml
yulinlin:
  security:
    crypto:
      exclude-paths:
        - /monitor/**
        - /actuator/health
        - /public/**
```

默认列表只有 `/monitor/**`。配置新列表会替换默认列表，因此使用监控模块时必须继续保留它。不要提供由客户端控制的“跳过加密”请求头，否则业务接口可能被绕过。

文件上传、文件下载、视频流、SSE、WebSocket、静态资源和健康检查不适合 JSON 加密 Advice，应保持无 `@ApiCrypto`，必要时加入排除路径。

## 可选 AES-GCM

支付、订单等需要密文完整性校验的设备可以显式切换到 GCM：

```yaml
yulinlin:
  security:
    crypto:
      devices:
        web:
          key: ${YULINLIN_SECURITY_WEB_KEY}
          iv: ${YULINLIN_SECURITY_WEB_IV}
        ios:
          algorithm: AES/GCM/NoPadding
          key-encoding: BASE64
          key: ${YULINLIN_SECURITY_IOS_KEY}
        unknown:
          key: ${YULINLIN_SECURITY_UNKNOWN_KEY}
          iv: ${YULINLIN_SECURITY_UNKNOWN_IV}
```

GCM 要求 Base64 解码后恰好为 32 字节的密钥，不允许配置固定 `iv`。框架每次生成 12 字节随机 IV，Base64 解码后的报文结构为：

```text
12字节随机IV + 密文 + 16字节认证标签
```

GCM 使用以下 UTF-8 内容作为 AAD，客户端必须完全一致：

```text
1\n<规范化设备类型>\nA256GCM
```

例如 web 设备的 AAD 是 `1\nweb\nA256GCM`。密文、设备名、IV、认证标签或密钥不匹配都会导致解密失败；这是 GCM 相比默认固定 IV CBC 的主要安全优势。

## 手动使用

需要显式选择设备时，注入线程安全的 `DeviceCryptoManager`：

```java
import com.yulinlin.security.crypto.DeviceCryptoManager;

public class SecureValueService {
    private final DeviceCryptoManager cryptoManager;

    public SecureValueService(DeviceCryptoManager cryptoManager) {
        this.cryptoManager = cryptoManager;
    }

    public String encrypt(String deviceType, String value) {
        return cryptoManager.getRequired(deviceType).encryptBase64(value);
    }

    public String decrypt(String deviceType, String ciphertext) {
        return cryptoManager.getRequired(deviceType).decryptBase64ToString(ciphertext);
    }
}
```

框架不会自动注入单设备 `AesCrypto`，避免多设备项目误用固定密钥。脱离 Spring、明确只处理一种密钥时，仍可以直接创建底层实现：

```java
AesCrypto cbc = new AesCbcCrypto(keyBytes, ivBytes);
AesCrypto gcm = new AesGcmCrypto(keyBytes, "web");
```

实现每次操作创建独立 `Cipher`，可以被多个请求线程共享。

## 安全边界与限制

- 当前只支持 Spring MVC，不支持 WebFlux。
- 生产环境仍必须使用 HTTPS；接口加密不能替代 TLS。
- CBC 使用固定 IV，不能可靠发现密文篡改；高敏感业务选择 GCM。
- 浏览器必须持有共享 AES 密钥，因此网页脚本、浏览器扩展和自动化浏览器最终都能读取它。该能力只能提高普通采集脚本的成本，不能单独承担防爬虫职责。
- AES 不是密码哈希，用户密码必须使用专用密码哈希算法保存。
- 自动 Advice 只适合 JSON 请求和 JSON/字符串响应，不适合 multipart、文件流、视频流、SSE 或 WebSocket。
- 日志不得记录密钥、IV、解密后的敏感正文或完整密文。
- 当前没有实现时间戳、nonce 防重放和密钥轮换，业务不能假设已经具备这些能力。
- 当前限流状态只存于单个 Java 进程；多实例部署时每个实例独立计数，不提供分布式强一致限流。
- 设备 ID 可以伪造，IP 也可能由 NAT 共享；限流用于控制接口频率，不能单独判断用户一定是爬虫。

本专题于 2026-10-20 按纯无状态 JWT 和有状态 Sa-Token 的当前实现更新。旧 JWT 登录存储示例已移除；本轮未运行测试、编译或打包。接入时应使用真实客户端验证凭证失效边界、重启恢复、自定义实现及加解密约定。

---

下一步：返回[接口安全主题目录](README.md)按需选择相关专题。
