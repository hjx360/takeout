package com.sky.service.impl;

import cn.hutool.core.lang.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.PasswordConstant;
import com.sky.dto.AddShopDTO;
import com.sky.dto.ShopDTO;
import com.sky.entity.Dish;
import com.sky.entity.Employee;
import com.sky.entity.Shop;
import com.sky.mapper.DishMapper;
import com.sky.mapper.ShopMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.service.ShopService;
import com.sky.utils.CacheClient;
import com.sky.vo.ShopInfo;
import com.sky.vo.ShopVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ShopServiceImpl implements ShopService {
    @Resource
    private ShopMapper shopMapper;
    @Resource
    private DishMapper dishMapper;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 缓存key前缀
    private static final String CACHE_SHOP_KEY = "cache:shop:";
    private static final String CACHE_SHOP_INFO_KEY = "cache:shop:info:";
    // 缓存过期时间
    private static final Long CACHE_SHOP_TTL = 30L;
    @Override
    public PageResult list(ShopDTO shopDTO) {
        PageHelper.startPage(shopDTO.getPage(), shopDTO.getPageSize());
        Page<ShopVo> shopVoList = shopMapper.getShopVoList();
        log.info("shopVoList={}", shopVoList);
        List<ShopVo> result = shopVoList.getResult();
        for (ShopVo shopVo : result) {
            shopVo.setDishes(dishMapper.getByshopid(shopVo.getId()));
        }
        PageResult pageResult = new PageResult(shopVoList.getTotal(), result);

        return pageResult;
    }

    @Override
    public PageResult search(ShopDTO shopDTO) {
        PageHelper.startPage(shopDTO.getPage(), shopDTO.getPageSize());
        Page<ShopVo> shopVoList = shopMapper.search(shopDTO.getName());
        List<Dish> dishes = dishMapper.getByDishName(shopDTO.getName());
        List<ShopVo> result = shopVoList.getResult();
        log.info("shopVoList={}", shopVoList);

        // 1. 为通过店铺名称搜索到的店铺设置菜品列表
        for (ShopVo shopVo : result) {
            shopVo.setDishes(new ArrayList<>(dishMapper.getByshopid(shopVo.getId())));
        }

        // 2. 通过菜品名称搜索到的菜品，按店铺ID分组
        Map<Long, List<Dish>> dishMap = dishes.stream()
                .collect(Collectors.groupingBy(Dish::getShopId));

        for (Map.Entry<Long, List<Dish>> entry : dishMap.entrySet()) {
            Long shopId = entry.getKey();
            List<Dish> matchedDishes = entry.getValue();

            // 3. 查找该菜品所属店铺是否已在结果列表中
            ShopVo existingShopVo = result.stream()
                    .filter(shop -> shop.getId().equals(shopId))
                    .findFirst()
                    .orElse(null);

            if (existingShopVo != null) {
                // 4. 店铺已存在，追加匹配菜品到dishes列表中
                existingShopVo.getDishes().addAll(matchedDishes);
            } else {
                // 5. 店铺不存在，新增该店铺记录
                ShopVo shopVo = shopMapper.getByshopid(shopId);
                shopVo.setDishes(matchedDishes);
                result.add(shopVo);
            }
        }

        return new PageResult(shopVoList.getTotal(), result);
    }

    /**
     * 根据店铺ID查询店铺信息（使用缓存穿透防护）
     * @param shopId 店铺ID
     * @return 店铺信息
     */
    @Override
    public ShopInfo getByshopid(Long shopId) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数：key前缀、ID、TypeReference类型引用、数据库回调函数、过期时间、时间单位
        ShopInfo shopInfo = cacheClient.queryWithPassThrough(
            CACHE_SHOP_INFO_KEY,
            shopId,
            new TypeReference<ShopInfo>() {},
            id -> shopMapper.getShopinfo(id),
            CACHE_SHOP_TTL,
            TimeUnit.MINUTES
        );
        return shopInfo;
    }

    @Override
    @Transactional
    public void add(AddShopDTO addShopDTO) {
        Shop shop = new Shop();
        Employee employee = new Employee();
        BeanUtils.copyProperties(addShopDTO, shop);

        shop.setStatus(0);
        shop.setCreateTime(LocalDateTime.now());
        shop.setUpdateTime(LocalDateTime.now());
        shopMapper.insert(shop);
        BeanUtils.copyProperties(addShopDTO, employee);
        employee.setShopId(shop.getId());
        employee.setRole(0L);
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());
        employee.setStatus(1);
        employee.setPassword(DigestUtils.md5DigestAsHex(addShopDTO.getPassword().getBytes()));
        shopMapper.insertEmployee(employee);
    }

}
