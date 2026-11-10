package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 根据博客ID查询博客详情
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 点赞博客
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询热门博客
     * @param current 当前页面
     * @return 热门博客列表结果
     */
    Result queryHotBlog(Integer current);

    /**
     * 保存博客
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 根据博客ID查询博客点赞列表
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 查询关注用户的博客列表
     * @param max max负责定位到上次查询结束的时间点
     * @param offset 处理多个博客时间戳相同导致的重复数据。
     * @return
     */
    Result queryBlogOfFollow(Long max,Integer offset);
}
