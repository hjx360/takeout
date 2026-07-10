package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.entity.Employee;
import com.sky.entity.Shop;
import com.sky.vo.ShopInfo;
import com.sky.vo.ShopVo;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShopMapper {
    @Select("select id, name, logo, description, status, min_amount,address_detail,latitude,longitude from sky_take_out.shop")
    Page<ShopVo> getShopVoList();

    @Select("select id, name, logo, description, status, min_amount from sky_take_out.shop where name like concat('%',#{name},'%')")
    Page<ShopVo> search(String name);

    @Select("select id, name, logo, description, status, min_amount from sky_take_out.shop where id = #{id}")
    ShopVo getByshopid(Long shopId);

    @Select("select id, name, logo,description,address_detail,longitude,latitude,min_amount " +
            "from sky_take_out.shop where id = #{shopId}")
    ShopInfo getShopinfo(Long shopId);

    @Insert("insert into sky_take_out.shop (name, logo, description, status, " +
            " address_detail, create_time, update_time, latitude, longitude) " +
            "values (#{name}, #{logo}, #{description}, #{status}, " +
            "#{addressDetail}, #{createTime}, #{updateTime}, #{latitude}, #{longitude})")
    @Options(useGeneratedKeys = true, keyColumn = "id", keyProperty = "id")
    void insert(Shop shop);

    @Select("select  status from sky_take_out.shop where id = #{shopId}")
    Integer getStatusById(Long shopId);
    @Update("update sky_take_out.shop set status = #{status} where id = #{shopId}")
    void updateStatus(Long shopId, Integer status);
    @Select("select min_amount from sky_take_out.shop where id = #{shopId}")
    Integer getMinAmount(Long shopId);
    @Insert("insert into sky_take_out.employee (username, name, password, phone, sex, id_number, " +
            "status, create_time, update_time, create_user, update_user, shop_id, role) " +
            "values (#{username}, #{name}, #{password}, #{phone}, #{sex}, " +
            "#{idNumber}, #{status}, #{createTime}, #{updateTime}, #{createUser}, #{updateUser}, #{shopId}, #{role})")
    void insertEmployee(Employee employee);
    @Select("SELECT s.id, s.name, s.logo, s.description, s.status, s.min_amount " +
            "FROM sky_take_out.shop s " +
            "INNER JOIN sky_take_out.favorites f ON s.id = f.shop_id " +
            "WHERE f.user_id = #{currentId} " +
            "ORDER BY f.create_time DESC")
    Page<ShopVo> getFavoritesShopVoList(@Param("currentId") Long currentId);
}
