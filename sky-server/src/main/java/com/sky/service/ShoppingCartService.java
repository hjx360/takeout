package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.vo.ShopCartALLVo;

import java.util.List;

public interface ShoppingCartService {
    void add(ShoppingCartDTO shoppingCartDTO);

    List<ShoppingCart> listShoppingCart(Long shopId);

    void clean(Long shopId, Long id);

    List<ShopCartALLVo> listAll();
}
