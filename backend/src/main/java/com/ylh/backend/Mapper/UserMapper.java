package com.ylh.backend.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ylh.backend.Entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
