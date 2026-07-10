package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.xiaoymin.knife4j.core.io.VFS;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.AddressBookService;
import com.sky.service.OrderService;
import com.sky.utils.RedisIdUtil;
import com.sky.vo.*;
import com.sky.websock.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;


import com.sky.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Resource
    private AddressBookMapper addressBookMapper;
    @Resource
    private ShoppingCartMapper shoppingCartMapper;
    @Resource
    private RedisIdUtil redisIdUtil;
    @Autowired
    private ShopMapper shopMapper;


    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        log.info("订单查询：{}", ordersPageQueryDTO);
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());

        Page<Orders> page = orderMapper.queryOrders(ordersPageQueryDTO, BaseContext.getShopId());
        log.info("查询结果：{}", page);
        List<Orders> list = page.getResult();
        if (CollectionUtils.isEmpty(list)) {
            return new PageResult(page.getTotal(), null);
        }
        List<OrderVO> orderVOList = new ArrayList<>();
        for (Orders orders : list) {
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(orders, orderVO);
            orderVO.setOrderDishes(getOrderDishes(orders));
            orderVOList.add(orderVO);
        }

        return new PageResult(page.getTotal(), orderVOList);


    }

    @Override
    public OrderStatisticsVO getStatistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        Long shopId = BaseContext.getShopId();
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED, shopId);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED, shopId);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS, shopId);
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    @Override
    public OrderVO getOrderDetails(Long id) {
        Orders orders = orderMapper.getById(id);
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailMapper.getByOrderId(id));
        return orderVO;
    }

    @Override
    public void takeOrder(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder().id(ordersConfirmDTO.getId()).status(Orders.CONFIRMED).build();
        log.info("订单确认：{}", orders);
        orderMapper.updateStatus(orders);
    }

    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        log.info("订单拒绝：{}", ordersRejectionDTO);
        Orders order = orderMapper.getById(ordersRejectionDTO.getId());
        if (order == null || order.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
//        //支付状态
//        Integer payStatus = ordersDB.getPayStatus();
//        if (payStatus == Orders.PAID) {
//            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
//        }
        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.updateStatus(orders);
    }

    @Override
    public void deliveryOrder(Long id) {
        log.info("订单派送：{}", id);
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.updateStatus(orders);
    }

    @Override
    public void complete(Long id) {
        log.info("订单完成：{}", id);
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.updateStatus(orders);
    }

    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        // 根据id查询订单
//        Orders ordersDB = orderMapper.getById(ordersCancelDTO.getId());
//
//        //支付状态
//        Integer payStatus = ordersDB.getPayStatus();
//        if (payStatus == 1) {
//            //用户已支付，需要退款
//            String refund = weChatPayUtil.refund(
//                    ordersDB.getNumber(),
//                    ordersDB.getNumber(),
//                    new BigDecimal(0.01),
//                    new BigDecimal(0.01));
//            log.info("申请退款：{}", refund);
//        }
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .status(Orders.CANCELLED)
                .cancelReason(ordersCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.updateStatus(orders);
    }

    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        log.info("用户下单：{}", ordersSubmitDTO);
        //地址簿不能为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new OrderBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //判断购物车是否为空

        List<ShoppingCart> shoppingCartList = shoppingCartMapper.getShoppingCartList(
                ShoppingCart.builder()
                        .userId(BaseContext.getCurrentId())
                        .shopId(ordersSubmitDTO.getShopId())
                        .build());
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new OrderBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        Integer minAmount = shopMapper.getMinAmount(ordersSubmitDTO.getShopId());
        if (minAmount == null || minAmount < 0 || ordersSubmitDTO.getAmount().compareTo(new BigDecimal(minAmount)) <= 0){
            throw new OrderBusinessException(MessageConstant.ORDER_AMOUNT_NOT_ENOUGH);
        }
        Orders order = new Orders();
        // 在 submit 方法开头，copyProperties 之前
        if (ordersSubmitDTO.getPackAmount() == null) {
            ordersSubmitDTO.setPackAmount(0);
        }
        if (ordersSubmitDTO.getTablewareNumber() == null) {
            ordersSubmitDTO.setTablewareNumber(0);
        }
        BeanUtils.copyProperties(ordersSubmitDTO, order);
        order.setNumber(redisIdUtil.nextId("order").toString());
        order.setOrderTime(LocalDateTime.now());
        order.setUserId(BaseContext.getCurrentId());
        order.setPayStatus(Orders.UN_PAID);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setAddress(ordersSubmitDTO.getAddress());
        order.setPhone(addressBook.getPhone());
        order.setUserName(addressBook.getConsignee());
        order.setConsignee(addressBook.getConsignee());
        orderMapper.insert(order);
        log.info("订单提交：{}", order);
        //向订单明细表中插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailMapper.insert(orderDetail);
        }
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();


        return orderSubmitVO;
    }

    @Override
    public void payment(OrdersPaymentDTO ordersPaymentDTO) {
        log.info("订单支付：{}", ordersPaymentDTO);
        Orders orders = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        AddressBook addressBook = addressBookMapper.getById(orders.getAddressBookId());
        if (orders.getStatus().equals(Orders.PENDING_PAYMENT)) {
            orders.setPayMethod(ordersPaymentDTO.getPayMethod());
            orders.setEstimatedDeliveryTime(ordersPaymentDTO.getEstimatedDeliveryTime());
            orders.setTablewareNumber(ordersPaymentDTO.getTablewareNumber());
            orders.setTablewareStatus(ordersPaymentDTO.getTablewareStatus());
            orders.setAddress(ordersPaymentDTO.getAddress());
            orders.setStatus(Orders.TO_BE_CONFIRMED);
            orders.setCheckoutTime(LocalDateTime.now());
            orders.setPayStatus(Orders.PAID);
            orders.setUserName(addressBook.getConsignee());
            orders.setPhone(addressBook.getPhone());
            orders.setConsignee(addressBook.getConsignee());
            orders.setAddressBookId(addressBook.getId());
            orders.setDeliveryFee(ordersPaymentDTO.getDeliveryFee());
            orderMapper.updateStatus(orders);

            Map map = new HashMap();
            map.put("type", 1);
            map.put("orderId", orders.getId());
            map.put("content", "订单号：" + orders.getNumber());
            WebSocketServer.sendToClient("shop:"+orders.getShopId(), JSON.toJSONString( map));
        }

    }

    @Override
    public OrderVO getOrderDetail(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        // 查询店铺信息
        if (orders.getShopId() != null) {
            ShopVo shop = shopMapper.getByshopid(orders.getShopId());
            if (shop != null) {
                orderVO.setShopName(shop.getName());
                orderVO.setShopImage(shop.getLogo());
            }
        }

        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    public void userCancelById(Long id) {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {

            throw new OrderBusinessException(MessageConstant.ORDER_CANCELLED);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    ordersDB.getNumber(), //商户订单号
//                    ordersDB.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额

            //支付状态修改为 退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 用户端订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        // 设置分页
        PageHelper.startPage(pageNum, pageSize);

        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);


        List<OrderVO> list = new ArrayList();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                log.info("订单：{}", orders);
                Long orderId = orders.getId();// 订单id

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                // 查询店铺信息
                if (orders.getShopId() != null) {
                    ShopVo shop = shopMapper.getByshopid(orders.getShopId());
                    if (shop != null) {
                        orderVO.setShopName(shop.getName());
                        orderVO.setShopImage(shop.getLogo());
                    }
                }

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    @Override
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        Integer shopId = orderMapper.getShopId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setShopId(Long.valueOf(shopId));
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    @Override
    public void reminder(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Map map = new HashMap();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号：" + orders.getNumber());
        WebSocketServer.sendToClient("shop:"+orders.getShopId(), JSON.toJSONString( map));
    }

    public String getOrderDishes(Orders orders) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());
        // 将每一条订单菜品信息拼接为字符串
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }
}
