package com.sky.controller.admin;

import cn.hutool.core.util.StrUtil;
import com.sky.context.BaseContext;
import com.sky.mapper.ShopMapper;
import com.sky.result.Result;
import com.sky.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/admin/shop")
@Slf4j
public class ShopController {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ShopMapper shopMapper;
    @Autowired
    private CacheClient cacheClient;


    /**
     * 获取店铺营业状态
     * 直接使用StringRedisTemplate存储Integer，避免JSON序列化问题
     * @return 营业状态
     */
    @GetMapping("/status")
    public Result<Integer> getStatus(){
        Long shopId = BaseContext.getShopId();
        if (shopId == null) {
            log.warn("获取店铺状态失败：shopId为null");
            return Result.error("店铺ID不存在");
        }
        
        String key = "admin:SHOP_STATUS:" + shopId;
        // 1.从Redis查询缓存
        String statusStr = stringRedisTemplate.opsForValue().get(key);
        
        if (StrUtil.isNotBlank(statusStr)) {
            // 2.命中缓存，直接返回
            return Result.success(Integer.parseInt(statusStr));
        }
        
        // 3.未命中，查询数据库
        Integer status = shopMapper.getStatusById(shopId);
        if (status == null) {
            // 缓存空值防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", 60L, TimeUnit.MINUTES);
            return Result.success(null);
        }
        
        // 4.写入缓存（直接存储数字字符串，不使用JSON序列化）
        stringRedisTemplate.opsForValue().set(key, String.valueOf(status), 720L, TimeUnit.MINUTES);
        
        return Result.success(status);
    }
    @PutMapping("/{status}")
    public Result changeStatus(@PathVariable Integer status){
        Long shopId = BaseContext.getShopId();
        if (shopId == null) {
            log.warn("修改店铺状态失败：shopId为null");
            return Result.error("店铺ID不存在");
        }
        
        // 修改数据库中的数据
        shopMapper.updateStatus(shopId, status);
        // 删除缓存（先更新数据库，后删除缓存）
        stringRedisTemplate.delete("admin:SHOP_STATUS:" + shopId);
        return Result.success();
    }
}
