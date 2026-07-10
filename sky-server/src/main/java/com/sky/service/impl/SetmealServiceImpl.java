package com.sky.service.impl;

import cn.hutool.core.lang.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.BaseException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.utils.CacheClient;
import com.sky.vo.DishItemVO;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.beans.beancontext.BeanContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 缓存key前缀
    private static final String CACHE_SETMEAL_KEY = "cache:setmeal:";
    private static final String CACHE_SETMEAL_VO_KEY = "cache:setmeal:vo:";
    private static final String CACHE_SETMEAL_LIST_KEY = "cache:setmeal:list:";
    // 缓存过期时间
    private static final Long CACHE_SETMEAL_TTL = 30L;

    @Override
    public PageResult page(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.page(setmealPageQueryDTO, BaseContext.getShopId());
        long total = page.getTotal();
        return new PageResult(total, page.getResult());
    }

    /**
     * 启用或禁用套餐（先更新数据库，后删除缓存）
     * @param status 状态
     * @param id 套餐ID
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        // 先更新数据库
        setmealMapper.startOrStop(status, id);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_SETMEAL_KEY + id);
        stringRedisTemplate.delete(CACHE_SETMEAL_VO_KEY + id);
        log.info("启用/禁用套餐，删除缓存：{} 和 {}", CACHE_SETMEAL_KEY + id, CACHE_SETMEAL_VO_KEY + id);
    }

    /**
     * 新增套餐（先更新数据库，后删除缓存）
     * @param setmealDTO 套餐DTO
     */
    @Override
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        setmeal.setShopId(BaseContext.getShopId());
        // 先更新数据库
        setmealMapper.insert(setmeal);
        Long setmealId = setmeal.getId();
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmealId);
        }
        setmealDishMapper.insertBatch(setmealDishes);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_SETMEAL_LIST_KEY + BaseContext.getShopId() + ":" + setmealDTO.getCategoryId());
        log.info("新增套餐，删除缓存：{}", CACHE_SETMEAL_LIST_KEY + BaseContext.getShopId() + ":" + setmealDTO.getCategoryId());
    }

    /**
     * 根据ID查询套餐详情（使用缓存穿透防护）
     * @param id 套餐ID
     * @return 套餐VO
     */
    @Override
    public SetmealVO getById(Long id) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数说明：key前缀、ID、TypeReference类型引用（用于泛型反序列化）、数据库回调函数、过期时间、时间单位
        SetmealVO setmealVO = cacheClient.queryWithPassThrough(
            CACHE_SETMEAL_VO_KEY,
            id,
            new TypeReference<SetmealVO>() {},
            setmealId -> {
                Setmeal setmeal = setmealMapper.querySetmeal(setmealId);
                if (setmeal == null) {
                    throw new BaseException("套餐不存在");
                }
                SetmealVO vo = new SetmealVO();
                BeanUtils.copyProperties(setmeal, vo);
                vo.setSetmealDishes(setmealDishMapper.getSetmealDishBySetmealId(Collections.singletonList(setmeal.getId())));
                return vo;
            },
            CACHE_SETMEAL_TTL,
            TimeUnit.MINUTES
        );
        return setmealVO;
    }

    /**
     * 更新套餐（先更新数据库，后删除缓存）
     * @param setmealDTO 套餐DTO
     */
    @Override
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 先更新数据库
        setmealMapper.update(setmeal);
        setmealDishMapper.deleteBySetmealId(setmealDTO.getId());
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmealDTO.getId());
        }
        setmealDishMapper.insertBatch(setmealDishes);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_SETMEAL_KEY + setmealDTO.getId());
        stringRedisTemplate.delete(CACHE_SETMEAL_VO_KEY + setmealDTO.getId());
        stringRedisTemplate.delete(CACHE_SETMEAL_LIST_KEY + BaseContext.getShopId() + ":" + setmealDTO.getCategoryId());
        log.info("更新套餐，删除缓存：{}、{}和{}", CACHE_SETMEAL_KEY + setmealDTO.getId(), CACHE_SETMEAL_VO_KEY + setmealDTO.getId(), CACHE_SETMEAL_LIST_KEY + BaseContext.getShopId() + ":" + setmealDTO.getCategoryId());
    }

    /**
     * 批量删除套餐（先更新数据库，后删除缓存）
     * @param ids 套餐ID列表
     */
    @Override
    @Transactional
    public void delete(List<Long> ids) {
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.querySetmeal(id);
            if (setmeal != null && setmeal.getStatus().equals(StatusConstant.ENABLE)) {
                throw new BaseException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        // 先更新数据库
        for (Long id : ids) {
            setmealMapper.deleteSetmeal(id);
            setmealDishMapper.deleteBySetmealId(id);
            // 后删除缓存
            stringRedisTemplate.delete(CACHE_SETMEAL_KEY + id);
            stringRedisTemplate.delete(CACHE_SETMEAL_VO_KEY + id);
            Setmeal setmealInfo = setmealMapper.querySetmeal(id);
            if (setmealInfo != null && setmealInfo.getCategoryId() != null) {
                stringRedisTemplate.delete(CACHE_SETMEAL_LIST_KEY + BaseContext.getShopId() + ":" + setmealInfo.getCategoryId());
            }
        }
        log.info("批量删除套餐，删除缓存");
    }

    /**
     * 管理端根据分类ID查询套餐列表（使用缓存穿透防护）
     * 使用BaseContext.getShopId()获取店铺ID
     * @param categoryId 分类ID
     * @return 套餐列表
     */
    @Override
    public List<Setmeal> list(Long categoryId) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数说明：key前缀、ID、TypeReference类型引用（用于泛型反序列化）、数据库回调函数、过期时间、时间单位
        List<Setmeal> list = cacheClient.queryWithPassThrough(
            CACHE_SETMEAL_LIST_KEY + BaseContext.getShopId() + ":",
            categoryId,
            new TypeReference<List<Setmeal>>() {},
            id -> setmealMapper.getByCategoryId(id),
            CACHE_SETMEAL_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }

    /**
     * 用户端根据分类ID查询套餐列表（使用缓存穿透防护）
     * 显式传递shopId参数，不依赖BaseContext
     * @param categoryId 分类ID
     * @param shopId 店铺ID
     * @return 套餐列表
     */
    @Override
    public List<Setmeal> userSetmealList(Long categoryId, Long shopId) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数说明：key前缀、ID、TypeReference类型引用（用于泛型反序列化）、数据库回调函数、过期时间、时间单位
        // 注意：使用传入的shopId构建缓存key，确保数据隔离
        List<Setmeal> list = cacheClient.queryWithPassThrough(
            CACHE_SETMEAL_LIST_KEY + shopId + ":",
            categoryId,
            new TypeReference<List<Setmeal>>() {},
            id -> setmealMapper.getByCategoryId(id),
            CACHE_SETMEAL_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }

    @Override
    public List<DishItemVO> getDishItemVOListById(Long id) {
        List<SetmealDish> setmealDishes = setmealDishMapper.getSetmealDishByDishId(Collections.singletonList(id));

        List<DishItemVO> dishItemVOList = new ArrayList<>();
        for (SetmealDish sd : setmealDishes) {
            DishVO dishVO = dishMapper.getById(sd.getDishId());
            DishItemVO vo = new DishItemVO();
            BeanUtils.copyProperties(sd, vo);
            vo.setImage(dishVO.getImage());
            vo.setDescription(dishVO.getDescription());
            dishItemVOList.add(vo);
        }
        return dishItemVOList;
    }
}
