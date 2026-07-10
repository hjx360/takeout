package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.CacheClient;
import cn.hutool.core.lang.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.xml.bind.Element;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    // 缓存key前缀
    private static final String CACHE_EMPLOYEE_KEY = "cache:employee:";
    // 缓存过期时间
    private static final Long CACHE_EMPLOYEE_TTL = 30L;

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        //对前端传过来的密码进行md5加密
        password = DigestUtils.md5DigestAsHex(password.getBytes());

        if (!password.equals(employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }


    public void insertEmployee(EmployeeDTO employeeDto) {
        //设置默认密码 123456
        Employee employee = new Employee();
        //拷贝
        BeanUtils.copyProperties(employeeDto, employee);
        employee.setPassword(DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes()));
        employee.setStatus(StatusConstant.ENABLE);
        employee.setUpdateUser(BaseContext.getCurrentId());
        employee.setCreateUser(BaseContext.getCurrentId());
        employee.setShopId(BaseContext.getShopId());
        employee.setRole(1L);
        employeeMapper.insertEmployee(employee);
    }

    /**
     * 根据ID查询员工（使用缓存穿透防护）
     * @param id 员工ID
     * @return 员工信息
     */
    @Override
    public Employee getEmployee(Long id) {
        Long shopId = BaseContext.getShopId();
        // 使用CacheClient的queryWithPassThrough方法实现缓存穿透防护
        // 参数说明：key前缀、ID、TypeReference类型、数据库回调函数、过期时间、时间单位
        Employee employee = cacheClient.queryWithPassThrough(
            CACHE_EMPLOYEE_KEY + shopId + ":",
            id,
            new TypeReference<Employee>() {},
            empId -> {
                Employee emp = employeeMapper.getEmployee(empId, shopId);
                emp.setPassword(null);
                return emp;
            },
            CACHE_EMPLOYEE_TTL,
            TimeUnit.MINUTES
        );
        return employee;
    }

    /**
     * 更新员工信息（先更新数据库，后删除缓存）
     * @param employeeDto 员工DTO
     */
    @Override
    public void updateEmployee(EmployeeDTO employeeDto) {
        Employee employee = new Employee();
        //对密码进行校验，不能超过11位
        if (employeeDto.getPhone().length() > 11) {
            throw new IllegalArgumentException(MessageConstant.PHONE_LENGTH_ERROR);
        }
        BeanUtils.copyProperties(employeeDto, employee);
        // 先更新数据库
        employeeMapper.updateEmployee(employee);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_EMPLOYEE_KEY + BaseContext.getShopId() + ":" + employeeDto.getId());
        log.info("更新员工信息，删除缓存：{}", CACHE_EMPLOYEE_KEY + BaseContext.getShopId() + ":" + employeeDto.getId());
    }

    /**
     * 更新员工状态（先更新数据库，后删除缓存）
     * @param status 状态
     * @param id 员工ID
     */
    @Override
    public void updateEmployeeStatus(Integer status, Long id) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setStatus(status);
        // 先更新数据库
        employeeMapper.updateEmployee(employee);
        // 后删除缓存
        stringRedisTemplate.delete(CACHE_EMPLOYEE_KEY + BaseContext.getShopId() + ":" + id);
        log.info("更新员工状态，删除缓存：{}", CACHE_EMPLOYEE_KEY + BaseContext.getShopId() + ":" + id);
    }

    @Override
    public void editPassword(PasswordEditDTO passwordEditDTO) {
        String oldPassword = passwordEditDTO.getOldPassword();
        String newPassword = passwordEditDTO.getNewPassword();
        Employee employee = employeeMapper.getEmployee(passwordEditDTO.getEmpId(), BaseContext.getCurrentId());
        if (!employee.getPassword().equals(DigestUtils.md5DigestAsHex(oldPassword.getBytes()))) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }
        employee.setPassword(DigestUtils.md5DigestAsHex(newPassword.getBytes()));
        employeeMapper.updateEmployee(employee);
    }

    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO,BaseContext.getShopId());
        return new PageResult(page.getTotal(), page.getResult());

    }

}
