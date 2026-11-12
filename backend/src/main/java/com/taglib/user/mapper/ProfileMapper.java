package com.taglib.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProfileMapper {

    List<Map<String, Object>> selectMyResources(@Param("userId") Long userId,
                                                 @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);

    Long countMyResources(@Param("userId") Long userId);

    List<Map<String, Object>> selectMyFavorites(@Param("userId") Long userId,
                                                 @Param("offset") int offset,
                                                 @Param("pageSize") int pageSize);

    Long countMyFavorites(@Param("userId") Long userId);
}
