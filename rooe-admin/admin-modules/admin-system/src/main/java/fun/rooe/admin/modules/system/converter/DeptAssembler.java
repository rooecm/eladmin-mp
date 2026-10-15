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
package fun.rooe.admin.modules.system.converter;

import fun.rooe.admin.modules.system.domain.Dept;
import fun.rooe.admin.modules.system.domain.dto.DeptCreateRequest;
import fun.rooe.admin.modules.system.domain.dto.DeptDto;
import fun.rooe.admin.modules.system.domain.dto.DeptUpdateRequest;
import org.mapstruct.BeanMapping;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts department persistence objects into API response objects.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeptAssembler {

    @Mapping(target = "children", ignore = true)
    DeptDto toDto(Dept dept);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<DeptDto> toDtos(List<Dept> depts);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "deptSort", source = "deptSort")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "pid", source = "pid")
    Dept toDept(DeptCreateRequest request);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "deptSort", source = "deptSort")
    @Mapping(target = "enabled", source = "enabled")
    @Mapping(target = "pid", source = "pid")
    void updateDept(DeptUpdateRequest request, @MappingTarget Dept dept);

    default List<DeptDto> toTree(List<DeptDto> deptList) {
        if (deptList == null || deptList.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, DeptDto> deptById = new LinkedHashMap<>();
        for (DeptDto dept : deptList) {
            deptById.putIfAbsent(dept.getId(), dept);
        }

        Set<DeptDto> roots = new LinkedHashSet<>();
        Set<DeptDto> parentsAndOrphans = new LinkedHashSet<>();
        for (DeptDto dept : deptById.values()) {
            if (dept.getPid() == null) {
                roots.add(dept);
            } else {
                DeptDto parent = deptById.get(dept.getPid());
                if (parent == null) {
                    parentsAndOrphans.add(dept);
                } else {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(dept);
                    parentsAndOrphans.add(parent);
                }
            }
        }

        return new ArrayList<>(roots.isEmpty() ? parentsAndOrphans : roots);
    }
}
