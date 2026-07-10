package com.sky.mapper;

import com.sky.entity.Favorites;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FavoritesMapper {
    @Insert("insert into favorites (user_id, shop_id,create_time) values (#{userId}, #{shopId}, #{createTime})")
    void addShopToFavorites(Favorites favorites);
    @Delete("delete from favorites where shop_id = #{shopId} and user_id = #{currentId}")
    void deleteShopFromFavorites(Long shopId, Long currentId);
}
