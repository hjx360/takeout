package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        claims.put(JwtClaimsConstant.SHOP_ID, employee.getShopId());
        claims.put(JwtClaimsConstant.ROLE, employee.getRole());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims);

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .shopId(employee.getShopId())
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        return Result.success();
    }


    /**
     * 新增员工
     *
     * @param employeeDto
     * @return
     */
    @PostMapping
    public Result insertEmployee(@RequestBody EmployeeDTO employeeDto) {
        if (BaseContext.getRole() != 0L) {
            return Result.error("无权限");
        }
        employeeService.insertEmployee(employeeDto);
        return Result.success();
    }

    /**
     * 根据id查询员工
     *
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public Result<Employee> getEmployeeById(@PathVariable Long id) {
        if (BaseContext.getRole() != 0L) {
            return Result.error("无权限");
        }
        Employee employee = employeeService.getEmployee(id);
        return Result.success(employee);
    }

    /**
     * 修改员工
     *
     * @param employeeDto
     * @return
     */
    @PutMapping
    public Result updateEmployee(@RequestBody EmployeeDTO employeeDto) {
        if (BaseContext.getRole() != 0L) {
            return Result.error("无权限");
        }
        employeeService.updateEmployee(employeeDto);
        return Result.success();
    }

    /**
     * 启用和禁用员工账号
     * @param status
     * @return
     */
    @PostMapping("/status/{status}")
    public Result updateEmployeeStatus(@PathVariable Integer status, Long id) {
        if (BaseContext.getRole() != 0L) {
            return Result.error("无权限");
        }
        employeeService.updateEmployeeStatus(status, id);
        return Result.success();
    }
    /**
     * 修改密码
     * @param passwordEditDTO
     * @return
     */
    @PutMapping("/editPassword")
    public Result editPassword(@RequestBody PasswordEditDTO passwordEditDTO) {
        employeeService.editPassword(passwordEditDTO);
        return Result.success();
    }
    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @GetMapping("/page")
    public Result<PageResult> pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        if (BaseContext.getRole() != 0L){
            return Result.error("无权限");
        }
        return Result.success(employeeService.pageQuery(employeePageQueryDTO));
    }

}

