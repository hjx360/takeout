package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.context.BaseContext;
import com.sky.dto.ShopDTO;
import com.sky.entity.Favorites;
import com.sky.mapper.DishMapper;
import com.sky.mapper.FavoritesMapper;
import com.sky.mapper.ShopMapper;
import com.sky.result.PageResult;
import com.sky.service.FavoritesService;
import com.sky.vo.ShopVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.beans.beancontext.BeanContext;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class FavoritesServiceImpl implements FavoritesService {
    @Resource
    private FavoritesMapper favoritesMapper;
    @Resource
    private ShopMapper shopMapper;
    @Resource
    private DishMapper dishMapper;
    @Override
    public void addShopToFavorites(Favorites favorites) {
        favorites.setCreateTime(LocalDateTime.now());
        favorites.setUserId(BaseContext.getCurrentId());
        favoritesMapper.addShopToFavorites(favorites);



    }

    @Override
    public void deleteShopFromFavorites(Long shopId) {
        favoritesMapper.deleteShopFromFavorites(shopId,BaseContext.getCurrentId());

    }

    @Override
    public PageResult getFavorites(ShopDTO shopDTO) {
        PageHelper.startPage(shopDTO.getPage(), shopDTO.getPageSize());
        Page<ShopVo> shopVoList = shopMapper.getFavoritesShopVoList(BaseContext.getCurrentId());
        log.info("shopVoList={}", shopVoList);
        List<ShopVo> result = shopVoList.getResult();
        for (ShopVo shopVo : result) {
            shopVo.setDishes(dishMapper.getByshopid(shopVo.getId()));
        }
        PageResult pageResult = new PageResult(shopVoList.getTotal(), result);

        return pageResult;
    }
}
