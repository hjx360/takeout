package com.sky.mapper;

import com.sky.entity.User;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface UserMapper {

    Integer getCount(Map<String, LocalDateTime> map);

    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    @Insert("insert into user (openid, name, phone, sex, id_number, avatar, create_time) " +
            "values (#{openid}, #{name}, #{phone}, #{sex}, #{idNumber}, #{avatar}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(User user);
    @Update("update user set name = #{name}, phone = #{phone}, sex = #{sex}, id_number = #{idNumber}, " +
            "avatar = #{avatar}, create_time = #{createTime} where id = #{id}")
    void update(User user);
    @Select("select id, name, phone, sex, id_number, avatar, create_time,birthday from user where id = #{id}")
    User getById(Long userId);

    void updateUser(User user);
}
