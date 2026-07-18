package com.nuono.next.permission.access;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredBusinessAccess {

    BusinessCapability capability();

    /** Optional query/form parameter whose value must pass the existing store guard. */
    String storeQueryParameter() default "";
}
