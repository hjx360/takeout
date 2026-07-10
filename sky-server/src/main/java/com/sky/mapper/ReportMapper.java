package com.sky.mapper;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface ReportMapper {
    @MapKey("day")
    @Select("select date(order_time) as day, sum(amount) as turnover " +
            "from orders " +
            "where order_time >= #{begintime} and order_time < #{endtime} " +
            "and status = #{status} " +
            "and shop_id = #{shopId} " +
            "group by date(order_time) " +
            "order by day")
    Map<String, Map<String, Object>> getturnovers(LocalDateTime begintime, LocalDateTime endtime, Long shopId, Integer status);
    @Select("select COALESCE(sum(amount), 0.00) " +
            "from orders " +
            "where order_time >= #{begintime} and order_time < #{endtime} " +
            "and status = #{status} " +
            "and shop_id = #{shopId}")
    Double getTotalTurnover(LocalDateTime begintime, LocalDateTime endtime, Long shopId, Integer status);




}