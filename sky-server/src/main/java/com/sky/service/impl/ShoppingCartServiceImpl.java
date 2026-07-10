package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.Shop;
import com.sky.entity.ShoppingCart;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShopMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.ShopCartALLVo;
import com.sky.vo.ShopVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Resource
    private ShoppingCartMapper shoppingCartMapper;
    @Resource
    private DishMapper dishMapper;
    @Resource
    private SetmealMapper setmealMapper;
    @Autowired
    private ShopMapper shopMapper;

    @Override
    public void add(ShoppingCartDTO shoppingCartDTO) {
        //获取当前用户在当前店铺中购物车列表
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.getShoppingCartList(shoppingCart);
        log.info("购物车列表：{}", shoppingCartList);
        if (shoppingCartList != null && shoppingCartList.size() > 0) {
            shoppingCart = shoppingCartList.get(0);
            shoppingCart.setNumber(shoppingCart.getNumber() + 1);
            shoppingCartMapper.update(shoppingCart);

        } else {
            if (shoppingCartDTO.getDishId() != null) {

                Dish dish = dishMapper.getDishById(shoppingCartDTO.getDishId());
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());
                shoppingCartMapper.insert(shoppingCart);

            } else {
                Setmeal setmeal = setmealMapper.getSetmealById(shoppingCartDTO.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
                shoppingCart.setNumber(1);
                shoppingCart.setCreateTime(LocalDateTime.now());
                shoppingCartMapper.insert(shoppingCart);


            }
        }
    }

    @Override
    public List<ShoppingCart> listShoppingCart(Long shopId) {
        Long userId = BaseContext.getCurrentId();
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        shoppingCart.setShopId(shopId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.getShoppingCartList(shoppingCart);
        return shoppingCartList;
    }

    @Override
    public void clean(Long shopId, Long id) {
        shoppingCartMapper.clean(id, BaseContext.getCurrentId(), shopId);
    }

    @Override
    public List<ShopCartALLVo> listAll() {
        Long userId = BaseContext.getCurrentId();
        List<ShopCartALLVo> shopCartALLVos = new ArrayList<>();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.listAll(userId);
        //判断shoppingCartList是否为空
        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            return Collections.emptyList();
        }
        //查询购物车列表中不同的店铺id
        List<Long> shopIds = shoppingCartList.stream().map(ShoppingCart::getShopId).distinct().collect(Collectors.toList());

        for (Long shopId : shopIds){
            List<ShoppingCart> carts = shoppingCartList.stream()
                    .filter(cart -> cart.getShopId().equals(shopId))
                    .collect(Collectors.toList());
            ShopVo shopVo = shopMapper.getByshopid(shopId);
            ShopCartALLVo shopCartALLVo = new ShopCartALLVo();
            BeanUtils.copyProperties(shopVo, shopCartALLVo);
            shopCartALLVo.setShoppingCarts(carts);
            shopCartALLVos.add(shopCartALLVo);
        }
        return shopCartALLVos;
    }
}
