package com.sky.service;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.vo.*;

public interface OrderService {
    PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO);

    OrderStatisticsVO getStatistics();

    OrderVO getOrderDetails(Long id);

    void takeOrder(OrdersConfirmDTO ordersConfirmDTO);

    void rejection(OrdersRejectionDTO ordersRejectionDTO);

    void deliveryOrder(Long id);

    void complete(Long id);

    void cancel(OrdersCancelDTO ordersCancelDTO);

    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);

    void payment(OrdersPaymentDTO ordersPaymentDTO);

    OrderVO getOrderDetail(Long id);

    void userCancelById(Long id);

    PageResult pageQuery4User(int page, int pageSize, Integer status);

    void repetition(Long id);

    void reminder(Long id);
}
