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
package fun.rooe.admin.infrastructure.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.rooe.admin.utils.FileUtil;
import fun.rooe.admin.response.PageResult;
import fun.rooe.admin.infrastructure.utils.PageUtil;
import fun.rooe.admin.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import fun.rooe.admin.infrastructure.config.properties.FileProperties;
import fun.rooe.admin.infrastructure.domain.LocalStorage;
import fun.rooe.admin.infrastructure.domain.dto.LocalStorageQueryCriteria;
import fun.rooe.admin.exception.BadRequestException;
import fun.rooe.admin.infrastructure.mapper.LocalStorageMapper;
import fun.rooe.admin.infrastructure.service.LocalStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Objects;

/**
* @author Zheng Jie
* @date 2019-09-05
*/
@Service
@RequiredArgsConstructor
public class LocalStorageServiceImpl extends ServiceImpl<LocalStorageMapper, LocalStorage> implements LocalStorageService {

    private final LocalStorageMapper localStorageMapper;
    private final FileProperties properties;

    @Override
    public PageResult<LocalStorage> queryAll(LocalStorageQueryCriteria criteria, Page<Object> page){
        return PageUtil.toPage(localStorageMapper.findAll(criteria, page));
    }

    @Override
    public List<LocalStorage> queryAll(LocalStorageQueryCriteria criteria){
        return localStorageMapper.findAll(criteria);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocalStorage create(String name, MultipartFile multipartFile) {
        FileUtil.checkSize(properties.getMaxSize(), multipartFile.getSize());
        String suffix = FileUtil.getExtensionName(multipartFile.getOriginalFilename());
        String type = FileUtil.getFileType(suffix);
        File file;
        try {
            file = FileUtil.upload(multipartFile.getInputStream(), multipartFile.getOriginalFilename(), properties.getPath().getPath() + type +  File.separator);
        } catch (IOException e) {
            throw new BadRequestException("上传失败");
        }
        if(Objects.isNull(file)){
            throw new BadRequestException("上传失败");
        }
        try {
            name = StringUtils.isBlank(name) ? FileUtil.getFileNameNoEx(multipartFile.getOriginalFilename()) : name;
            LocalStorage localStorage = new LocalStorage(
                    file.getName(),
                    name,
                    suffix,
                    file.getPath(),
                    type,
                    FileUtil.getSize(multipartFile.getSize())
            );
            save(localStorage);
            return localStorage;
        }catch (Exception e){
            FileUtil.del(file);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(LocalStorage resources) {
        LocalStorage localStorage = getById(resources.getId());
        localStorage.copy(resources);
        saveOrUpdate(localStorage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(Long[] ids) {
        for (Long id : ids) {
            LocalStorage storage = getById(id);
            FileUtil.del(storage.getPath());
            removeById(storage);
        }
    }

    @Override
    public void download(List<LocalStorage> queryAll, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = new ArrayList<>();
        for (LocalStorage localStorage : queryAll) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("文件名", localStorage.getRealName());
            map.put("备注名", localStorage.getName());
            map.put("文件类型", localStorage.getType());
            map.put("文件大小", localStorage.getSize());
            map.put("创建者", localStorage.getCreateBy());
            map.put("创建日期", localStorage.getCreateTime());
            list.add(map);
        }
        FileUtil.downloadExcel(list, response);
    }
}
