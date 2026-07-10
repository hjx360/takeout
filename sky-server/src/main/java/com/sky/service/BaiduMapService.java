package com.sky.service;

import com.sky.dto.DeliveryInfoDTO;
import com.sky.dto.GeocodeResultDTO;
import com.sky.dto.RouteResultDTO;

public interface BaiduMapService {
    /**
     * 逆地理编码 - 经纬度转地址
     * @param lat 纬度
     * @param lng 经度
     * @return 地址字符串
     */
    String reverseGeocode(double lat, double lng);
    
    /**
     * 地理编码 - 地址转经纬度
     * @param address 地址字符串
     * @return 经纬度结果
     */
    GeocodeResultDTO geocode(String address);
    
    /**
     * 地理编码（带缓存） - 优先从数据库获取经纬度
     * 1. 如果addressId存在，先从数据库查询该地址的经纬度
     * 2. 如果数据库中有有效经纬度，直接返回
     * 3. 如果数据库中没有，调用百度API获取
     * 4. 获取成功后，更新数据库中的经纬度
     * 
     * @param address 地址字符串
     * @param addressId 地址ID（可选）
     * @return 经纬度结果
     */
    GeocodeResultDTO geocodeWithCache(String address, Long addressId);
    
    /**
     * 从数据库获取地址的经纬度（不调用API）
     * @param addressId 地址ID
     * @return 经纬度结果（如果不存在返回null）
     */
    GeocodeResultDTO geocodeFromDatabase(Long addressId);
    
    /**
     * 更新地址的经纬度到数据库
     * @param addressId 地址ID
     * @param lng 经度
     * @param lat 纬度
     */
    void updateAddressGeocode(Long addressId, Double lng, Double lat);
    
    /**
     * 路线规划 - 计算两点之间的距离和时间
     * @param originLat 起点纬度
     * @param originLng 起点经度
     * @param destLat 终点纬度
     * @param destLng 终点经度
     * @param mode 导航模式：driving/riding/walking
     * @return 路线规划结果
     */
    RouteResultDTO route(double originLat, double originLng, double destLat, double destLng, String mode);
    
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
    DeliveryInfoDTO calculateDeliveryInfo(Double originLng, Double originLat, Double destLng, Double destLat, Double deliveryFee, String mode);
}
