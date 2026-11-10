package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserInfoMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOWS_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;



    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId){
        //1.获取登录用户
        Long userId= UserHolder.getUser().getId();
        //2.数据库查询
        Integer count=query()
                .eq("user_id",userId)
                .eq("follow_user_id",followUserId)
                .count();
        //3.判断
        return Result.ok(count>0);
    }

    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId,Boolean isFollow){
        //1.获取登录用户
        Long userId= UserHolder.getUser().getId();
        String key=FOLLOWS_KEY+userId;
        //2.关注，新增数据
        if(isFollow){
            Follow follow=new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean success=save(follow);
            if(success){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //3.取关
            boolean success=remove(new QueryWrapper<Follow>()
                    .eq("user_id",userId)
                    .eq("follow_user_id",followUserId));
            if(success){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询当前用户与目标用户之间的共同关注列表
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id){
        //1.获取当前用户
        Long userId= UserHolder.getUser().getId();
        //2.求交集
        String key1=FOLLOWS_KEY+userId;
        String key2=FOLLOWS_KEY+id;
        Set<String> intersect=stringRedisTemplate.opsForSet().intersect(key1,key2);
        //3.无交集
        if(intersect==null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //4.解析id集合
        List<Long> ids=intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //5.查询用户
        List<UserDTO> userDTOS=userService.listByIds(ids)
                .stream()
                .map(user-> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        //6.返回
        return Result.ok(userDTOS);
    }

}
