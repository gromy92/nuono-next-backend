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

    /** Query/form parameter guarded for store access; required when the target type is BusinessStoreAccess. */
    String storeQueryParameter() default "";
}
