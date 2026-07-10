package com.sky.service;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.result.PageResult;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;

import java.util.List;

public interface SetmealService {
    PageResult page(SetmealPageQueryDTO setmealPageQueryDTO);

    void startOrStop(Integer status, Long id);

    void save(SetmealDTO setmealDTO);

    SetmealVO getById(Long id);

    void update(SetmealDTO setmealDTO);

    void delete(List<Long> ids);

    /**
     * 管理端查询套餐列表（使用BaseContext.getShopId()）
     * @param categoryId 分类ID
     * @return 套餐列表
     */
    List<Setmeal> list(Long categoryId);

    /**
     * 用户端查询套餐列表（显式传递shopId）
     * @param categoryId 分类ID
     * @param shopId 店铺ID
     * @return 套餐列表
     */
    List<Setmeal> userSetmealList(Long categoryId, Long shopId);

    List<DishItemVO> getDishItemVOListById(Long id);
}
