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
package fun.rooe.admin.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Set;

/**
 * <p>
 * 数据权限枚举
 * </p>
 * @author Zheng Jie
 * @date 2020-05-07
 */
@Getter
public enum DataScopeEnum {

    ALL("ALL", "全部数据", "全部", "全部数据"),
    DEPT_AND_CHILD("DEPT_AND_CHILD", "本部门及以下", "本部门及以下"),
    DEPT("DEPT", "本部门", "本级", "本部门"),
    SELF("SELF", "仅本人", "仅本人"),
    CUSTOM("CUSTOM", "自定义", "自定义");

    private final String value;
    private final String description;
    private final Set<String> aliases;

    DataScopeEnum(String value, String description, String... aliases) {
        this.value = value;
        this.description = description;
        this.aliases = Set.copyOf(Arrays.asList(aliases));
    }

    public static DataScopeEnum find(String val) {
        if (val == null) {
            return null;
        }
        for (DataScopeEnum dataScopeEnum : DataScopeEnum.values()) {
            if (dataScopeEnum.getValue().equalsIgnoreCase(val)
                    || dataScopeEnum.aliases.contains(val)) {
                return dataScopeEnum;
            }
        }
        return null;
    }

}
