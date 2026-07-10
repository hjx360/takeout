package com.sky.dto;

import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrdersPaymentDTO implements Serializable {
    //订单号
    private String orderNumber;

    //付款方式
    private Integer payMethod;
    //预计送达时间
    private LocalDateTime estimatedDeliveryTime;
    //餐具 数量
    private int tablewareNumber;

    //餐具数量状态  1按餐量提供  0选择具体数量
    private Integer tablewareStatus;
    //地址id
    private Long addressId;

    // 地址
    private String address;
    //配送费
    private BigDecimal deliveryFee;
}
