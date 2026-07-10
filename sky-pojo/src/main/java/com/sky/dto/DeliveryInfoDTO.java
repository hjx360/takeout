package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配送信息结果DTO
 * 包含配送距离、时间、预计送达时间、配送费等详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryInfoDTO {
    /**
     * 配送距离（米）
     */
    private Integer distance;

    /**
     * 配送时间（秒）
     */
    private Integer duration;

    /**
     * 格式化的距离字符串（例如："2.5公里"）
     */
    private String formattedDistance;

    /**
     * 格式化的时间字符串（例如："15分钟"）
     */
    private String formattedDuration;

    /**
     * 预计送达时间（例如："14:30"）
     */
    private String estimatedArrival;

    /**
     * 配送费（元）
     */
    private Double deliveryFee;

    /**
     * 导航模式：driving/riding/walking
     */
    private String mode;

    /**
     * 是否为估算值（API失败时使用直线距离估算）
     */
    private Boolean isEstimated;

    /**
     * 错误类型（如果计算失败）
     * shop: 店铺地址错误
     * user: 用户地址错误
     * api: API调用错误
     */
    private String errorType;

    /**
     * 错误消息（如果计算失败）
     */
    private String errorMessage;

    /**
     * 起点经度
     */
    private Double originLng;

    /**
     * 起点纬度
     */
    private Double originLat;

    /**
     * 终点经度
     */
    private Double destLng;

    /**
     * 终点纬度
     */
    private Double destLat;
}