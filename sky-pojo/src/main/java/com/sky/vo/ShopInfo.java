package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 店铺
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //店铺名称
    private String name;

    //logo图片
    private String logo;

    //店铺简介
    private String description;

    //省份编码
    private String provinceCode;

    //城市编码
    private String cityCode;

    //区县编码
    private String districtCode;

    //店铺详细地址
    private String addressDetail;

    //经度
    private BigDecimal longitude;

    //纬度
    private BigDecimal latitude;
    //地址
    private String address;
    //营业状态
    private Integer status;
    //最小起送价
    private BigDecimal minAmount;



}
