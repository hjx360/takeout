package com.sky.aspect;

import com.sky.annotation.AutoFill;
import com.sky.constant.AutoFillConstant;
import com.sky.context.BaseContext;
import com.sky.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collection;

@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointcut() {
    }

    @Before("autoFillPointcut()")
    public void autoFill(JoinPoint joinPoint) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        log.info("开始执行公共字段自动填充...");

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();
        Object[] args = joinPoint.getArgs();

        if (args == null || args.length == 0) {
            return;
        }

        Object object = args[0];

        // 处理单个对象或集合的情况
        if (object instanceof Collection) {
            // 如果是集合，遍历集合中的每个元素
            for (Object element : (Collection<?>) object) {
                setFields(element, operationType);
            }
        } else {
            // 如果是单个对象，直接设置字段
            setFields(object, operationType);
        }
    }

    private void setFields(Object object, OperationType operationType) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (operationType == OperationType.INSERT) {
            // 设置创建时间
            object.getClass().getMethod("setCreateTime", LocalDateTime.class).invoke(object, LocalDateTime.now());
            // 设置更新时间
            object.getClass().getMethod("setUpdateTime", LocalDateTime.class).invoke(object, LocalDateTime.now());
            // 设置创建用户
            object.getClass().getMethod("setCreateUser", Long.class).invoke(object, BaseContext.getCurrentId());
            // 设置更新用户
            object.getClass().getMethod("setUpdateUser", Long.class).invoke(object, BaseContext.getCurrentId());
        } else if (operationType == OperationType.UPDATE) {
            // 设置更新时间
            object.getClass().getMethod("setUpdateTime", LocalDateTime.class).invoke(object, LocalDateTime.now());
            // 设置更新用户
            object.getClass().getMethod("setUpdateUser", Long.class).invoke(object, BaseContext.getCurrentId());
        }
    }
}
