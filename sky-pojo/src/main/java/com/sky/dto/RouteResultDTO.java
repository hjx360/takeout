package com.sky.dto;

import lombok.Data;

/**
 * 路线规划结果DTO
 * 两点之间的距离和时间计算结果
 */
@Data
public class RouteResultDTO {
    /**
     * 距离（米）
     */
    private Integer distance;
    
    /**
     * 时间（秒）
     */
    private Integer duration;
    
    /**
     * 导航模式：driving/riding/walking
     */
    private String mode;
    
    /**
     * 是否为估算值（API失败时使用直线距离估算）
     */
    private Boolean isEstimated;
}