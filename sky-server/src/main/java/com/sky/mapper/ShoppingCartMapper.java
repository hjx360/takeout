package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import com.sky.vo.ShopCartALLVo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    List<ShoppingCart> getShoppingCartList(ShoppingCart shoppingCart);
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void update(ShoppingCart shoppingCart);
    @Insert("insert into shopping_cart (name, image, dish_id, setmeal_id, dish_flavor, number, amount, create_time," +
            "shop_id,user_id) " +
            "values (#{name}, #{image}, #{dishId}, #{setmealId}, #{dishFlavor}, #{number}, #{amount}, #{createTime}," +
            " #{shopId}, #{userId})")
    void insert(ShoppingCart shoppingCart);

    void clean(@Param("id") Long id, @Param("currentId") Long currentId, @Param("shopId") Long shopId);

    void insertBatch(List<ShoppingCart> shoppingCartList);
    @Select("select * from shopping_cart where user_id = #{userId}")
    List<ShoppingCart> listAll(Long userId);
}
