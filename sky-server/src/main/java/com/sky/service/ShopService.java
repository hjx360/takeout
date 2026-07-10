package com.sky.service;

import com.sky.dto.AddShopDTO;
import com.sky.dto.ShopDTO;
import com.sky.result.PageResult;
import com.sky.vo.ShopInfo;
import com.sky.vo.ShopVo;

import java.util.List;

public interface ShopService {
    PageResult list(ShopDTO shopDTO);

    PageResult search(ShopDTO shopDTO);

    ShopInfo getByshopid(Long shopId);

    void add(AddShopDTO addShopDTO);
}
