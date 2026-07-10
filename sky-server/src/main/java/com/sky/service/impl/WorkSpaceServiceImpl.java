package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrderStatisticsDTO;
import com.sky.entity.Orders;
import com.sky.entity.Setmeal;
import com.sky.mapper.*;
import com.sky.service.WorkSpaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.beans.beancontext.BeanContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Map;

@Slf4j
@Service
public class WorkSpaceServiceImpl implements WorkSpaceService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private ReportMapper reportMapper;
    @Autowired
    private ReportServiceImpl reportService;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private WorkSpaceMapper workSpaceMapper;

    @Override
    public BusinessDataVO getBusinessData() {
        Long shopId = BaseContext.getShopId();
        log.info("获取店铺 {} 的营业数据", shopId);

        // 直接获取营业额总和
        Double completedOrdersSumValue = reportMapper.getTotalTurnover(
                LocalDateTime.of(LocalDate.now(), LocalTime.MIN),
                LocalDateTime.of(LocalDate.now(), LocalTime.MAX),
                shopId,
                Orders.COMPLETED
        );

        if (completedOrdersSumValue == 0.00) {
            completedOrdersSumValue = 0.00;
        }
        log.info("营业额 {}", completedOrdersSumValue);
        
        OrderReportVO orderReportVO = reportService.ordersStatistics(LocalDate.now(), LocalDate.now());
        log.info("订单 {}", orderReportVO);
        Integer validOrderCount = orderReportVO.getValidOrderCount() != null ? orderReportVO.getValidOrderCount() : 0;
        log.info("有效订单 {}", validOrderCount);
        Double orderCompletionRate = orderReportVO.getOrderCompletionRate() != null ? orderReportVO.getOrderCompletionRate() : 0.0;
        log.info("订单完成率 {}", orderCompletionRate);
        
        Double unitPrice = 0.0;
        if (validOrderCount > 0) {
            unitPrice = (double) completedOrdersSumValue / (double) validOrderCount;
        }
        log.info("单价 {}", unitPrice);
        
        return BusinessDataVO.builder()
                .turnover(Double.valueOf(completedOrdersSumValue))
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .unitPrice(unitPrice)
                .build();
    }

    @Override
    public SetmealOverViewVO getOverviewSetmeals() {
        Long shopId = BaseContext.getShopId();
        log.info("获取店铺 {} 的套餐 overview", shopId);
        return SetmealOverViewVO.builder()
                .sold(setmealMapper.countStatus(StatusConstant.ENABLE, shopId))
                .discontinued(setmealMapper.countStatus(StatusConstant.DISABLE, shopId))
                .build();
    }

    @Override
    public DishOverViewVO getOverviewDish() {
        Long shopId = BaseContext.getShopId();
        log.info("获取店铺 {} 的菜品 overview", shopId);
        return DishOverViewVO.builder()
                .sold(dishMapper.countStatus(StatusConstant.ENABLE, shopId))
                .discontinued(dishMapper.countStatus(StatusConstant.DISABLE, shopId))
                .build();
    }

    @Override
    public OrderOverViewVO getOverviewOrders() {
        Long shopId = BaseContext.getShopId();
        log.info("获取店铺 {} 的订单 overview", shopId);
        return OrderOverViewVO.builder()
                .waitingOrders(workSpaceMapper.countStatus(Orders.builder().status(Orders.TO_BE_CONFIRMED).shopId(shopId).build()))
                .deliveredOrders(workSpaceMapper.countStatus(Orders.builder().status(Orders.CONFIRMED).shopId(shopId).build()))
                .completedOrders(workSpaceMapper.countStatus(Orders.builder().status(Orders.COMPLETED).shopId(shopId).build()))
                .cancelledOrders(workSpaceMapper.countStatus(Orders.builder().status(Orders.CANCELLED).shopId(shopId).build()))
                .allOrders(workSpaceMapper.countStatus(Orders.builder().shopId(shopId).build()))
                .build();
    }
}
