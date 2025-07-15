package com.dahe.v2.modules.auth.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明某个控制器（或方法）对应的后台菜单权限码。
 *
 * <p>默认情况下，权限码会按路由自动推断；当路由段与前端菜单码不一致、
 * 或同一接口需要绑定多个菜单码时，使用该注解显式声明。</p>
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminMenuCode {

    /**
     * 菜单权限码列表，如：{@code /roles}、{@code /admin-users}。
     */
    String[] value() default {};
}

