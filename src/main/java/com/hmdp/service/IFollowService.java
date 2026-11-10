package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 判断是否关注
     * @param followUserId
     * @return
     */
    public Result isFollow(Long followUserId);

    /**
     * 关注或取关
     * @param followUserId
     * @param isFollow
     * @return
     */
    public Result follow(Long followUserId,Boolean isFollow);

    /**
     * 查询当前用户与目标用户之间的共同关注列表
     * @param id
     * @return
     */
    public Result followCommons(Long id);
}
