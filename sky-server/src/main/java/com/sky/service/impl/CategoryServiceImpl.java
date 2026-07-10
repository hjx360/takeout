package com.sky.service.impl;

import cn.hutool.core.lang.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;


import com.sky.service.CategoryService;
import com.sky.service.DishService;
import com.sky.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CategoryServiceImpl implements CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 缓存key前缀
    private static final String CACHE_CATEGORY_KEY = "cache:category:";
    private static final String CACHE_CATEGORY_LIST_KEY = "cache:category:list:";
    // 缓存过期时间
    private static final Long CACHE_CATEGORY_TTL = 30L;

    @Override
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        PageHelper.startPage(categoryPageQueryDTO.getPage(), categoryPageQueryDTO.getPageSize());
        Page<Category> page = categoryMapper.pageQuery(categoryPageQueryDTO, BaseContext.getShopId());
        long total = page.getTotal();
        List<Category> records = page.getResult();
        return new PageResult(total, records);
    }


    /**
     * 新增分类（先更新数据库，后删除缓存）
     * @param categoryDTO 分类DTO
     */
    @Override
    public void save(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);
        category.setShopId(BaseContext.getShopId());
        category.setStatus(StatusConstant.DISABLE);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        category.setCreateUser(BaseContext.getCurrentId());
        category.setUpdateUser(BaseContext.getCurrentId());
        // 先更新数据库
        categoryMapper.insert(category);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
        log.info("新增分类，删除缓存：{}", CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
    }

    /**
     * 删除分类（先更新数据库，后删除缓存）
     * @param id 分类ID
     */
    @Override
    public void delete(Long id) {
        List<Dish> dishList = dishMapper.getByCategoryId(id);
        if (dishList != null && !dishList.isEmpty()) {
            throw new RuntimeException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        }
        List<Setmeal> setmealList = setmealMapper.getByCategoryId(id);
        if (setmealList != null && !setmealList.isEmpty()) {
            throw new RuntimeException(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
        }
        // 先更新数据库
        categoryMapper.delete(id);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_CATEGORY_KEY + id);
        stringRedisTemplate.delete(CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
        log.info("删除分类，删除缓存：{} 和 {}", CACHE_CATEGORY_KEY + id, CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
    }

    /**
     * 更新分类（先更新数据库，后删除缓存）
     * @param categorydto 分类DTO
     */
    @Override
    public void update(CategoryDTO categorydto) {
        Category category = new Category();
        BeanUtils.copyProperties(categorydto, category);
        // 先更新数据库
        categoryMapper.update(category);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_CATEGORY_KEY + categorydto.getId());
        stringRedisTemplate.delete(CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
        log.info("更新分类，删除缓存：{} 和 {}", CACHE_CATEGORY_KEY + categorydto.getId(), CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
    }


    /**
     * 查询分类列表（使用缓存穿透防护）
     * @param type 分类类型
     * @return 分类列表
     */
    @Override
    public List<Category> list(Integer type) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数：key前缀、ID、TypeReference类型引用（处理泛型List<Category>）、数据库回调函数、过期时间、时间单位
        List<Category> list = cacheClient.queryWithPassThrough(
            CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId() + ":",
            type,
            new TypeReference<List<Category>>() {},
            t -> categoryMapper.list(t, BaseContext.getShopId()),
            CACHE_CATEGORY_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }

    /**
     * 用户端查询分类列表（使用缓存穿透防护）
     * @param type 分类类型
     * @param shopId 店铺ID
     * @return 分类列表
     */
    @Override
    public List<Category> userCategoryList(Integer type, Long shopId) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数：key前缀、ID、TypeReference类型引用（处理泛型List<Category>）、数据库回调函数、过期时间、时间单位
        List<Category> list = cacheClient.queryWithPassThrough(
            CACHE_CATEGORY_LIST_KEY + shopId + ":",
            type,
            new TypeReference<List<Category>>() {},
            t -> categoryMapper.list(t, shopId),
            CACHE_CATEGORY_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }

    /**
     * 启用或禁用分类（先更新数据库，后删除缓存）
     * @param status 状态
     * @param id 分类ID
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        Category category = Category.builder().status(status).id(id).build();
        // 先更新数据库
        categoryMapper.update(category);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_CATEGORY_KEY + id);
        stringRedisTemplate.delete(CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
        log.info("启用/禁用分类，删除缓存：{} 和 {}", CACHE_CATEGORY_KEY + id, CACHE_CATEGORY_LIST_KEY + BaseContext.getShopId());
    }
}

