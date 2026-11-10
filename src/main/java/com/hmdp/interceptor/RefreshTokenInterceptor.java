package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

@Slf4j
@Component
public class RefreshTokenInterceptor implements HandlerInterceptor {

//    private final StringRedisTemplate stringRedisTemplate;
//
//    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }
//
//    @Override
//    public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler)throws Exception{
//        //1.获取请求头的token
//        String token = request.getHeader("authorization");
//        if(StrUtil.isBlank(token)){
//            return true;
//        }
//
//        //2.基于Token获取用户
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);
//
//        //3.判断是否不存在
//        if(userMap.isEmpty()){
//            return true;
//        }
//
//        //4.将查询到的Hash数据转为UserDTO对象
//        UserDTO userDTO= BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
//
//        //5.保存用户信息到ThreadLocal
//        UserHolder.saveUser(userDTO);
//        //6.刷新token有效期
//        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
//        return true;
//    }
//
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)throws Exception {
//        UserHolder.removeUser();
//    }
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取token
        String token = request.getHeader("authorization");
        //2.token为空
        if(StrUtil.isBlank(token)){
            return true;
        }
        //3.查找用户
        Map<Object,Object> map = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY+token);

        //4.用户不存在
        if(map.isEmpty()){
            return true;
        }

        //5.Hash转UserDTO
        UserDTO userDTO=BeanUtil.fillBeanWithMap(map,new UserDTO(),false);

        //6.用户信息存在ThreaLocal
        UserHolder.saveUser(userDTO);

        //7.刷新token
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8.放行
        return  true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
