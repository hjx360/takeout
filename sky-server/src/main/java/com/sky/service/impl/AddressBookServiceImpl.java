package com.sky.service.impl;

import cn.hutool.core.lang.TypeReference;
import com.sky.context.BaseContext;
import com.sky.entity.AddressBook;
import com.sky.mapper.AddressBookMapper;
import com.sky.service.AddressBookService;
import com.sky.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class AddressBookServiceImpl implements AddressBookService {
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 缓存key前缀
    private static final String CACHE_ADDRESS_KEY = "cache:address:";
    private static final String CACHE_ADDRESS_LIST_KEY = "cache:address:list:";
    // 缓存过期时间
    private static final Long CACHE_ADDRESS_TTL = 30L;

    /**
     * 条件查询（使用缓存穿透防护）
     *
     * @param addressBook
     * @return
     */
    public List<AddressBook> list(AddressBook addressBook) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数说明：key前缀、ID、TypeReference类型（支持泛型）、数据库回调函数、过期时间、时间单位
        //String key = CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId();
        List<AddressBook> list = cacheClient.queryWithPassThrough(
            CACHE_ADDRESS_LIST_KEY,
            BaseContext.getCurrentId(),
            new TypeReference<List<AddressBook>>() {},
            userId -> addressBookMapper.list(addressBook),
            CACHE_ADDRESS_TTL,
            TimeUnit.MINUTES
        );
        return list;
    }

    /**
     * 新增地址（先更新数据库，后删除缓存）
     *
     * @param addressBook
     */
    public void save(AddressBook addressBook) {
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBook.setIsDefault(0);
        // 先更新数据库
        addressBookMapper.insert(addressBook);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
        log.info("新增地址，删除缓存：{}", CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
    }

    /**
     * 根据id查询（使用缓存穿透防护）
     *
     * @param id
     * @return
     */
    public AddressBook getById(Long id) {
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数说明：key前缀、ID、TypeReference类型、数据库回调函数、过期时间、时间单位
        AddressBook addressBook = cacheClient.queryWithPassThrough(
            CACHE_ADDRESS_KEY,
            id,
            new TypeReference<AddressBook>() {},
            addressId -> addressBookMapper.getById(addressId),
            CACHE_ADDRESS_TTL,
            TimeUnit.MINUTES
        );
        return addressBook;
    }

    /**
     * 根据id修改地址（先更新数据库，后删除缓存）
     *
     * @param addressBook
     */
    public void update(AddressBook addressBook) {
        // 先更新数据库
        addressBookMapper.update(addressBook);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_ADDRESS_KEY + addressBook.getId());
        stringRedisTemplate.delete(CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
        log.info("修改地址，删除缓存：{} 和 {}", CACHE_ADDRESS_KEY + addressBook.getId(), CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
    }

    /**
     * 设置默认地址（先更新数据库，后删除缓存）
     *
     * @param addressBook
     */
    @Transactional
    public void setDefault(AddressBook addressBook) {
        //1、将当前用户的所有地址修改为非默认地址 update address_book set is_default = ? where user_id = ?
        addressBook.setIsDefault(0);
        addressBook.setUserId(BaseContext.getCurrentId());
        addressBookMapper.updateIsDefaultByUserId(addressBook);

        //2、将当前地址改为默认地址 update address_book set is_default = ? where id = ?
        addressBook.setIsDefault(1);
        // 先更新数据库
        addressBookMapper.update(addressBook);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_ADDRESS_KEY + addressBook.getId());
        stringRedisTemplate.delete(CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
        log.info("设置默认地址，删除缓存：{} 和 {}", CACHE_ADDRESS_KEY + addressBook.getId(), CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
    }

    /**
     * 根据id删除地址（先更新数据库，后删除缓存）
     *
     * @param id
     */
    public void deleteById(Long id) {
        // 先更新数据库
        addressBookMapper.deleteById(id);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_ADDRESS_KEY + id);
        stringRedisTemplate.delete(CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
        log.info("删除地址，删除缓存：{} 和 {}", CACHE_ADDRESS_KEY + id, CACHE_ADDRESS_LIST_KEY + BaseContext.getCurrentId());
    }

}
