package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SetmealMapper {

    Page<SetmealVO> page(@Param("dto") SetmealPageQueryDTO setmealPageQueryDTO,
                         @Param("shopId") Long shopId);

    @Update("update setmeal set status = #{status} where id = #{id}")
    void startOrStop(Integer status, Long id);

    @Select("select * from setmeal where id = #{id}")
    Setmeal querySetmeal(Long id);

    @Insert("insert into setmeal (name,category_id,price,status,create_time,update_time,create_user,update_user,shop_id,image,description) " +
            "values (#{name},#{categoryId},#{price},#{status},#{createTime},#{updateTime},#{createUser},#{updateUser},#{shopId},#{image},#{description})")
    @AutoFill(value = OperationType.INSERT)
    @Options(useGeneratedKeys = true, keyColumn = "id", keyProperty = "id")
    void insert(Setmeal setmeal);

    @AutoFill(value = OperationType.UPDATE)
    @Update("update setmeal set name = #{name},category_id = #{categoryId},price = #{price},status = #{status}," +
            "update_time = #{updateTime},update_user = #{updateUser},image = #{image},description = #{description} where id = #{id}")
    void update(Setmeal setmeal);

    @Delete("delete from setmeal where id = #{id}")
    void deleteSetmeal(Long id);

    @Select("select * from setmeal where category_id = #{id}")
    List<Setmeal> getByCategoryId(Long id);

    @Select("select count(id) from setmeal where status = #{status} and shop_id = #{shopId}")
    Integer countStatus(@Param("status") Integer enable, @Param("shopId") Long shopId);

    @Select("select * from setmeal where id = #{id}")
    Setmeal getSetmealById(Long setmealId);
}
