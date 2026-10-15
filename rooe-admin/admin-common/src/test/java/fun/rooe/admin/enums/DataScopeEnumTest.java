package fun.rooe.admin.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataScopeEnumTest {

    @Test
    void supportsAllFiveCodes() {
        assertEquals(DataScopeEnum.ALL, DataScopeEnum.find("ALL"));
        assertEquals(DataScopeEnum.DEPT_AND_CHILD, DataScopeEnum.find("DEPT_AND_CHILD"));
        assertEquals(DataScopeEnum.DEPT, DataScopeEnum.find("DEPT"));
        assertEquals(DataScopeEnum.SELF, DataScopeEnum.find("SELF"));
        assertEquals(DataScopeEnum.CUSTOM, DataScopeEnum.find("CUSTOM"));
    }

    @Test
    void readsLegacyChineseValues() {
        assertEquals(DataScopeEnum.ALL, DataScopeEnum.find("全部"));
        assertEquals(DataScopeEnum.DEPT, DataScopeEnum.find("本级"));
        assertEquals(DataScopeEnum.CUSTOM, DataScopeEnum.find("自定义"));
    }
}
