package com.sky.service.impl;

import cn.hutool.core.lang.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.utils.CacheClient;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    
    // 缓存key前缀
    private static final String CACHE_DISH_KEY = "cache:dish:";
    private static final String CACHE_DISH_LIST_KEY = "cache:dish:list:";
    private static final String CACHE_DISH_VO_KEY = "cache:dish:vo:";
    // 缓存过期时间
    private static final Long CACHE_DISH_TTL = 30L;

    /**
     * 获取菜品列表
     *
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO, BaseContext.getShopId());
        log.info("page:{}", page);

        PageResult pageResult = new PageResult(page.getTotal(), page.getResult());
        log.info("pageResult:{}", pageResult);
        return pageResult;
    }

    /**
     * 管理端根据分类id查询菜品（使用缓存穿透防护）
     * 使用BaseContext.getShopId()获取店铺ID
     * 使用TypeReference支持泛型类型反序列化
     *
     * @param categoryId
     * @return
     */
    @Override
    public List<Dish> list(Long categoryId) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数：key前缀、ID、TypeReference类型（支持泛型）、数据库回调函数、过期时间、时间单位
        String key = CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":" + categoryId;
        List<Dish> list = cacheClient.queryWithPassThrough(
            CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":",
            categoryId,
            new TypeReference<List<Dish>>() {},
            id -> dishMapper.list(id, BaseContext.getShopId()),
            CACHE_DISH_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }

    /**
     * 用户端根据分类id查询菜品（使用缓存穿透防护）
     * 显式传递shopId参数，不依赖BaseContext
     * 使用TypeReference支持泛型类型反序列化
     *
     * @param categoryId 分类ID
     * @param shopId 店铺ID
     * @return 菜品列表
     */
    @Override
    public List<Dish> userDishList(Long categoryId, Long shopId) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数：key前缀、ID、TypeReference类型（支持泛型）、数据库回调函数、过期时间、时间单位
        // 注意：使用传入的shopId构建缓存key，确保数据隔离
        List<Dish> list = cacheClient.queryWithPassThrough(
            CACHE_DISH_LIST_KEY + shopId + ":",
            categoryId,
            new TypeReference<List<Dish>>() {},
            id -> dishMapper.list(id, shopId),
            CACHE_DISH_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }

    /**
     * 根据ID查询菜品详情（使用缓存穿透防护）
     * 使用TypeReference支持类型反序列化
     * @param id 菜品ID
     * @return 菜品VO
     */
    @Override
    public DishVO getById(long id) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数：key前缀、ID、TypeReference类型、数据库回调函数、过期时间、时间单位
        DishVO dishVO = cacheClient.queryWithPassThrough(
            CACHE_DISH_VO_KEY,
            id,
            new TypeReference<DishVO>() {},
            dishId -> {
                DishVO vo = dishMapper.getById(dishId);
                if (vo != null) {
                    vo.setFlavors(dishMapper.getFlavors(dishId));
                }
                return vo;
            },
            CACHE_DISH_TTL,
            TimeUnit.MINUTES
        );
        log.info("dishVO:{}", dishVO);
        return dishVO;
    }

    /**
     * 更新菜品状态（先更新数据库，后删除缓存）
     * @param id 菜品ID
     * @param status 状态
     */
    @Override
    public void update(long id, Integer status) {
        Dish dish = Dish.builder()
                .id(id)
                .status(status)
                .build();
        // 先更新数据库
        dishMapper.update(dish);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_DISH_KEY + id);
        stringRedisTemplate.delete(CACHE_DISH_VO_KEY + id);
        // 删除菜品列表缓存
        Dish dishInfo = dishMapper.getDishById(id);
        if (dishInfo != null && dishInfo.getCategoryId() != null) {
            stringRedisTemplate.delete(CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":" + dishInfo.getCategoryId());
        }
        log.info("更新菜品状态，删除缓存：{}", CACHE_DISH_KEY + id);
    }

    /**
     * 新增菜品（先更新数据库，后删除缓存）
     * @param dishDTO 菜品DTO
     */
    @Transactional
    @Override
    public void save(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dish.setShopId(BaseContext.getShopId());
        // 先更新数据库
        dishMapper.insertDish(dish);
        List<DishFlavor> flavors = dishDTO.getFlavors();
        for (DishFlavor flavor : flavors) {
            flavor.setDishId(dish.getId());
        }
        dishMapper.insertDishFlavor(flavors);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":" + dishDTO.getCategoryId());
        log.info("新增菜品，删除缓存：{}", CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":" + dishDTO.getCategoryId());
    }

    /**
     * 更新菜品（先更新数据库，后删除缓存）
     * @param dishDTO 菜品DTO
     */
    @Override
    @Transactional
    public void updateDish(DishDTO dishDTO) {
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        // 先更新数据库
        dishMapper.updateDish(dish);
        dishMapper.deleteDishFlavor(dishDTO.getId());
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dish.getId());
            }
            dishMapper.insertDishFlavor(flavors);
        }
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_DISH_KEY + dishDTO.getId());
        stringRedisTemplate.delete(CACHE_DISH_VO_KEY + dishDTO.getId());
        stringRedisTemplate.delete(CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":" + dishDTO.getCategoryId());
        log.info("更新菜品，删除缓存：{}、{}和{}", CACHE_DISH_KEY + dishDTO.getId(), CACHE_DISH_VO_KEY + dishDTO.getId(), CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":" + dishDTO.getCategoryId());
    }

    /**
     * 批量删除菜品（先更新数据库，后删除缓存）
     * @param ids 菜品ID列表
     */
    @Override
    public void deleteBatch(List<Long> ids) {
        for (Long id : ids) {
            DishVO dish = dishMapper.getById(id);
            if (dish.getStatus() != StatusConstant.DISABLE) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }

        }
        List<SetmealDish> setmealDishList = setmealDishMapper.getSetmealDishByDishId(ids);
        if (setmealDishList != null && setmealDishList.size() > 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }
        // 先更新数据库
        for (Long id : ids) {
            dishMapper.deleteDish(id);
            dishMapper.deleteDishFlavor(id);
            // 后删除缓存
            stringRedisTemplate.delete(CACHE_DISH_KEY + id);
            stringRedisTemplate.delete(CACHE_DISH_VO_KEY + id);
            Dish dishInfo = dishMapper.getDishById(id);
            if (dishInfo != null && dishInfo.getCategoryId() != null) {
                stringRedisTemplate.delete(CACHE_DISH_LIST_KEY + BaseContext.getShopId() + ":" + dishInfo.getCategoryId());
            }
        }
        log.info("批量删除菜品，删除缓存");
    }

    /**
     * 查询菜品列表（使用缓存穿透防护）
     * 使用TypeReference支持泛型类型反序列化
     * @param categoryid 分类ID
     * @return 菜品列表
     */
    @Override
    public List<Dish> listDishes(Long categoryid) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数：key前缀、ID、TypeReference类型（支持泛型）、数据库回调函数、过期时间、时间单位
        List<Dish> list = cacheClient.queryWithPassThrough(
            CACHE_DISH_LIST_KEY,
            categoryid,
            new TypeReference<List<Dish>>() {},
            id -> dishMapper.listDishes(id),
            CACHE_DISH_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }
}