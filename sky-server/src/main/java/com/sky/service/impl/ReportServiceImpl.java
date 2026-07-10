package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.OrderStatisticsDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ReportMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.beans.beancontext.BeanContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public TurnoverReportVO turnoverStatistics(LocalDate begin, LocalDate end) {
        LocalDateTime begintime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endtime = LocalDateTime.of(end, LocalTime.MAX);
        Map<?, Map<String, Object>> map = reportMapper.getturnovers(begintime, endtime, BaseContext.getShopId(), Orders.COMPLETED);

        if (map == null || map.isEmpty()) {
            return TurnoverReportVO.builder()
                    .dateList("")
                    .turnoverList("")
                    .build();
        }

        // 提取日期列表（key可能是java.sql.Date或String，需要统一转换为String）
        List<String> dateList = new ArrayList<>();
        for (Object key : map.keySet()) {
            if (key instanceof java.sql.Date) {
                dateList.add(((java.sql.Date) key).toString());
            } else {
                dateList.add(key.toString());
            }
        }
        Collections.sort(dateList); // 按日期排序

        // 提取营业额列表
        List<String> turnoverList = dateList.stream()
                .map(date -> {
                    // 需要根据原始key类型查找对应的value
                    Map<String, Object> row = null;
                    for (Map.Entry<?, Map<String, Object>> entry : map.entrySet()) {
                        Object entryKey = entry.getKey();
                        String entryKeyStr = entryKey instanceof java.sql.Date 
                                ? ((java.sql.Date) entryKey).toString() 
                                : entryKey.toString();
                        if (entryKeyStr.equals(date)) {
                            row = entry.getValue();
                            break;
                        }
                    }
                    if (row == null) return "0";
                    Object turnover = row.get("turnover");
                    return turnover != null ? String.valueOf(turnover) : "0";
                })
                .collect(Collectors.toList());

        return TurnoverReportVO.builder()
                .dateList(String.join(",", dateList))
                .turnoverList(String.join(",", turnoverList))
                .build();
    }

//    @Override
//    public UserReportVO userStatistics(LocalDate begin, LocalDate end) {
//        List<LocalDate> dateList = new ArrayList<>();
//        dateList.add(begin);
//        while (!begin.equals(end)) {
//            begin = begin.plusDays(1);
//            dateList.add(begin);
//        }
//        //日期，以逗号分隔，例如：2022-10-01,2022-10-02,2022-10-03
//        String dateListStr = dateList.stream()
//                .map(String::valueOf)
//                .collect(Collectors.joining(","));
//
//        //添加时间00:00:00
//
//        List<Integer> newUserList = new ArrayList<>();
//        List<Integer> totalUserList = new ArrayList<>();
//        for (LocalDate date : dateList) {
//            LocalDateTime begintime = LocalDateTime.of(date, LocalTime.MIN);
//            LocalDateTime endtime = LocalDateTime.of(date, LocalTime.MAX);
//            Map<String, LocalDateTime> map = new HashMap<>();
//            map.put("endtime", endtime);
//            Integer totalUser = userMapper.getCount(map);
//            map.put("begintime", begintime);
//            Integer newUser = userMapper.getCount(map);
//
//            newUserList.add(newUser);
//            totalUserList.add(totalUser);
//        }
//        UserReportVO userReportVO = UserReportVO.builder()
//                .dateList(dateListStr)
//                .newUserList(newUserList.stream().map(String::valueOf).collect(Collectors.joining(",")))
//                .totalUserList(totalUserList.stream().map(String::valueOf).collect(Collectors.joining(",")))
//                .build();
//        return userReportVO;
//    }

    @Override
    public OrderReportVO ordersStatistics(LocalDate begin, LocalDate end) {
        LocalDateTime begintime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endtime = LocalDateTime.of(end, LocalTime.MAX);

        OrderStatisticsDTO dto = OrderStatisticsDTO.builder()
                .begintime(begintime)
                .endtime(endtime)
                .shopId(BaseContext.getShopId())
                .build();

        // 全部订单
        List<Map<String, Object>> rawAll = orderMapper.getOrdersSum(dto);
        // 已完成订单
        List<Map<String, Object>> rawCompleted = orderMapper.getOrdersSum(
                OrderStatisticsDTO.builder()
                        .begintime(begintime)
                        .endtime(endtime)
                        .shopId(BaseContext.getShopId())
                        .status(Orders.COMPLETED)
                        .build()
        );

        // 转换成按日期排序的 Map<日期, 订单数>
        Map<String, Integer> countMap = toDateCountMap(rawAll);
        Map<String, Integer> completedMap = toDateCountMap(rawCompleted);

        if (countMap.isEmpty()) {
            return OrderReportVO.builder()
                    .dateList("")
                    .orderCountList("")
                    .validOrderCountList("")
                    .totalOrderCount(0)
                    .validOrderCount(0)
                    .build();
        }

        String dateList = String.join(",", countMap.keySet());
        String orderCountList = countMap.values().stream()
                .map(String::valueOf).collect(Collectors.joining(","));
        String validOrderCountList = completedMap.values().stream()
                .map(String::valueOf).collect(Collectors.joining(","));
        int totalOrderCount = countMap.values().stream().reduce(0, Integer::sum);
        int validOrderCount = completedMap.values().stream().reduce(0, Integer::sum);

        return OrderReportVO.builder()
                .dateList(dateList)
                .orderCountList(orderCountList)
                .validOrderCountList(validOrderCountList)
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(validOrderCount * 1.0 / totalOrderCount)
                .build();
    }

    // 辅助方法：将 List<Map<String,Object>> 转成 LinkedHashMap 保持日期顺序
    private Map<String, Integer> toDateCountMap(List<Map<String, Object>> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String day = row.get("day").toString();
                // totalOrderCount 可能是 Long 或 BigInteger，统一转为 Integer
                Integer count = ((Number) row.get("totalOrderCount")).intValue();
                result.put(day, count);
            }
        }
        return result;
    }

    @Override
    public SalesTop10ReportVO top10(LocalDate begin, LocalDate end) {
        LocalDateTime begintime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endtime = LocalDateTime.of(end, LocalTime.MAX);
        List<GoodsSalesDTO> list = orderMapper.getSalesTop10(OrderStatisticsDTO.builder()
                .begintime(begintime)
                .endtime(endtime)
                .shopId(BaseContext.getShopId())
                .status(Orders.COMPLETED)
                .build());
        String nameList = list.stream()
                .map(GoodsSalesDTO::getName)
                .collect(Collectors.joining(","));
        String numberList = list.stream()
                .map(GoodsSalesDTO::getNumber)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        SalesTop10ReportVO salesTop10ReportVO = SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();

        return salesTop10ReportVO;
    }
}
