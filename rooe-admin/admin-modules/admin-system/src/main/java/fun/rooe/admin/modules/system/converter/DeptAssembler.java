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

import fun.rooe.admin.modules.system.domain.dto.DeptDTO;
import fun.rooe.admin.modules.system.domain.entity.Dept;
import fun.rooe.admin.modules.system.domain.request.DeptCreateRequest;
import fun.rooe.admin.modules.system.domain.request.DeptUpdateRequest;
import org.mapstruct.*;

import java.util.*;

/**
 * Converts department persistence objects into API response objects.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DeptAssembler {

    @Mapping(target = "children", ignore = true)
    DeptDTO toDto(Dept dept);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<DeptDTO> toDtos(List<Dept> depts);

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

    default List<DeptDTO> toTree(List<DeptDTO> deptList) {
        if (deptList == null || deptList.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, DeptDTO> deptById = new LinkedHashMap<>();
        for (DeptDTO dept : deptList) {
            deptById.putIfAbsent(dept.getId(), dept);
        }

        Set<DeptDTO> roots = new LinkedHashSet<>();
        Set<DeptDTO> parentsAndOrphans = new LinkedHashSet<>();
        for (DeptDTO dept : deptById.values()) {
            if (dept.getPid() == null) {
                roots.add(dept);
            } else {
                DeptDTO parent = deptById.get(dept.getPid());
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
