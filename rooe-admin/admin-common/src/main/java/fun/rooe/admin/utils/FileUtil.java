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

import fun.rooe.admin.error.CommonErrorCode;
import fun.rooe.admin.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

@Slf4j
public class FileUtil {

    public static final String SYS_TEM_DIR = System.getProperty("java.io.tmpdir") + File.separator;
    private static final int GB = 1024 * 1024 * 1024;
    private static final int MB = 1024 * 1024;
    private static final int KB = 1024;
    private static final DecimalFormat DF = new DecimalFormat("0.00");
    public static final String IMAGE = "图片";
    public static final String TXT = "文档";
    public static final String MUSIC = "音乐";
    public static final String VIDEO = "视频";
    public static final String OTHER = "其他";

    public static String getExtensionName(String filename) {
        if ((filename != null) && (!filename.isEmpty())) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }

    public static String getFileNameNoEx(String filename) {
        if ((filename != null) && (!filename.isEmpty())) {
            int dot = filename.lastIndexOf('.');
            if (dot > -1) {
                return filename.substring(0, dot);
            }
        }
        return filename;
    }

    public static String getSize(long size) {
        String resultSize;
        if (size / GB >= 1) {
            resultSize = DF.format(size / (float) GB) + "GB";
        } else if (size / MB >= 1) {
            resultSize = DF.format(size / (float) MB) + "MB";
        } else if (size / KB >= 1) {
            resultSize = DF.format(size / (float) KB) + "KB";
        } else {
            resultSize = size + "B";
        }
        return resultSize;
    }

    public static File upload(InputStream ins, String originalFilename, String filePath) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddhhmmssS");
        String name = getFileNameNoEx(verifyFilename(originalFilename));
        String suffix = getExtensionName(originalFilename);
        String nowStr = "-" + format.format(new Date());
        try (InputStream source = ins) {
            String fileName = name + nowStr + "." + suffix;
            File dest = new File(filePath + fileName).getCanonicalFile();
            File parent = dest.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                log.error("创建上传目录失败: {}", parent);
            }
            Files.copy(source, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static String getFileType(String type) {
        String documents = "txt doc pdf ppt pps xlsx xls docx";
        String music = "mp3 wav wma mpa ram ra aac aif m4a";
        String video = "avi mpg mpe mpeg asf wmv mov qt rm mp4 flv m4v webm ogv ogg";
        String image = "bmp dib pcp dif wmf gif jpg tif eps psd cdr iff tga pcd mpt png jpeg";
        if (image.contains(type)) {
            return IMAGE;
        } else if (documents.contains(type)) {
            return TXT;
        } else if (music.contains(type)) {
            return MUSIC;
        } else if (video.contains(type)) {
            return VIDEO;
        }
        return OTHER;
    }

    public static void checkSize(long maxSize, long size) {
        if (size > (maxSize * 1024 * 1024)) {
            throw new BusinessException(CommonErrorCode.FILE_TOO_LARGE, "文件超出规定大小:" + maxSize + "MB");
        }
    }

    private static String verifyFilename(String fileName) {
        fileName = fileName.replaceAll("[\\\\/:*?\"<>|~\\s]", "");
        fileName = fileName.trim().replaceAll("^[. ]+|[. ]+$", "");
        int maxFileNameLength = System.getProperty("os.name").startsWith("Windows") ? 260 : 255;
        if (fileName.length() > maxFileNameLength) {
            fileName = fileName.substring(0, maxFileNameLength);
        }
        fileName = fileName.replaceAll("[\\p{Cntrl}]", "");
        fileName = fileName.replaceAll("\\.{2,}", "");
        fileName = fileName.replaceAll("^\\.+/", "");
        return fileName.replaceAll("^(.*)(\\.[^.]*)$", "$1").replaceAll("\\.", "") +
                fileName.replaceAll("^(.*)(\\.[^.]*)$", "$2");
    }

    public static void del(String path) {
        Path target = Paths.get(path);
        try {
            if (Files.isDirectory(target)) {
                try (var paths = Files.walk(target)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(current -> {
                        try {
                            Files.delete(current);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void del(File file) {
        del(file.getAbsolutePath());
    }

    public static String getMimeType(String filename) {
        String mimeType = URLConnection.guessContentTypeFromName(filename);
        return mimeType == null ? "application/octet-stream" : mimeType;
    }
}
