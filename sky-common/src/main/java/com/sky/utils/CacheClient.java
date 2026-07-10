package com.sky.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.sky.entity.RedisData;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private static final long CACHE_NULL_TTL = 60L ;//缓存空数据的过期时间，默认60s
    private static final String LOCK_SHOP_KEY =  "lock:shop:";
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //通过构造函数进行注入
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     * @param value
     * @param key
     * @param time
     * @param unit
     */
    public void set(Object value, String key, Long time, TimeUnit unit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);

    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓
     * @param value
     * @param key
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(Object value, String key, Long time, TimeUnit unit){
        //设置逻辑过期时间
        RedisData redisData = new com.sky.entity.RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
    /**
     * 缓存穿透
     * @return
     */
    public <R, ID> R queryWithPassThrough(String pre, ID id,
                                          TypeReference<R> typeRef,
                                          Function<ID, R> dbFallback,
                                          Long time, TimeUnit unit){

        String key = pre + id;
        //1.从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否命中
        if (StrUtil.isNotBlank(json) && !json.equals("{}")) {
            //3.命中 返回商铺信息
            return JSONUtil.toBean(json, typeRef, false);
        }
        //判断是否为null值或空JSON对象
        if(json != null){
            return null;
        }
        //4.未命中 根据id查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            //5.将空值存入redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            //5.存在 返回
            return null;
        }
        //6.存在 写入redis 返回商品信息
        this.set(r,key,time,unit);
        return r;
    }

    /**
     * 获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 利用逻辑过期时间解决缓存击穿
     * @return
     */
    public <R,ID> R queryWithLogicalExpire(String pre,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit){
        String key = pre + id;
        //1.从redis中查询缓存
        String Json = stringRedisTemplate.opsForValue().get(key);
        //2.不存在 直接返回空（不是热点数据）
        if(StrUtil.isBlank(Json)){
            return null;
        }
        //3.存在 需要先把Json反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        JSONObject data =(JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data,type);

        //4.判断缓存是否过期
        LocalDateTime expireTime = redisData.getExpireTime();

        if(expireTime.isAfter(LocalDateTime.now())){
            //5.未过期 返回商铺信息
            return r;
        }
        //6.已过期 缓存重建
        //6.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isTryLock = tryLock(lockKey);
        //6.2判断是否获取成功
        if (isTryLock) {
            //6.3成功

            //再次检查缓存中的数据是否过期,如果没有过期，无需重建
            //1.从redis中查询缓存
            Json = stringRedisTemplate.opsForValue().get(key);
            //2.不存在 直接返回空（不是热点数据）
            if(StrUtil.isBlank(Json)){
                return null;
            }
            //3.存在 需要先把Json反序列化为对象
            redisData = JSONUtil.toBean(Json, RedisData.class);
            data =(JSONObject) redisData.getData();
            r = JSONUtil.toBean(data, type);
            //4.判断缓存是否过期
            expireTime = redisData.getExpireTime();
            if(expireTime.isAfter(LocalDateTime.now())){
                //5.未过期 返回商铺信息
                return r;
            }
            //还过期 开启独立线程 缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //先查数据库
                    R r1 = dbFallback.apply(id);
                    //再写入Redis
                    this.setWithLogicalExpire(r1,key,time,unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });


        }
        //6.4返回商铺信息
        return r;
    }

}