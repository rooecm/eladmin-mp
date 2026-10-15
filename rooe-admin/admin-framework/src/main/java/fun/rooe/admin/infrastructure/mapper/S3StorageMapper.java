/*
*  Copyright 2019-2025 Zheng Jie
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License.
*  You may obtain a copy of the License at
*
*  http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software
*  distributed under the License is distributed on an "AS IS" BASIS,
*  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*  See the License for the specific language governing permissions and
*  limitations under the License.
*/
package fun.rooe.admin.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.rooe.admin.infrastructure.domain.S3StorageDO;
import fun.rooe.admin.infrastructure.domain.dto.S3StorageQueryCriteria;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author Zheng Jie
* @date 2025-06-19
**/
@Mapper
public interface S3StorageMapper extends BaseMapper<S3StorageDO> {

    IPage<S3StorageDO> findAll(@Param("criteria") S3StorageQueryCriteria criteria, Page<Object> page);

    List<S3StorageDO> findAll(@Param("criteria") S3StorageQueryCriteria criteria);

    String selectFilePathById(@Param("id") Long id);
}