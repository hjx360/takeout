package com.sky.context;

public class BaseContext {

//    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
//    public
//
//    public static void setCurrentId(Long id) {
//        threadLocal.set(id);
//    }
//
//    public static Long getCurrentId() {
//        return threadLocal.get();
//    }
//
//    public static void removeCurrentId() {
//        threadLocal.remove();
//    }
//
//    public static void setShopId(Long shopId) {
//        threadLocal.set(shopId);
//    }
//
//    public static Long getShopId() {
//        return threadLocal.get();
//    }
//
//    public static void removeShopId() {
//        threadLocal.remove();
//    }
//    public static void setRole(Long role) {
//        threadLocal.set(role);
//    }
//
//    public static Long getRole() {
//        return  threadLocal.get();
//    }
//
//    public static void removeRole() {
//        threadLocal.remove();
//


    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();
    public static ThreadLocal<Long> shopIdThreadLocal = new ThreadLocal<>();
    public static ThreadLocal<Long> roleThreadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

    public static void setShopId(Long shopId) {
        shopIdThreadLocal.set(shopId);
    }

    public static Long getShopId() {
        return shopIdThreadLocal.get();
    }

    public static void removeShopId() {
        shopIdThreadLocal.remove();
    }

    public static void setRole(Long role) {
        roleThreadLocal.set(role);
    }

    public static Long getRole() {
        return roleThreadLocal.get();
    }

    public static void removeRole() {
        roleThreadLocal.remove();
    }

}



