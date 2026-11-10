package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

//Redis 缓存数据包装类
//把真正要缓存的数据 data 和一个逻辑过期时间 expireTime 放在一起存入 Redis。
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
