package fun.rooe.admin.framework.utils;

import java.beans.PropertyDescriptor;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

public final class BeanUtils {

    private BeanUtils() {
    }

    public static void copyNonNullProperties(Object source, Object target) {
        BeanWrapper sourceWrapper = new BeanWrapperImpl(source);
        BeanWrapper targetWrapper = new BeanWrapperImpl(target);
        for (PropertyDescriptor descriptor : org.springframework.beans.BeanUtils.getPropertyDescriptors(source.getClass())) {
            if (descriptor.getReadMethod() == null || descriptor.getWriteMethod() == null) {
                continue;
            }
            Object value = sourceWrapper.getPropertyValue(descriptor.getName());
            if (value != null) {
                targetWrapper.setPropertyValue(descriptor.getName(), value);
            }
        }
    }
}
