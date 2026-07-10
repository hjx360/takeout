package com.sky.controller.map;

import com.sky.dto.BaiduMapDTO;
import com.sky.dto.DeliveryInfoDTO;
import com.sky.dto.GeocodeResultDTO;
import com.sky.dto.RouteResultDTO;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import com.sky.service.BaiduMapService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/map")
public class BaiduMapController {
    private final BaiduMapService baiduMapService;

    public BaiduMapController(BaiduMapService baiduMapService) {
        this.baiduMapService = baiduMapService;
    }

    /**
     * 逆地理编码 - 经纬度转地址
     * @param baiduMapDTO 经纬度参数
     * @return 地址字符串
     */
    @PostMapping("/reverse-geocode")
    public Result<String> reverseGeocode(@RequestBody BaiduMapDTO baiduMapDTO) {
        try {
            String address = baiduMapService.reverseGeocode(baiduMapDTO.getLat(), baiduMapDTO.getLng());
            return Result.success(address);
        } catch (Exception e) {
            throw new BaseException(e.getMessage());
        }
    }

    /**
     * 地理编码 - 地址转经纬度
     * 优先从数据库获取，如果不存在则调用百度API并更新数据库
     * 
     * @param address 地址字符串
     * @param addressId 地址ID（可选，用于优先从数据库查询）
     * @return 经纬度结果
     */
    @GetMapping("/geocode")
    public Result<GeocodeResultDTO> geocode(
            @RequestParam String address,
            @RequestParam(required = false) Long addressId) {
        try {
            // 添加详细的日志输出，帮助追踪问题
            log.info("[BaiduMapController] 接收地理编码请求:");
            log.info("[BaiduMapController] - 地址: {}", address);
            log.info("[BaiduMapController] - 地址ID: {}", addressId);
            log.info("[BaiduMapController] - 地址ID类型: {}", (addressId != null ? addressId.getClass().getName() : "null"));
            
            GeocodeResultDTO result = baiduMapService.geocodeWithCache(address, addressId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[BaiduMapController] 地理编码异常: {}", e.getMessage());
            throw new BaseException(e.getMessage());
        }
    }

    /**
     * 根据地址ID获取经纬度（仅从数据库查询，不调用API）
     * 
     * @param addressId 地址ID
     * @return 经纬度结果（如果数据库中不存在则返回null）
     */
    @GetMapping("/geocode-by-id")
    public Result<GeocodeResultDTO> geocodeById(@RequestParam Long addressId) {
        try {
            GeocodeResultDTO result = baiduMapService.geocodeFromDatabase(addressId);
            return Result.success(result);
        } catch (Exception e) {
            throw new BaseException(e.getMessage());
        }
    }

    /**
     * 路线规划 - 计算两点之间的距离和时间
     * @param originLat 起点纬度
     * @param originLng 起点经度
     * @param destLat 终点纬度
     * @param destLng 终点经度
     * @param mode 导航模式：driving/riding/walking
     * @return 路线规划结果
     */
    @GetMapping("/route")
    public Result<RouteResultDTO> route(
            @RequestParam Double originLat,
            @RequestParam Double originLng,
            @RequestParam Double destLat,
            @RequestParam Double destLng,
            @RequestParam(defaultValue = "riding") String mode) {
        try {
            RouteResultDTO result = baiduMapService.route(originLat, originLng, destLat, destLng, mode);
            return Result.success(result);
        } catch (Exception e) {
            throw new BaseException(e.getMessage());
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
     * @param deliveryFee 配送费（可选）
     * @param mode 导航模式：driving/riding/walking（默认riding）
     * @return 配送信息结果（包含距离、时间、预计送达时间等）
     */
    @GetMapping("/delivery-info")
    public Result<DeliveryInfoDTO> calculateDeliveryInfo(
            @RequestParam Double originLng,
            @RequestParam Double originLat,
            @RequestParam Double destLng,
            @RequestParam Double destLat,
            @RequestParam(required = false) Double deliveryFee,
            @RequestParam(defaultValue = "riding") String mode) {
        try {
            log.info("[BaiduMapController] 接收配送信息计算请求:");
            log.info("[BaiduMapController] - 起点经纬度: lng={}, lat={}", originLng, originLat);
            log.info("[BaiduMapController] - 终点经纬度: lng={}, lat={}", destLng, destLat);
            log.info("[BaiduMapController] - 配送费: {}", deliveryFee);
            log.info("[BaiduMapController] - 导航模式: {}", mode);
            
            DeliveryInfoDTO result = baiduMapService.calculateDeliveryInfo(originLng, originLat, destLng, destLat, deliveryFee, mode);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[BaiduMapController] 配送信息计算异常: {}", e.getMessage());
            throw new BaseException(e.getMessage());
        }
    }
}
