package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地理编码结果DTO
 * 地址转经纬度的返回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeResultDTO {
    /**
     * 经度
     */
    private Double lng;
    
    /**
     * 纬度
     */
    private Double lat;
    
    /**
     * 精确度置信度
     */
    private Integer confidence;
    
    /**
     * 地址级别
     */
    private String level;
}