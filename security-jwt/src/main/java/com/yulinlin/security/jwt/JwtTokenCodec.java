package com.yulinlin.security.jwt;

import com.yulinlin.security.auth.IUserDetails;

/**
 * 可替换的 Token 生成与验证协议；声明此类型的 Bean 即替换默认 JJWT 实现。
 * 两个方法必须成对工作且线程安全；默认链路无状态，不负责用户加载或服务端登录记录。
 */
public interface JwtTokenCodec {
    /**
     * 为已通过业务验证的用户生成 Token，解析后必须还原同一个 userId 和 deviceType。
     * 生成器负责每次登录唯一的 tokenId、签发时间和过期时间，不得复用旧登录的 tokenId。
     * 可自定义声明名称、签名算法和密钥；接收用户对象不等于序列化整个对象，默认仅取用户 ID。
     * 不得修改传入用户对象，不得将密码等敏感资料写入 Token。
     * 框架不保存登录记录，也不通过服务端存储检测 tokenId 是否重复。
     */
    String generate(IUserDetails user, String deviceType);

    /**
     * 验证签名及所需签发方等约束后解析声明；不允许只做 BASE64 解码就返回用户身份。
     * 无效、过期、被篡改的 Token 返回 null，或抛出 LoginException。
     * 密钥服务等基础设施故障应抛出其他异常，不要当作正常匿名请求吞掉。
     * 旧 Token 缺少设备声明时可返回 deviceType=null；不得把已存在的错误设备声明改成 null 绕过校验。
     * 返回后管理器仍会检查时间并按 userId 加载用户，不额外检查登录记录或注销状态。
     */
    TokenInfo parse(String token);
}
