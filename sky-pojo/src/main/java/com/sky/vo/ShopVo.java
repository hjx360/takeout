package com.sky.vo;

import com.sky.entity.Dish;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopVo {
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
    //店铺详细地址
    private String addressDetail;
    //经度
    private BigDecimal longitude;

    //纬度
    private BigDecimal latitude;

    //前三个菜品
    private List<Dish> dishes;
}
