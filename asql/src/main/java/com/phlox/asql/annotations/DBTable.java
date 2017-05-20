package com.phlox.asql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by fedex on 12.09.16.
 */
@Target(value= ElementType.TYPE)
@Retention(value= RetentionPolicy.RUNTIME)
public @interface DBTable {
    String name() default "";
    MarkMode markMode() default MarkMode.ALL_EXCEPT_IGNORED;
}
