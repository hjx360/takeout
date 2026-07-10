package com.sky.mapper;

import com.sky.annotation.AutoFill;
import com.sky.entity.SetmealDish;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SetmealDishMapper {

    List<SetmealDish> getSetmealDishByDishId(@Param("dishIds")List<Long> ids);


    void insertBatch(@Param("setmealDishes")List<SetmealDish> setmealDishes);

    @Delete("delete from setmeal_dish where setmeal_id = #{id}")
    void deleteBySetmealId(Long id);

    List<SetmealDish> getSetmealDishBySetmealId(@Param("setmealIds")List<Long> setmealIds);
}
