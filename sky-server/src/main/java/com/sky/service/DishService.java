package com.sky.service;

import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.result.PageResult;
import com.sky.vo.DishVO;

import java.util.List;

public interface DishService {

    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 管理端查询菜品列表（使用BaseContext.getShopId()）
     * @param categoryId 分类ID
     * @return 菜品列表
     */
    List<Dish> list(Long categoryId);

    /**
     * 用户端查询菜品列表（显式传递shopId）
     * @param categoryId 分类ID
     * @param shopId 店铺ID
     * @return 菜品列表
     */
    List<Dish> userDishList(Long categoryId, Long shopId);

    DishVO getById(long id);

    void update(long id, Integer status);

    void save(DishDTO dishDTO);

    void updateDish(DishDTO dishDTO);

    void deleteBatch(List<Long> ids);

    List<Dish> listDishes(Long categoryid);
}
