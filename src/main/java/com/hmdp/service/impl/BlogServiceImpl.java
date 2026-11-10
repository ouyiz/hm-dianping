package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    public BlogServiceImpl(IUserService iUserService, StringRedisTemplate stringRedisTemplate) {
        this.userService = iUserService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog=getById(id);
        if(blog==null){
            return Result.fail("博客不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void queryBlogUser(Blog blog) {
        Long userId=blog.getUserId();
        User user=userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user=UserHolder.getUser();
        if(user==null){
            return;
        }
        String key = BLOG_LIKED_KEY+blog.getId();
        Long userId = UserHolder.getUser().getId();
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        blog.setIsLike(score!=null);
    }
    @Override
    public Result likeBlog(Long id) {
        String key=BLOG_LIKED_KEY+id;
        //1.获取当前登录用户id
        Long userId= UserHolder.getUser().getId();
        //2.判断当前登录用户是否已经点赞
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score==null){
            //3.如果未点赞，可以点赞
            //3.1数据库点赞+1
            boolean success=lambdaUpdate()
                    .setSql("liked=liked+1")
                    .eq(Blog::getId,id)
                    .update();
            //3.2 保存到Redis中
            if(success){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
            //4.如果已点赞，取消点赞
            //4.1数据库点赞数-1
            boolean success=lambdaUpdate()
                    .setSql("liked=liked-1")
                    .eq(Blog::getId,id)
                    .update();
            //4.2 把用户从Redis到set集合移除
            if(success){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 查询热门博客
     * @param current 当前页面
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page=query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records=page.getRecords();
        records.forEach(blog->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据博客ID查询博客点赞列表
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key=BLOG_LIKED_KEY+id;
        //1.查询top5的点赞用户
        Set<String> top5=stringRedisTemplate.opsForZSet().range(key,0,4);
        if(top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //2.解析出用户的id
        List<Long> ids=top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.根据用户id查询用户
        List<User> users = userService.list(
                new LambdaQueryWrapper<User>()
                        .in(User::getId, ids)
        );

        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<UserDTO> userDTOS = ids.stream()
                .map(userMap::get)
                .filter(user -> user != null)
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //4.返回
        return Result.ok(userDTOS);
    }

    /**
     * 保存博客
     * @param blog
     * @return
     */
    @Transactional
    @Override
    public Result saveBlog(Blog blog) {
        //1.获取登录用户并设置博客用户
        UserDTO user=UserHolder.getUser();
        blog.setUserId(user.getId());
        //2.保存探店笔记
        Boolean success=save(blog);
        if(!success){
            return Result.fail("新增笔记失败");
        }
        //3.查询笔记所以的粉丝
        List<Follow> follows=followService.lambdaQuery()
                .eq(Follow::getFollowUserId,user.getId())
                .list();
        //4.推送给所以的粉丝
        for(Follow follow:follows){
            Long userId=follow.getUserId();
            String key=FEED_KEY+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        //返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询关注用户的博客列表
     * @param max max负责定位到上次查询结束的时间点
     * @param offset 处理多个博客时间戳相同导致的重复数据。
     * @return
     */
    public  Result queryBlogOfFollow(Long max,Integer offset){
        //1.获取当前用户id
        Long userId=UserHolder.getUser().getId();
        //2.查询收件箱
        String key=FEED_KEY+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples=stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key,0,max,offset,2);
        //3.非空判断
        if(typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //4.解析数据
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime=0;
        int os=1;
        for(ZSetOperations.TypedTuple<String> tuple:typedTuples){
            String idStr=tuple.getValue();
            ids.add(Long.valueOf(idStr));
            long time=tuple.getScore().longValue();
            if(minTime==time){
                os++;
            }else{
                minTime=time;
                os=1;
            }
        }
        //5.根据id查询blog
        LambdaQueryWrapper<Blog> wrapper=new LambdaQueryWrapper<Blog>()
                .in(Blog::getId,ids)
                .orderByDesc(Blog::getId);
        List<Blog> blogs=list(wrapper);
        for(Blog blog:blogs){
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //6.封装并返回
        ScrollResult<Blog> r = new ScrollResult<>();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(minTime);
        return Result.ok(r);
    }
}
