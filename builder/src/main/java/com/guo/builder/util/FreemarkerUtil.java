package com.guo.builder.util;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Freemarker工具类，用于初始化配置和生成文件
 */
public class FreemarkerUtil {
    //初始化路径
    static String ftlPath = "D:\\programme\\JAVA_DEV\\code\\train\\builder\\src\\main\\java\\com\\guo\\builder\\ftl";

    //保存加载后的模板
    static Template temp;

    /**
     * 读模板
     * @param ftlName 模板文件名
     * @throws IOException 当模板文件读取失败时抛出此异常
     */
    public static void initConfig(String ftlName) throws IOException {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_21);
        cfg.setDirectoryForTemplateLoading(new File(ftlPath));
        cfg.setObjectWrapper(new DefaultObjectWrapper(Configuration.VERSION_2_3_21));
        temp = cfg.getTemplate(ftlName);
    }

    /**
     * 根据模板，生成文件
     * @param fileName 生成的文件名
     * @param map 包含模板所需数据的映射
     * @throws IOException 当文件写入失败时抛出此异常
     * @throws TemplateException 当模板处理失败时抛出此异常
     */
    public static void generator(String fileName, Map<String, Object> map) throws IOException, TemplateException {
        FileWriter fw = new FileWriter(fileName);
        BufferedWriter bw = new BufferedWriter(fw);
        temp.process(map, bw);
        bw.flush();
        fw.close();
    }
}
