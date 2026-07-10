package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrderStatisticsDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    Page<Orders> queryOrders(@Param("dto") OrdersPageQueryDTO ordersPageQueryDTO, @Param("shopId") Long shopId);

    @Select("select count(id) from orders where status = #{status} and shop_id = #{shopId}")
    Integer countStatus(@Param("status") Integer toBeConfirmed, @Param("shopId") Long shopId);

    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    void updateStatus(Orders orders);

    List<Map<String, Object>> getOrdersSum(@Param("dto") OrderStatisticsDTO orderStatisticsDTO);

    List<GoodsSalesDTO> getSalesTop10(@Param("dto") OrderStatisticsDTO orderStatisticsDTO);

    void insert(Orders order);

    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);
    @Update("update orders set status = #{status},cancel_time = #{cancelTime},cancel_reason = #{cancelReason} where id = #{id}")
    void update(Orders orders);

    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);
    @Select("select shop_id from orders where id = #{id}")
    Integer getShopId(Long id);
}


