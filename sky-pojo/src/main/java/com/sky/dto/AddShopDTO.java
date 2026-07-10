package com.sky.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddShopDTO {
    private String name;

    //logo图片
    private String logo;

    //店铺简介
    private String description;

    //起送价
    private BigDecimal minAmount;
    //店铺详细地址
    private String addressDetail;

    //经度
    private BigDecimal longitude;

    //纬度
    private BigDecimal latitude;

    //店铺创建人用户名
    private String username;
    //店铺创建人姓名
    private String creatorName;
    //店铺创建人密码
    private String password;

    //店铺创建人手机号
    private String phone;
    //店铺创建人性别
    private String sex;

    //店铺创建人身份证号
    private String idNumber;
}
