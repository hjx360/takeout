package com.sky.entity;

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
public class Shop implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    //店铺名称
    private String name;

    //logo图片
    private String logo;

    //店铺简介
    private String description;

    //0休息中 1营业中
    private Integer status;

    //起送价
    private BigDecimal minAmount;

    //配送费
    private BigDecimal deliveryFee;

    //预计送达时间
    private String deliveryTime;

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

    //创建时间
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;

    //创建人（员工id）
    private Long createUser;

    //修改人
    private Long updateUser;

}
