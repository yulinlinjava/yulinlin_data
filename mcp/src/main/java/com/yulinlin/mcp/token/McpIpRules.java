package com.yulinlin.mcp.token;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.*;

/** IP 白名单：单个地址或 CIDR，支持 IPv4 / IPv6。只接受字面量，不做 DNS 解析。 */
public final class McpIpRules {
    private McpIpRules() { }

    /** 校验并规范化；null 视为空列表（不限制）。 */
    public static List<String> normalize(Collection<String> rules) {
        if (rules == null) return List.of();
        if (rules.size() > 32) throw new IllegalArgumentException("IP 白名单最多 32 项");
        var result = new LinkedHashSet<String>();
        for (String raw : rules) {
            if (raw == null || raw.isBlank()) throw new IllegalArgumentException("IP 白名单项不能为空");
            var rule = parse(raw.strip());
            result.add(rule.text());
        }
        return List.copyOf(result);
    }

    public static boolean allows(List<String> rules, String remoteAddress) {
        if (rules == null || rules.isEmpty()) return true;
        InetAddress address = literal(remoteAddress);
        if (address == null) return false;
        for (String text : rules) if (parse(text).matches(address)) return true;
        return false;
    }

    private record Rule(String text, byte[] network, int bits) {
        boolean matches(InetAddress address) {
            byte[] value = address.getAddress();
            if (value.length != network.length) return false;
            int total = network.length * 8, shift = total - bits;
            var mask = bits == 0 ? BigInteger.ZERO : BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE).shiftLeft(shift);
            return new BigInteger(1, value).and(mask).equals(new BigInteger(1, network).and(mask));
        }
    }

    private static Rule parse(String text) {
        String host = text;
        Integer bits = null;
        int slash = text.indexOf('/');
        if (slash >= 0) {
            host = text.substring(0, slash);
            try { bits = Integer.parseInt(text.substring(slash + 1)); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("无效的 CIDR: " + text); }
        }
        InetAddress address = literal(host);
        if (address == null) throw new IllegalArgumentException("无效的 IP: " + text);
        int max = address.getAddress().length * 8;
        int prefix = bits == null ? max : bits;
        if (prefix < 0 || prefix > max) throw new IllegalArgumentException("无效的 CIDR 前缀: " + text);
        String normalized = address.getHostAddress() + (prefix == max ? "" : "/" + prefix);
        return new Rule(normalized, address.getAddress(), prefix);
    }

    private static InetAddress literal(String host) {
        if (host == null || host.isBlank() || host.length() > 64) return null;
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        try { return InetAddress.ofLiteral(host); }
        catch (IllegalArgumentException e) { return null; }
    }
}
