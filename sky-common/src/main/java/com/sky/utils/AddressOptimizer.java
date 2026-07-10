package com.sky.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 地址字符串优化与标准化工具
 * <p>
 * 用于百度地图地理编码前的地址预处理，以及生成缓存键时的标准化。
 * 主要功能：提取城市、去除省份前缀、简化过长地址、标准化格式。
 */
@Slf4j
@Component
public class AddressOptimizer {

    /**
     * 从完整地址中提取城市名称（如 "南宁市"）
     *
     * @param address 原始地址
     * @return 城市名（含"市"字），提取失败返回 null
     */
    public String extractCity(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }

        // 优先匹配 "市" 关键字
        int cityIndex = address.indexOf("市");
        if (cityIndex > 0) {
            String beforeCity = address.substring(0, cityIndex + 1);

            // 去掉省份/自治区前缀
            int provinceEnd = -1;
            if (beforeCity.contains("省")) {
                provinceEnd = beforeCity.indexOf("省");
            } else if (beforeCity.contains("自治区")) {
                provinceEnd = beforeCity.indexOf("自治区") + 2;
            }

            if (provinceEnd >= 0 && provinceEnd < cityIndex) {
                return beforeCity.substring(provinceEnd + 1);
            }
            return beforeCity;
        }

        // 直辖市特殊处理
        if (address.contains("北京")) return "北京市";
        if (address.contains("上海")) return "上海市";
        if (address.contains("天津")) return "天津市";
        if (address.contains("重庆")) return "重庆市";

        return null;
    }

    /**
     * 优化地址用于百度地理编码 API 调用
     * <p>
     * 去除省份前缀，缩短长度，提高解析准确度。
     *
     * @param address 原始地址
     * @param city    已提取的城市名（可为 null）
     * @return 优化后的地址
     */
    public String optimizeForApi(String address, String city) {
        if (address == null || address.isEmpty()) {
            return address;
        }

        String optimized = address;

        // 去除省份前缀
        if (optimized.contains("省")) {
            int provinceEnd = optimized.indexOf("省") + 1;
            optimized = optimized.substring(provinceEnd);
        } else if (optimized.contains("自治区")) {
            int provinceEnd = optimized.indexOf("自治区") + 3;
            optimized = optimized.substring(provinceEnd);
        }

        return optimized.trim();
    }

    /**
     * 进一步简化地址（当优化后仍超过百度 API 84 字节限制时使用）
     * <p>
     * 尝试保留：城市 + 区 + 路名及门牌号，若仍过长则仅保留“城市 + 路名+门牌号”。
     *
     * @param address 已做过一次优化的地址
     * @param city    城市名
     * @return 简化后的地址
     */
    public String simplifyForApi(String address, String city) {
        if (address == null || address.isEmpty()) {
            return address;
        }

        // 提取区名
        String district = null;
        int districtIndex = address.indexOf("区");
        if (districtIndex > 0) {
            int startIdx = 0;
            if (city != null && address.startsWith(city)) {
                startIdx = city.length();
            }
            district = address.substring(startIdx, districtIndex + 1);
        }

        // 提取路名及门牌号
        String roadAndNumber = null;
        int roadIndex = address.indexOf("路");
        if (roadIndex > 0) {
            roadAndNumber = address.substring(roadIndex - 2); // 包含路名前两个字
        }

        // 组合
        StringBuilder simplified = new StringBuilder();
        if (city != null) {
            simplified.append(city);
        }
        if (district != null) {
            simplified.append(district);
        }
        if (roadAndNumber != null) {
            simplified.append(roadAndNumber);
        }

        String result = simplified.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > 84) {
            if (roadAndNumber != null) {
                result = city + roadAndNumber;
            }
        }
        return result;
    }

    /**
     * 标准化地址字符串，用作缓存键
     * <p>
     * 处理：去除所有省份/自治区前缀、去除“壮族”等冗余、合并空格、trim。
     * 与 {@link #optimizeForApi} 不同，这里会做更彻底的清理以提升缓存命中率。
     *
     * @param address 待标准化的地址
     * @return 标准化后的地址
     */
    public String normalize(String address) {
        if (address == null || address.isEmpty()) {
            return "";
        }

        String normalized = address.trim().replaceAll("\\s+", " ");

        // 去除省份前缀
        if (normalized.contains("省")) {
            int idx = normalized.indexOf("省") + 1;
            normalized = normalized.substring(idx);
        }
        if (normalized.contains("自治区")) {
            int idx = normalized.indexOf("自治区") + 3;
            normalized = normalized.substring(idx);
        }

        // 去除“壮族”等冗余字
        normalized = normalized.replaceFirst("壮族", "");

        return normalized.trim();
    }

    /**
     * 检查地址的 UTF-8 字节长度是否超过百度 API 限制（84 字节）
     *
     * @param address 地址字符串
     * @return true 表示超过限制
     */
    public boolean exceedsByteLimit(String address) {
        return address != null && address.getBytes(StandardCharsets.UTF_8).length > 84;
    }
}