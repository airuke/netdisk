package com.airuike.netdisk.backend.security;

import com.airuike.netdisk.backend.entity.SysUser;
import com.airuike.netdisk.backend.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class NetdiskUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;

    public NetdiskUserDetailsService(SysUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, username)
        );

        if (user == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }

        return new LoginUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getNickname(),
                Integer.valueOf(1).equals(user.getStatus())
        );
    }
}