package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CategoryMapper {


    Page<Category> pageQuery(@Param("dto") CategoryPageQueryDTO categoryPageQueryDTO, @Param("shopId") Long shopId);


    @Update("update category set status = #{status} where id = #{id}")
    void startOrStop(Integer status, Long id);
    @AutoFill(value = OperationType.INSERT)
        @Insert("insert into category (type, name, sort, status, create_time, update_time, create_user,update_user, shop_id) " +
            "VALUES (#{type}, #{name}, #{sort}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser}, #{shopId})")
    void insert(Category category);
    @Delete("delete from category where id = #{id}")
    void delete(Long id);
    @AutoFill(value = OperationType.UPDATE)
    void update(Category category);

    List<Category> list(Integer type, Long shopId);
}
