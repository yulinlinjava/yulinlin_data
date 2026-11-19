package com.yulinlin.security.limit;

import com.yulinlin.security.SecurityLimitProperties;
import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Optional resolver retaining the trusted-proxy and single-IP validation policy. */
public final class TrustedProxyIpResolver implements IpResolver {

    private final String ipHeader;
    private final List<CidrBlock> trustedProxies;

    public TrustedProxyIpResolver(SecurityLimitProperties properties) {
        this.ipHeader = properties.getIpHeader();
        this.trustedProxies = parseNetworks(properties.getTrustedProxies());
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (request == null) return null;
        String remote = normalizeIp(request.getRemoteAddr());
        String fallback = remote == null ? String.valueOf(request.getRemoteAddr()) : remote;
        String resolved = fallback;
        if (remote != null && matchesAny(trustedProxies, remote)) {
            String forwarded = normalizeIp(request.getHeader(ipHeader));
            if (forwarded != null) resolved = forwarded;
        }
        return resolved;
    }

    private static boolean matchesAny(List<CidrBlock> networks, String address) {
        byte[] candidate = parseIp(address);
        if (candidate == null) return false;
        for (CidrBlock network : networks) {
            if (network.matches(candidate)) return true;
        }
        return false;
    }

    private static List<CidrBlock> parseNetworks(List<String> configured) {
        if (configured == null || configured.isEmpty()) return List.of();
        List<CidrBlock> result = new ArrayList<>(configured.size());
        for (String value : configured) {
            if (value == null || value.isBlank()) continue;
            result.add(CidrBlock.parse(value.trim()));
        }
        return List.copyOf(result);
    }

    private static String normalizeIp(String value) {
        if (value == null || value.isBlank() || value.indexOf(',') >= 0) return null;
        byte[] bytes = parseIp(value.trim());
        if (bytes == null) return null;
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException impossible) {
            return null;
        }
    }

    private static byte[] parseIp(String value) {
        if (value == null || value.isBlank()) return null;
        String address = value.trim();
        if (address.startsWith("[") && address.endsWith("]")) {
            address = address.substring(1, address.length() - 1);
        }
        if (address.indexOf(':') >= 0) {
            for (int index = 0; index < address.length(); index++) {
                char character = address.charAt(index);
                boolean hexadecimal = character >= '0' && character <= '9'
                        || character >= 'a' && character <= 'f'
                        || character >= 'A' && character <= 'F';
                if (!hexadecimal && character != ':' && character != '.') return null;
            }
            try {
                return InetAddress.getByName(address).getAddress();
            } catch (UnknownHostException ignored) {
                return null;
            }
        }
        String[] parts = address.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] bytes = new byte[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3) return null;
            int number = 0;
            for (int offset = 0; offset < part.length(); offset++) {
                char character = part.charAt(offset);
                if (character < '0' || character > '9') return null;
                number = number * 10 + character - '0';
            }
            if (number > 255) return null;
            bytes[index] = (byte) number;
        }
        return bytes;
    }

    private record CidrBlock(byte[] network, int prefixLength) {

        private static CidrBlock parse(String value) {
            int slash = value.indexOf('/');
            String address = slash < 0 ? value : value.substring(0, slash);
            byte[] bytes = parseIp(address);
            if (bytes == null) throw new IllegalArgumentException(
                    "Invalid yulinlin.security.limit.trusted-proxies entry: " + value);

            int maximumPrefix = bytes.length * Byte.SIZE;
            int prefix = maximumPrefix;
            if (slash >= 0) {
                if (slash != value.lastIndexOf('/') || slash == value.length() - 1) {
                    throw new IllegalArgumentException(
                            "Invalid yulinlin.security.limit.trusted-proxies CIDR: " + value);
                }
                try {
                    prefix = Integer.parseInt(value.substring(slash + 1));
                } catch (NumberFormatException error) {
                    throw new IllegalArgumentException(
                            "Invalid yulinlin.security.limit.trusted-proxies CIDR: " + value, error);
                }
                if (prefix < 0 || prefix > maximumPrefix) {
                    throw new IllegalArgumentException(
                            "Invalid yulinlin.security.limit.trusted-proxies prefix: " + value);
                }
            }
            return new CidrBlock(Arrays.copyOf(bytes, bytes.length), prefix);
        }

        private boolean matches(byte[] candidate) {
            if (candidate.length != network.length) return false;
            int completeBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < completeBytes; index++) {
                if (candidate[index] != network[index]) return false;
            }
            if (remainingBits == 0) return true;
            int mask = 0xff << (Byte.SIZE - remainingBits);
            return (candidate[completeBytes] & mask) == (network[completeBytes] & mask);
        }
    }
}
