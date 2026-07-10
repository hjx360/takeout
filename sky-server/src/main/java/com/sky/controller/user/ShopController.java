package com.sky.controller.user;

import cn.hutool.core.util.StrUtil;
import com.sky.dto.AddShopDTO;
import com.sky.dto.ShopDTO;
import com.sky.mapper.ShopMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.ShopService;
import com.sky.utils.CacheClient;
import com.sky.vo.ShopInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Slf4j
public class ShopController {
    @Resource
    private ShopService shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private ShopMapper shopMapper;


    /**
     * 获取所有店铺信息
     *
     * @return
     */
    @GetMapping("/list")
    public Result<PageResult> list(ShopDTO shopDTO) {
        return Result.success(shopService.list(shopDTO));
    }

    /**
     * 搜索店铺或菜品
     *
     * @return
     */
    @GetMapping("/search")
    public Result<PageResult> search(ShopDTO shopDTO) {
        return Result.success(shopService.search(shopDTO));
    }

    /**
     * 获取店铺信息
     *
     * @return
     */
    @GetMapping("/getByshopid")
    public Result<ShopInfo> getByshopid(Long shopId) {
        return Result.success(shopService.getByshopid(shopId));
    }

    /**
     * 获取店铺营业状态
     * 直接使用StringRedisTemplate存储Integer，避免JSON序列化问题
     * @param shopId 店铺ID
     * @return 营业状态
     */
    @GetMapping("/status")
    public Result<Integer> getStatus(Long shopId) {
        if (shopId == null) {
            log.warn("获取店铺状态失败：shopId参数为null");
            return Result.error("店铺ID不能为空");
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
        stringRedisTemplate.opsForValue().set(key, String.valueOf(status), 60L, TimeUnit.MINUTES);
        
        return Result.success(status);
    }
    /**
     * 店铺入驻
     *
     * @return
     */
    @PostMapping("/add")
    public Result add(@RequestBody AddShopDTO addShopDTO) {
        shopService.add(addShopDTO);
        return Result.success();
    }
}
