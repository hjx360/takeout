package com.sky.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.DeliveryInfoDTO;
import com.sky.dto.GeocodeResultDTO;
import com.sky.dto.RouteResultDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Shop;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.ShopMapper;
import com.sky.properties.BaiduMapProperties;
import com.sky.service.BaiduMapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BaiduMapServiceImpl implements BaiduMapService {
    private final BaiduMapProperties baiduMapProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AddressBookMapper addressBookMapper;
    private final ShopMapper shopMapper;
    
    // 地址缓存：标准化地址字符串 -> 经纬度结果
    // 使用ConcurrentHashMap保证线程安全
    private static final ConcurrentHashMap<String, GeocodeCacheEntry> GEOCODE_CACHE = new ConcurrentHashMap<>();
    
    // 缓存过期时间（毫秒）：24小时
    private static final long CACHE_EXPIRE_TIME = 24 * 60 * 60 * 1000;
    
    // 配送路线计算重试次数
    private static final int MAX_RETRY_COUNT = 3;
    
    // 配送路线计算重试间隔（毫秒）
    private static final long RETRY_INTERVAL = 1000;

    public BaiduMapServiceImpl(BaiduMapProperties baiduMapProperties, AddressBookMapper addressBookMapper, ShopMapper shopMapper) {
        this.baiduMapProperties = baiduMapProperties;
        this.addressBookMapper = addressBookMapper;
        this.shopMapper = shopMapper;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * 缓存条目类
     */
    private static class GeocodeCacheEntry {
        private final Double lng;
        private final Double lat;
        private final Integer confidence;
        private final long timestamp;
        
        public GeocodeCacheEntry(Double lng, Double lat, Integer confidence) {
            this.lng = lng;
            this.lat = lat;
            this.confidence = confidence;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRE_TIME;
        }
        
        public GeocodeResultDTO toDTO() {
            GeocodeResultDTO dto = new GeocodeResultDTO();
            dto.setLng(lng);
            dto.setLat(lat);
            dto.setConfidence(confidence);
            dto.setLevel("cache");
            return dto;
        }
        
        // Getter methods
        public Double getLng() {
            return lng;
        }
        
        public Double getLat() {
            return lat;
        }
        
        public Integer getConfidence() {
            return confidence;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 根据经纬度获取格式化地址（逆地理编码）
     *
     * @param lat 纬度
     * @param lng 经度
     * @return 地址字符串
     */
    public String reverseGeocode(double lat, double lng) {
        // 使用UriComponentsBuilder构建URL
        URI uri = UriComponentsBuilder.fromHttpUrl("http://api.map.baidu.com/reverse_geocoding/v3/")
                .queryParam("ak", baiduMapProperties.getAk())
                .queryParam("output", "json")
                .queryParam("coordtype", "wgs84ll")   // 前端原生定位返回的是WGS84坐标
                .queryParam("location", lat + "," + lng)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        log.info("[百度地图] 逆地理编码请求URI: {}", uri);
        String result = restTemplate.getForObject(uri, String.class);
        log.info("[百度地图] 逆地理编码响应: {}", result);

        JsonNode root;
        try {
            root = objectMapper.readTree(result);
        } catch (JsonProcessingException e) {
            log.error("[百度地图] 逆地理编码JSON解析失败", e);
            throw new RuntimeException("百度逆地理编码返回结果解析失败", e);
        }

        int status = root.get("status").asInt();
        if (status == 0) {
            // 返回详细地址
            return root.get("result").get("formatted_address").asText();
        } else {
            String message = root.has("message") ? root.get("message").asText() : getBaiduErrorMessage(status);
            log.error("[百度地图] 逆地理编码失败，状态码: {}, 错误信息: {}", status, message);
            throw new RuntimeException("百度逆地理编码失败：" + message);
        }
    }

    /**
     * 根据地址获取经纬度（地理编码）
     * 百度API限制：address参数最多84字节
     *
     * @param address 地址字符串
     * @return 经纬度结果
     */
    public GeocodeResultDTO geocode(String address) {
        // 提取城市名称作为city参数（提高解析准确性）
        String city = extractCityFromAddress(address);
        
        // 优化地址：去掉省份前缀，缩短地址长度（百度API限制84字节）
        String optimizedAddress = optimizeAddress(address, city);
        
        log.info("[百度地图] 原始地址: {}, 提取城市: {}, 优化后地址: {}", address, city, optimizedAddress);
        
        // 检查地址字节长度（UTF-8编码，百度API限制84字节）
        int addressBytes = optimizedAddress.getBytes(StandardCharsets.UTF_8).length;
        if (addressBytes > 84) {
            log.warn("[百度地图] 地址字节长度({})超过84字节限制，尝试进一步简化", addressBytes);
            // 进一步简化：只保留城市+区+路名+门牌号
            optimizedAddress = simplifyAddress(optimizedAddress, city);
            addressBytes = optimizedAddress.getBytes(StandardCharsets.UTF_8).length;
            log.info("[百度地图] 简化后地址: {}, 字节长度: {}", optimizedAddress, addressBytes);
        }
        
        // 使用UriComponentsBuilder构建URL，避免二次编码问题
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl("http://api.map.baidu.com/geocoding/v3/")
                .queryParam("ak", baiduMapProperties.getAk())
                .queryParam("output", "json")
                .queryParam("address", optimizedAddress);
        
        // 只有提取到城市名称才添加city参数
        if (city != null && !city.isEmpty()) {
            builder.queryParam("city", city);
        }
        
        // 构建URI，encode()会自动处理编码
        URI uri = builder.build().encode(StandardCharsets.UTF_8).toUri();
        
        log.info("[百度地图] 地理编码请求URI: {}", uri);
        
        String result = restTemplate.getForObject(uri, String.class);
        log.info("[百度地图] 地理编码响应: {}", result);

        JsonNode root;
        try {
            root = objectMapper.readTree(result);
        } catch (JsonProcessingException e) {
            log.error("[百度地图] 地理编码JSON解析失败", e);
            throw new RuntimeException("百度地理编码返回结果解析失败", e);
        }

        int status = root.get("status").asInt();
        if (status == 0) {
            JsonNode location = root.get("result").get("location");
            GeocodeResultDTO dto = new GeocodeResultDTO();
            dto.setLng(location.get("lng").asDouble());
            dto.setLat(location.get("lat").asDouble());
            dto.setConfidence(root.get("result").get("confidence").asInt());
            dto.setLevel(root.get("result").get("level").asText());
            log.info("[百度地图] 地理编码成功: lng={}, lat={}, confidence={}", 
                    dto.getLng(), dto.getLat(), dto.getConfidence());
            return dto;
        } else {
            // 同时检查message和msg字段（百度API两种格式）
            String message = root.has("message") ? root.get("message").asText() : 
                            (root.has("msg") ? root.get("msg").asText() : getBaiduErrorMessage(status));
            log.error("[百度地图] 地理编码失败，状态码: {}, 错误信息: {}, 地址: {}", status, message, optimizedAddress);
            throw new RuntimeException("百度地理编码失败：" + message);
        }
    }

    /**
     * 优化地址：去掉省份前缀，缩短地址长度
     * 
     * @param address 原始地址
     * @param city 已提取的城市名称
     * @return 优化后的地址
     */
    private String optimizeAddress(String address, String city) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        
        String optimized = address;
        
        // 去掉省份前缀（省、自治区）
        if (optimized.contains("省")) {
            int provinceEnd = optimized.indexOf("省") + 1;
            optimized = optimized.substring(provinceEnd);
        } else if (optimized.contains("自治区")) {
            int provinceEnd = optimized.indexOf("自治区") + 3;
            optimized = optimized.substring(provinceEnd);
        }
        
        // 去掉"壮族自治区"等特殊格式中的"族"字冗余
        if (optimized.contains("壮族") && optimized.contains("市")) {
            // 例如：南宁市 -> 保持不变
        }
        
        return optimized.trim();
    }

    /**
     * 进一步简化地址：只保留关键定位信息
     * 格式：城市+区+路名+门牌号
     * 
     * @param address 地址
     * @param city 城市名称
     * @return 简化后的地址
     */
    private String simplifyAddress(String address, String city) {
        if (address == null || address.isEmpty()) {
            return address;
        }
        
        // 提取区名
        String district = null;
        int districtIndex = address.indexOf("区");
        if (districtIndex > 0) {
            // 找到区名之前的部分（去掉城市名）
            int startIdx = 0;
            if (city != null && address.startsWith(city)) {
                startIdx = city.length();
            }
            district = address.substring(startIdx, districtIndex + 1);
        }
        
        // 提取路名和门牌号
        String roadAndNumber = null;
        int roadIndex = address.indexOf("路");
        if (roadIndex > 0) {
            // 从路名开始截取到末尾
            roadAndNumber = address.substring(roadIndex - 2); // 包含路名前的2个字（如"明秀西路"中的"明秀"）
        }
        
        // 组合简化地址
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
        // 如果简化后仍然过长，只保留路名+门牌号
        if (result.getBytes(StandardCharsets.UTF_8).length > 84) {
            if (roadAndNumber != null) {
                result = city + roadAndNumber;
            }
        }
        
        return result;
    }

    /**
     * 从地址中提取城市名称
     * 
     * @param address 完整地址
     * @return 城市名称，如果无法提取则返回null
     */
    private String extractCityFromAddress(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        
        // 尝试匹配"市"关键字
        int cityIndex = address.indexOf("市");
        if (cityIndex > 0) {
            // 找到"市"之前的内容，尝试提取城市名
            String beforeCity = address.substring(0, cityIndex + 1);
            
            // 如果有省/自治区前缀，去掉它
            int provinceEnd = -1;
            if (beforeCity.contains("省")) {
                provinceEnd = beforeCity.indexOf("省");
            } else if (beforeCity.contains("自治区")) {
                provinceEnd = beforeCity.indexOf("自治区") + 2;
            }
            
            if (provinceEnd >= 0 && provinceEnd < cityIndex) {
                return beforeCity.substring(provinceEnd + 1);
            }
            
            // 如果没有省前缀，直接返回城市名
            return beforeCity;
        }
        
        // 如果找不到"市"，尝试匹配直辖市
        if (address.contains("北京")) return "北京市";
        if (address.contains("上海")) return "上海市";
        if (address.contains("天津")) return "天津市";
        if (address.contains("重庆")) return "重庆市";
        
        return null;
    }

    /**
     * 路线规划 - 计算两点之间的距离和时间
     *
     * @param originLat 起点纬度
     * @param originLng 起点经度
     * @param destLat   终点纬度
     * @param destLng   终点经度
     * @param mode      导航模式：driving/riding/walking
     * @return 路线规划结果
     */
    public RouteResultDTO route(double originLat, double originLng, double destLat, double destLng, String mode) {
        // 根据模式选择不同的API
        String apiUrl;
        if ("driving".equals(mode)) {
            apiUrl = "http://api.map.baidu.com/direction/v2/driving";
        } else if ("riding".equals(mode)) {
            apiUrl = "http://api.map.baidu.com/direction/v2/riding";
        } else {
            apiUrl = "http://api.map.baidu.com/direction/v2/walking";
        }

        // 使用UriComponentsBuilder构建URL
        URI uri = UriComponentsBuilder.fromHttpUrl(apiUrl)
                .queryParam("ak", baiduMapProperties.getAk())
                .queryParam("output", "json")
                .queryParam("origin", originLat + "," + originLng)
                .queryParam("destination", destLat + "," + destLng)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();

        log.info("[百度地图] 路线规划请求URI: {}", uri);
        String result = restTemplate.getForObject(uri, String.class);
        log.info("[百度地图] 路线规划响应: {}", result);

        JsonNode root;
        try {
            root = objectMapper.readTree(result);
        } catch (JsonProcessingException e) {
            log.error("[百度地图] 路线规划JSON解析失败", e);
            throw new RuntimeException("百度路线规划返回结果解析失败", e);
        }

        int status = root.get("status").asInt();
        if (status == 0) {
            JsonNode routes = root.get("result").get("routes");
            if (routes != null && routes.isArray() && routes.size() > 0) {
                JsonNode route = routes.get(0);
                RouteResultDTO dto = new RouteResultDTO();
                dto.setDistance(route.get("distance").asInt());
                dto.setDuration(route.get("duration").asInt());
                dto.setMode(mode);
                dto.setIsEstimated(false);
                log.info("[百度地图] 路线规划成功: distance={}, duration={}", dto.getDistance(), dto.getDuration());
                return dto;
            }
        }

        // 如果路线规划失败，记录错误并使用直线距离估算
        String message = root.has("message") ? root.get("message").asText() : getBaiduErrorMessage(status);
        log.warn("[百度地图] 路线规划失败，使用直线距离估算。状态码: {}, 错误信息: {}", status, message);
        
        RouteResultDTO dto = new RouteResultDTO();
        double directDistance = calculateDirectDistance(originLat, originLng, destLat, destLng);
        dto.setDistance((int) directDistance);
        
        // 根据配送方式估算时间
        // 骑行平均速度：15km/h = 4.17m/s
        // 步行平均速度：5km/h = 1.39m/s
        // 驾车平均速度：30km/h = 8.33m/s（城市道路）
        double speed;
        if ("driving".equals(mode)) {
            speed = 8.33;
        } else if ("riding".equals(mode)) {
            speed = 4.17;
        } else {
            speed = 1.39;
        }
        
        dto.setDuration((int) (directDistance / speed));
        dto.setMode(mode);
        dto.setIsEstimated(true);
        return dto;
    }

    /**
     * 计算两点之间的直线距离（Haversine公式）
     *
     * @param lat1 起点纬度
     * @param lng1 起点经度
     * @param lat2 终点纬度
     * @param lng2 终点经度
     * @return 距离（米）
     */
    private double calculateDirectDistance(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // 地球半径（米）

        double lat1Rad = lat1 * Math.PI / 180;
        double lat2Rad = lat2 * Math.PI / 180;
        double deltaLat = (lat2 - lat1) * Math.PI / 180;
        double deltaLng = (lng2 - lng1) * Math.PI / 180;

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    /**
     * 根据百度API状态码获取错误信息
     *
     * @param status 状态码
     * @return 错误信息描述
     */
    private String getBaiduErrorMessage(int status) {
        switch (status) {
            case 0:
                return "成功";
            case 1:
                return "服务器内部错误";
            case 2:
                return "请求参数非法";
            case 3:
                return "权限校验失败";
            case 4:
                return "AK不存在";
            case 5:
                return "AK参数缺失";
            case 101:
                return "AK被禁用";
            case 102:
                return "白名单IP校验失败";
            case 103:
                return "白名单域名校验失败";
            case 200:
                return "AK不存在或被禁用";
            case 201:
                return "AK被禁用";
            case 202:
                return "白名单IP校验失败";
            case 203:
                return "白名单域名校验失败";
            case 300:
                return "天配额超限";
            case 301:
                return "并发超限";
            case 302:
                return "IP黑名单校验失败";
            case 401:
                return "服务未开通";
            case 402:
                return "服务已停用";
            default:
                return "未知错误，状态码：" + status;
        }
    }

    /**
     * 计算配送信息（基于经纬度坐标）
     * 直接使用经纬度坐标计算配送路线，无需地理编码
     * 
     * @param originLng 起点经度（店铺）
     * @param originLat 起点纬度（店铺）
     * @param destLng 终点经度（用户）
     * @param destLat 终点纬度（用户）
     * @param deliveryFee 配送费（可选，如果提供则包含在结果中）
     * @param mode 导航模式：driving/riding/walking（默认riding）
     * @return 配送信息结果
     */
    @Override
    public DeliveryInfoDTO calculateDeliveryInfo(Double originLng, Double originLat, Double destLng, Double destLat, Double deliveryFee, String mode) {
        log.info("[百度地图] ===== 配送信息计算开始 =====");
        log.info("[百度地图] 接收参数:");
        log.info("[百度地图] - 起点经纬度: lng={}, lat={}", originLng, originLat);
        log.info("[百度地图] - 终点经纬度: lng={}, lat={}", destLng, destLat);
        log.info("[百度地图] - 配送费: {}", deliveryFee);
        log.info("[百度地图] - 导航模式: {}", mode);
        
        // 1. 参数验证
        if (originLng == null || originLat == null) {
            log.error("[百度地图] 起点经纬度参数缺失");
            return DeliveryInfoDTO.builder()
                    .errorType("shop")
                    .errorMessage("店铺经纬度参数缺失")
                    .build();
        }
        
        if (destLng == null || destLat == null) {
            log.error("[百度地图] 终点经纬度参数缺失");
            return DeliveryInfoDTO.builder()
                    .errorType("user")
                    .errorMessage("用户经纬度参数缺失")
                    .build();
        }
        
        // 验证经纬度范围
        if (originLng < -180 || originLng > 180 || originLat < -90 || originLat > 90) {
            log.error("[百度地图] 起点经纬度超出合理范围: lng={}, lat={}", originLng, originLat);
            return DeliveryInfoDTO.builder()
                    .errorType("shop")
                    .errorMessage("店铺经纬度超出合理范围")
                    .originLng(originLng)
                    .originLat(originLat)
                    .build();
        }
        
        if (destLng < -180 || destLng > 180 || destLat < -90 || destLat > 90) {
            log.error("[百度地图] 终点经纬度超出合理范围: lng={}, lat={}", destLng, destLat);
            return DeliveryInfoDTO.builder()
                    .errorType("user")
                    .errorMessage("用户经纬度超出合理范围")
                    .destLng(destLng)
                    .destLat(destLat)
                    .build();
        }
        
        // 设置默认导航模式
        if (mode == null || mode.isEmpty()) {
            mode = "riding";
        }
        
        // 2. 调用路线规划API（带重试逻辑）
        RouteResultDTO routeResult = null;
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                log.info("[百度地图] 第{}次尝试调用路线规划API", retryCount + 1);
                routeResult = route(originLat, originLng, destLat, destLng, mode);
                log.info("[百度地图] 路线规划成功: distance={}, duration={}", routeResult.getDistance(), routeResult.getDuration());
                break;
            } catch (Exception e) {
                lastException = e;
                log.error("[百度地图] 第{}次路线规划失败: {}", retryCount + 1, e.getMessage());
                retryCount++;
                
                if (retryCount < MAX_RETRY_COUNT) {
                    log.info("[百度地图] 等待{}毫秒后重试...", RETRY_INTERVAL);
                    try {
                        Thread.sleep(RETRY_INTERVAL);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("[百度地图] 重试等待被中断");
                        break;
                    }
                }
            }
        }
        
        // 3. 如果所有重试都失败，使用直线距离估算
        if (routeResult == null) {
            log.warn("[百度地图] 所有重试失败，使用直线距离估算");
            double directDistance = calculateDirectDistance(originLat, originLng, destLat, destLng);
            
            // 根据配送方式估算时间
            double speed;
            if ("driving".equals(mode)) {
                speed = 8.33; // 30km/h
            } else if ("riding".equals(mode)) {
                speed = 4.17; // 15km/h
            } else {
                speed = 1.39; // 5km/h
            }
            
            routeResult = new RouteResultDTO();
            routeResult.setDistance((int) directDistance);
            routeResult.setDuration((int) (directDistance / speed));
            routeResult.setMode(mode);
            routeResult.setIsEstimated(true);
            
            log.info("[百度地图] 直线距离估算: distance={}, duration={}", routeResult.getDistance(), routeResult.getDuration());
        }
        
        // 4. 构建配送信息DTO
        DeliveryInfoDTO deliveryInfo = buildDeliveryInfoDTO(routeResult, deliveryFee, originLng, originLat, destLng, destLat);
        
        log.info("[百度地图] ===== 配送信息计算完成 =====");
        log.info("[百度地图] 结果: distance={}, duration={}, estimatedArrival={}", 
                deliveryInfo.getFormattedDistance(), deliveryInfo.getFormattedDuration(), deliveryInfo.getEstimatedArrival());
        
        return deliveryInfo;
    }
    
    /**
     * 构建配送信息DTO
     *
     * @param routeResult 路线规划结果
     * @param deliveryFee 配送费（可选，如果不传递则自动计算）
     * @param originLng 起点经度
     * @param originLat 起点纬度
     * @param destLng 终点经度
     * @param destLat 终点纬度
     * @return 配送信息DTO
     */
    private DeliveryInfoDTO buildDeliveryInfoDTO(RouteResultDTO routeResult, Double deliveryFee,
                                                  Double originLng, Double originLat, Double destLng, Double destLat) {
        // 格式化距离
        String formattedDistance = formatDistance(routeResult.getDistance());

        // 格式化时间
        String formattedDuration = formatDuration(routeResult.getDuration());

        // 计算预计送达时间
        String estimatedArrival = calculateEstimatedArrival(routeResult.getDuration());

        // ✅ 配送费计算逻辑：如果前端没有传递配送费，则自动计算
        // 规则：按实际配送距离计算，每公里收费1元，不足1公里时配送费为0元
        Double calculatedDeliveryFee = deliveryFee;
        if (deliveryFee == null || deliveryFee == 0) {
            // 距离转换为公里（routeResult.getDistance()单位是米）
            int distanceInMeters = routeResult.getDistance();
            int distanceInKm = distanceInMeters / 1000; // 距离（公里）

            // 配送费计算：每公里1元，不足1公里为0元
            calculatedDeliveryFee = distanceInKm >= 1 ? (double) distanceInKm : 0.0;

            log.info("[百度地图] 自动计算配送费: distance={}米, distanceInKm={}公里, deliveryFee={}元",
                    distanceInMeters, distanceInKm, calculatedDeliveryFee);
        }

        return DeliveryInfoDTO.builder()
                .distance(routeResult.getDistance())
                .duration(routeResult.getDuration())
                .formattedDistance(formattedDistance)
                .formattedDuration(formattedDuration)
                .estimatedArrival(estimatedArrival)
                .deliveryFee(calculatedDeliveryFee)
                .mode(routeResult.getMode())
                .isEstimated(routeResult.getIsEstimated())
                .originLng(originLng)
                .originLat(originLat)
                .destLng(destLng)
                .destLat(destLat)
                .build();
    }
    
    /**
     * 格式化距离
     * 
     * @param distance 距离（米）
     * @return 格式化的距离字符串
     */
    private String formatDistance(int distance) {
        if (distance < 1000) {
            return distance + "米";
        } else {
            double km = distance / 1000.0;
            return String.format("%.1f公里", km);
        }
    }
    
    /**
     * 格式化时间
     * 
     * @param duration 时间（秒）
     * @return 格式化的时间字符串
     */
    private String formatDuration(int duration) {
        int minutes = duration / 60;
        if (minutes < 60) {
            return minutes + "分钟";
        } else {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            return hours + "小时" + (remainingMinutes > 0 ? remainingMinutes + "分钟" : "");
        }
    }
    
    /**
     * 计算预计送达时间
     * 
     * @param duration 配送时间（秒）
     * @return 预计送达时间字符串（格式：HH:mm）
     */
    private String calculateEstimatedArrival(int duration) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime arrivalTime = now.plusSeconds(duration);
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return arrivalTime.format(formatter);
    }

    /**
     * 地理编码（带缓存） - 优先从数据库获取经纬度
     * 
     * 缓存策略：
     * 1. 如果addressId存在，优先从用户地址表查询经纬度
     * 2. 如果addressId不存在，从地址缓存表查询（基于标准化地址字符串）
     * 3. 如果缓存中没有，调用百度API
     * 4. 获取成功后，更新缓存表和用户地址表（如果有addressId）
     * 
     * @param address 地址字符串
     * @param addressId 地址ID（可选）
     * @return 经纬度结果
     */
    @Override
    public GeocodeResultDTO geocodeWithCache(String address, Long addressId) {
        // 添加更详细的日志输出
        log.info("[百度地图] ===== 地理编码请求开始 =====");
        log.info("[百度地图] 接收参数:");
        log.info("[百度地图] - 原始地址: {}", address);
        log.info("[百度地图] - 地址ID: {}", addressId);
        log.info("[百度地图] - 地址ID是否为null: {}", addressId == null);
        
        if (addressId != null) {
            log.info("[百度地图] - 地址ID值: {}", addressId);
            log.info("[百度地图] - 地址ID类型: {}", addressId.getClass().getName());
        }
        
        // 优化地址：去掉省份前缀，标准化地址格式
        String city = extractCityFromAddress(address);
        String optimizedAddress = optimizeAddress(address, city);
        String normalizedAddress = normalizeAddress(optimizedAddress);
        
        log.info("[百度地图] 标准化地址: {}, 城市: {}", normalizedAddress, city);
        
        // 1. 先查询内存缓存（基于标准化地址字符串）
        GeocodeCacheEntry cacheEntry = GEOCODE_CACHE.get(normalizedAddress);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            log.info("[百度地图] 从内存缓存获取到经纬度: lng={}, lat={}", cacheEntry.getLng(), cacheEntry.getLat());
            return GeocodeResultDTO.builder()
                    .lng(cacheEntry.getLng())
                    .lat(cacheEntry.getLat())
                    .confidence(cacheEntry.getConfidence())
                    .level("cache")
                    .build();
        }
        
        // 2. 查询数据库（根据地址类型）
        if (addressId != null) {
            // 用户地址：查询用户地址表
            GeocodeResultDTO dbResult = geocodeFromDatabase(addressId);
            if (dbResult != null && dbResult.getLng() != null && dbResult.getLat() != null) {
                log.info("[百度地图] 从用户地址表获取到经纬度: lng={}, lat={}", dbResult.getLng(), dbResult.getLat());
                
                // 更新内存缓存
                GEOCODE_CACHE.put(normalizedAddress, new GeocodeCacheEntry(dbResult.getLng(), dbResult.getLat(), dbResult.getConfidence()));
                log.info("[百度地图] 已更新内存缓存，地址: {}", normalizedAddress);
                
                return dbResult;
            }
            log.info("[百度地图] 用户地址表中无有效经纬度");
        } else {
            // 店铺地址：提示前端应该使用shop表的经纬度，不应该调用地理编码API
            log.warn("[百度地图] 店铺地址（addressId为null）不应该调用地理编码API，前端应该直接使用shop表的经纬度");
            log.warn("[百度地图] 建议：前端在获取店铺详情时，直接使用shop表的longitude和latitude字段");
            
            // 但是为了兼容性，仍然继续查询缓存表和调用百度API
            log.info("[百度地图] 继续查询缓存表和调用百度API（兼容模式）");
        }
        
        // 3. 查询缓存表（address_geocode_cache）
        GeocodeResultDTO cachedResult = geocodeFromCacheTable(normalizedAddress);
        if (cachedResult != null && cachedResult.getLng() != null && cachedResult.getLat() != null) {
            log.info("[百度地图] 从缓存表获取到经纬度: lng={}, lat={}", cachedResult.getLng(), cachedResult.getLat());
            
            // 更新内存缓存
            GEOCODE_CACHE.put(normalizedAddress, new GeocodeCacheEntry(cachedResult.getLng(), cachedResult.getLat(), cachedResult.getConfidence()));
            log.info("[百度地图] 已更新内存缓存，地址: {}", normalizedAddress);
            
            // 如果有addressId，同步更新用户地址表
            if (addressId != null) {
                try {
                    updateAddressGeocode(addressId, cachedResult.getLng(), cachedResult.getLat());
                    log.info("[百度地图] 已同步更新用户地址表，地址ID: {}", addressId);
                } catch (Exception e) {
                    log.warn("[百度地图] 同步更新用户地址表失败: {}", e.getMessage());
                }
            }
            
            return cachedResult;
        }
        
        log.info("[百度地图] 缓存表中无记录，调用百度API");
        
        // 4. 调用百度API获取经纬度
        GeocodeResultDTO result = geocode(address);
        
        // 5. 如果获取成功，更新缓存（内存缓存、缓存表、用户地址表）
        if (result != null && result.getLng() != null && result.getLat() != null) {
            // 更新内存缓存
            GEOCODE_CACHE.put(normalizedAddress, new GeocodeCacheEntry(result.getLng(), result.getLat(), result.getConfidence()));
            log.info("[百度地图] 已保存到内存缓存: {}, lng={}, lat={}, 当前缓存大小: {}", 
                    normalizedAddress, result.getLng(), result.getLat(), GEOCODE_CACHE.size());
            
            // 更新缓存表
            try {
                saveGeocodeToCacheTable(normalizedAddress, result.getLng(), result.getLat());
                log.info("[百度地图] 已保存到缓存表，地址: {}", normalizedAddress);
            } catch (Exception e) {
                log.warn("[百度地图] 保存到缓存表失败: {}", e.getMessage());
            }
            
            // 如果有addressId，更新用户地址表
            if (addressId != null) {
                try {
                    updateAddressGeocode(addressId, result.getLng(), result.getLat());
                    log.info("[百度地图] 已更新用户地址表，地址ID: {}", addressId);
                } catch (Exception e) {
                    log.warn("[百度地图] 更新用户地址表失败: {}", e.getMessage());
                }
            }
        }
        
        return result;
    }

    /**
     * 标准化地址字符串（用于缓存匹配）
     * 去除多余空格、统一格式
     * 
     * @param address 地址字符串
     * @return 标准化后的地址
     */
    private String normalizeAddress(String address) {
        if (address == null || address.isEmpty()) {
            return "";
        }
        
        // 去除多余空格
        String normalized = address.trim().replaceAll("\\s+", " ");
        
        // 去除省份前缀（如果还有）
        if (normalized.contains("省")) {
            int idx = normalized.indexOf("省") + 1;
            normalized = normalized.substring(idx);
        }
        if (normalized.contains("自治区")) {
            int idx = normalized.indexOf("自治区") + 3;
            normalized = normalized.substring(idx);
        }
        
        // 去除"壮族自治区"等特殊格式
        normalized = normalized.replaceFirst("壮族", "");
        
        return normalized.trim();
    }

    /**
     * 从缓存表查询地址经纬度
     * 
     * @param normalizedAddress 标准化地址字符串
     * @return 经纬度结果（如果不存在或已过期返回null）
     */
    private GeocodeResultDTO geocodeFromCacheTable(String normalizedAddress) {
        if (normalizedAddress == null || normalizedAddress.isEmpty()) {
            return null;
        }
        
        GeocodeCacheEntry entry = GEOCODE_CACHE.get(normalizedAddress);
        if (entry == null) {
            log.debug("[百度地图] 缓存中无记录: {}", normalizedAddress);
            return null;
        }
        
        // 检查缓存是否过期
        if (entry.isExpired()) {
            log.info("[百度地图] 缓存已过期，移除: {}", normalizedAddress);
            GEOCODE_CACHE.remove(normalizedAddress);
            return null;
        }
        
        log.info("[百度地图] 从内存缓存获取到经纬度: {}, lng={}, lat={}", normalizedAddress, entry.lng, entry.lat);
        return entry.toDTO();
    }

    /**
     * 保存经纬度到缓存表
     * 
     * @param normalizedAddress 标准化地址字符串
     * @param lng 经度
     * @param lat 纬度
     */
    private void saveGeocodeToCacheTable(String normalizedAddress, Double lng, Double lat) {
        if (normalizedAddress == null || normalizedAddress.isEmpty() || lng == null || lat == null) {
            return;
        }
        
        GeocodeCacheEntry entry = new GeocodeCacheEntry(lng, lat, 80);
        GEOCODE_CACHE.put(normalizedAddress, entry);
        
        log.info("[百度地图] 已保存到内存缓存: {}, lng={}, lat={}, 当前缓存大小: {}", 
                normalizedAddress, lng, lat, GEOCODE_CACHE.size());
    }

    /**
     * 从数据库获取地址的经纬度（不调用API）
     * 
     * @param addressId 地址ID
     * @return 经纬度结果（如果不存在返回null）
     */
    @Override
    public GeocodeResultDTO geocodeFromDatabase(Long addressId) {
        if (addressId == null) {
            return null;
        }
        
        AddressBook addressBook = addressBookMapper.getById(addressId);
        if (addressBook == null) {
            log.warn("[百度地图] 地址不存在，ID: {}", addressId);
            return null;
        }
        
        String longitudeStr = addressBook.getLongitude();
        String latitudeStr = addressBook.getLatitude();
        
        // 检查经纬度是否有效
        if (longitudeStr == null || latitudeStr == null || longitudeStr.isEmpty() || latitudeStr.isEmpty()) {
            log.info("[百度地图] 地址经纬度为空，ID: {}", addressId);
            return null;
        }
        
        try {
            Double lng = Double.parseDouble(longitudeStr);
            Double lat = Double.parseDouble(latitudeStr);
            
            // 验证经纬度范围
            if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
                log.warn("[百度地图] 经纬度值无效，ID: {}, lng={}, lat={}", addressId, lng, lat);
                return null;
            }
            
            GeocodeResultDTO result = new GeocodeResultDTO();
            result.setLng(lng);
            result.setLat(lat);
            result.setConfidence(100); // 数据库中的数据置信度设为100
            result.setLevel("database");
            return result;
        } catch (NumberFormatException e) {
            log.warn("[百度地图] 经纬度格式错误，ID: {}, longitude={}, latitude={}", addressId, longitudeStr, latitudeStr);
            return null;
        }
    }

    /**
     * 更新地址的经纬度到数据库
     * 
     * @param addressId 地址ID
     * @param lng 经度
     * @param lat 纬度
     */
    @Override
    public void updateAddressGeocode(Long addressId, Double lng, Double lat) {
        if (addressId == null || lng == null || lat == null) {
            log.warn("[百度地图] 更新地址经纬度参数无效");
            return;
        }
        
        AddressBook addressBook = new AddressBook();
        addressBook.setId(addressId);
        addressBook.setLongitude(String.valueOf(lng));
        addressBook.setLatitude(String.valueOf(lat));
        
        addressBookMapper.update(addressBook);
        log.info("[百度地图] 已更新地址经纬度，ID: {}, lng={}, lat={}", addressId, lng, lat);
    }
}


