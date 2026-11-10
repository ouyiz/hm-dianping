package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

//保存当前登录用户信息的工具类。在一次请求处理过程中，把当前用户 `UserDTO` 保存起来，方便后续业务代码随时获取当前登录用户。
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
