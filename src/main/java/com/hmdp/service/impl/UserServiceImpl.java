package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
@Transactional
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    /**
//     * 发送验证码
//     * @param phone
//     * @param session
//     * @return
//     */
//    @Override
//    public Result sendCode(String phone, HttpSession session){
//        //1.校验手机号
//        if(!RegexUtils.isPhoneInvalid((phone))){
//            //2.手机号不合法
//            return Result.fail("手机号格式错误");
//        }
//        //3.手机号合法
//        //3.1 生成验证码
//        String code= RandomUtil.randomNumbers(6);
//        //3.2 保存验证码
//        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        //3.3 发送验证码
//        log.debug("发送验证码成功：{}",code);
//        return Result.ok();
//    }
//
//    /**
//     * 登录功能
//     * @param loginFormDTO
//     * @param session
//     * @return
//     */
//    @Override
//    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
//        //1.获取手机号、验证码
//        String phone=loginFormDTO.getPhone();
//        String code=loginFormDTO.getCode();
//        //2.校验验证码
//        String cachecode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
//        if (cachecode==null || !cachecode.equals(code)){
//            return Result.fail("验证码错误");
//        }
//        //3.根据手机号查找用户
//        User user=lambdaQuery()
//                .eq(User::getPhone,phone)
//                .one();
//
//        //4.用户不存在
//        if(user==null){
//            user=createUser(phone);
//        }
//
//        UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
//        //5.保存用户信息
//        String token= UUID.randomUUID().toString();
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
//                CopyOptions.create()
//                        .setIgnoreNullValue(true)  // 忽略空值
//                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString())  // 将字段值转换为字符串
//        );
//        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
//        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
//
//        //6.返回token
//        return Result.ok(token);
//    }
//
//    /**
//     * 创建新用户
//     * @param phone
//     */
//    private User createUser(String phone){
//        User user=new User();
//        user.setPhone(phone);
//        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
//        save(user);
//        return user;
//    }

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session){
        //1.校验手机号是否合法
        if(!RegexUtils.isPhoneInvalid(phone)) {
            //2.手机号不合法，返回错误
            return Result.fail("手机号格式错误");
        }
        //3.生成验证码
        String code=RandomUtil.randomNumbers(6);
        //4.保存验证码
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.返回发送成功
        log.debug("发送验证码成功：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session){
        //1.获取手机号与验证码
        String phone = loginForm.getPhone();
        String code=loginForm.getCode();
        //2.从Redis中获取验证码
        String  cacheCode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        //3.比较验证码
        if(cacheCode==null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //4.查找用户
        User user=lambdaQuery().eq(User::getPhone,phone).one();
        //5.用户不存在
        if(user==null) {
            user=createUser(phone);
        }
        //6.User转UserDTO
        UserDTO userDTO=BeanUtil.toBean(user,UserDTO.class);
        //7.随机生成token
        String token = UUID.randomUUID().toString();
        //8.将UserDTO 保存到 Redis
        Map<String,Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,map);
        //9.设置过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
        //10.返回
        return Result.ok(token);
    }

    private User createUser(String phone){
        User user=new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+phone);
        save(user);
        return user;
    }

    /**
     * 用户签到功能
     * @return
     */
    @Override
    public Result sign(){
        //1.获取当前用户登录id
        Long userId= UserHolder.getUser().getId();
        //2.获取当前时间
        LocalDateTime now=LocalDateTime.now();
        //3.拼接key
        String keySuffix= now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        String key=USER_SIGN_KEY+userId+":"+keySuffix;
        //4.获取今天是第几天
        int dayOfMonth=now.getDayOfMonth();
        //5.写入Redis
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);
        return Result.ok();
    }

    /**
     * 统计用户签到次数
     * @return
     */
    @Override
    public Result signCount(){
        //1.获取当前用户id
        Long userId= UserHolder.getUser().getId();
        //2.获取当前时间
        LocalDateTime now=LocalDateTime.now();
        //3.获取key
        String keySuffix= now.format(DateTimeFormatter.ofPattern("yyyy:MM"));
        String key=USER_SIGN_KEY+userId+":"+keySuffix;
        //4.获取今天是第几天
        int dayOfMonth=now.getDayOfMonth();
        //5.获取本月的签到记录
        List<Long> list=stringRedisTemplate.opsForValue().bitField(
                key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if(list==null || list.isEmpty()){
            return Result.ok(0);
        }
        //6.计算连续签到次数
        Long num=list.get(0);
        if(num==null){
            return Result.ok(0);
        }
        int count=0;
        while(true){
            if((num & 1) ==0) {
                break;
            }else{
                count++;
            }
            num>>>=1;
        }
        return Result.ok(count);
    }
}
