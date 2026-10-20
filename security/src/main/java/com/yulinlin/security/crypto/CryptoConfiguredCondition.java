package com.yulinlin.security.crypto;

import com.yulinlin.security.SecurityCryptoProperties;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/** 仅在存在设备密钥时启用框架加解密；具体密钥、IV、unknown 配置仍由管理器校验。 */
public final class CryptoConfiguredCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, SecurityCryptoProperties.Device> devices = Binder.get(context.getEnvironment())
                .bind("yulinlin.security.crypto.devices",
                        Bindable.mapOf(String.class, SecurityCryptoProperties.Device.class))
                .orElse(Map.of());
        SecurityCryptoProperties properties = new SecurityCryptoProperties();
        properties.setDevices(devices);
        return properties.hasDeviceKeys()
                ? ConditionOutcome.match("已配置设备密钥，启用接口加解密")
                : ConditionOutcome.noMatch("未配置设备密钥，不接管接口加解密");
    }
}
