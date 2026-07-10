package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.*;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

@Mapper
public interface DishMapper {


    Page<DishVO> pageQuery(@Param("dto") DishPageQueryDTO dishPageQueryDTO,
                           @Param("shopId") Long shopId);


    List<Dish> list(Long categoryId, Long shopId);

    @Select("select * from dish  where id = #{id}")
    DishVO getById(long id);

    @Select("select * from dish_flavor where dish_id = #{id}")
    List<DishFlavor> getFlavors(long id);

    @Update("update dish set status = #{status} where id = #{id}")
    void update(Dish dish);

    @AutoFill(value = OperationType.INSERT)
    @Options(useGeneratedKeys = true, keyColumn = "id", keyProperty = "id")
    @Insert("insert into dish (name,category_id,price,status,create_time,update_time,create_user,update_user,shop_id,image,description) " +
            "values (#{name},#{categoryId},#{price},#{status}," +
            "#{createTime},#{updateTime},#{createUser},#{updateUser},#{shopId},#{image},#{description})")
    void insertDish(Dish dish);


    void insertDishFlavor(List<DishFlavor> flavors);

    @Update("update dish " +
            "set name = #{name},category_id = #{categoryId},price = #{price}," +
            "status = #{status},update_time = #{updateTime},update_user = #{updateUser},description =#{description},image =#{image} where id = #{id}")
    @AutoFill(value = OperationType.UPDATE)
    void updateDish(Dish dish);

    @Delete("delete from dish_flavor where dish_id = #{id}")
    void deleteDishFlavor(Long id);

    @Delete("delete from dish where id = #{id}")
    void deleteDish(long id);

    @Select("select * from dish where category_id = #{id}")
    List<Dish> getByCategoryId(Long id);

    @Select("select count(id) from dish where status = #{status} and shop_id = #{shopId}")
    Integer countStatus(@Param("status") Integer disable, @Param("shopId") Long shopId);

    @Select("select id, name,  price, image,shop_id from dish where shop_id = #{id} limit 3")
    List<Dish> getByshopid(Long id);

    @Select("select id, name,  price, image,shop_id from dish where name like concat('%',#{name},'%')")
    List<Dish> getByDishName(@Param("name") String name);

    @Select("select id, name, category_id, price, image, description, shop_id from dish where category_id = #{categoryid} and status=1")
    List<Dish> listDishes(Long categoryid);
    @Select("select * from dish where id = #{id}")
    Dish getDishById(Long id);
}
