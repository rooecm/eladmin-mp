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
package fun.rooe.admin.utils;

import cn.idev.excel.FastExcel;
import cn.idev.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExcelUtil {

    public static void write(List<Map<String, Object>> list, OutputStream outputStream) throws IOException {
        List<Map<String, Object>> sanitizedList = list.parallelStream().map(map -> {
            Map<String, Object> sanitizedMap = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (value instanceof String strValue
                        && (strValue.startsWith("=") || strValue.startsWith("+")
                        || strValue.startsWith("-") || strValue.startsWith("@"))) {
                    strValue = "'" + strValue;
                }
                sanitizedMap.put(key, value);
            });
            return sanitizedMap;
        }).collect(Collectors.toList());
        Set<String> headers = new LinkedHashSet<>();
        sanitizedList.forEach(map -> headers.addAll(map.keySet()));
        List<List<String>> head = headers.stream().map(List::of).toList();
        List<List<Object>> rows = sanitizedList.stream()
                .map(data -> headers.stream()
                        .map(header -> data.get(header) == null ? "" : data.get(header))
                        .toList())
                .toList();
        FastExcel.write(outputStream)
                .head(head)
                .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                .sheet("Sheet1")
                .doWrite(rows);
    }
}
