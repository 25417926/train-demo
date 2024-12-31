package com.guo.builder.server;

import com.guo.builder.util.DbUtil;
import com.guo.builder.util.Field;
import com.guo.builder.util.FreemarkerUtil;
import freemarker.template.TemplateException;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ServerGenerator {
    // 表示是否为只读界面
    static boolean readOnly = false;
    static String vuePath = "admin-ui/src/views/HomeView/";
    static String serverPath = "[module]/src/main/java/com/guo/[module]/";
    static String pomPath = "builder/pom.xml";
    static String module = "";
    // static {
    //     new File(serverPath).mkdirs();
    // }

    public static void main(String[] args) throws Exception {
        // 获取mybatis-generator
        String builderPath = getBuilderPath();
        // 比如builder-config-member.xml，得到module = member
        //这里的pom.xml中如果是
        //<configurationFile>src/main/resources/generatorConfig-business.xml</configurationFile>
        //需要将tag值改为src/main/resources/generatorConfig-，才能拿到正确的module值
        module = builderPath.replace("src/main/resources/generatorConfig-", "").replace(".xml", "");
        System.out.println("module: " + module);
        //拼接serverPath
        serverPath = serverPath.replace("[module]", module);
        new File(serverPath).mkdirs();
        System.out.println("servicePath: " + serverPath);

            // 读取table节点
            // 使用SAXReader读取指定路径下的XML文件，路径为'builder/'加上builderPath变量的值
            Document document = new SAXReader().read("builder/" + builderPath);
            // 从读取的文档中选择第一个匹配的table节点
            Node table = document.selectSingleNode("//table");
            // 输出table节点以供调试查看
            System.out.println(table);
            // 获取table节点的tableName属性
            Node tableName = table.selectSingleNode("@tableName");
            // 获取table节点的domainObjectName属性
            Node domainObjectName = table.selectSingleNode("@domainObjectName");
            // 输出tableName和domainObjectName属性的文本内容，用于调试
            System.out.println(tableName.getText() + "/" + domainObjectName.getText());

        // 为DbUtil设置数据源
        Node connectionURL = document.selectSingleNode("//@connectionURL");
        Node userId = document.selectSingleNode("//@userId");
        Node password = document.selectSingleNode("//@password");
        System.out.println("url: " + connectionURL.getText());
        System.out.println("user: " + userId.getText());
        System.out.println("password: " + password.getText());
        DbUtil.url = connectionURL.getText();
        DbUtil.user = userId.getText();
        DbUtil.password = password.getText();

        // 示例：表名 guo_test
        // Domain = guoTest
        String Domain = domainObjectName.getText();
        // domain = guoTest
        String domain = Domain.substring(0, 1).toLowerCase() + Domain.substring(1);
        // do_main = guo-test
        String do_main = tableName.getText().replaceAll("_", "-");
        // 表中文名
        String tableNameCn = DbUtil.getTableComment(tableName.getText());
        List<Field> fieldList = DbUtil.getColumnByTableName(tableName.getText());
        Set<String> typeSet = getJavaTypes(fieldList);

        // 组装参数
        Map<String, Object> param = new HashMap<>();
        param.put("module", module);
        param.put("Domain", Domain);
        param.put("domain", domain);
        param.put("do_main", do_main);
        param.put("tableNameCn", tableNameCn);
        param.put("fieldList", fieldList);
        param.put("typeSet", typeSet);
        param.put("readOnly", readOnly);
        System.out.println("组装参数：" + param);

        gen(Domain, param, "service", "service");
        gen(Domain, param, "controller", "controller");
        gen(Domain, param, "req", "saveReq");
        gen(Domain, param, "req", "queryReq");
        gen(Domain, param, "resp", "queryResp");

        genVue(do_main, param);
    }

    private static void gen(String Domain, Map<String, Object> param, String packageName, String target) throws IOException, TemplateException {

        FreemarkerUtil.initConfig(target + ".ftl");
        String toPath = serverPath + packageName + "/";
        new File(toPath).mkdirs();
        String Target = target.substring(0, 1).toUpperCase() + target.substring(1);
        String fileName = toPath + Domain + Target + ".java";
        System.out.println("开始生成：" + fileName);
        FreemarkerUtil.generator(fileName, param);
    }

    private static void genVue(String do_main, Map<String, Object> param) throws IOException, TemplateException {
        FreemarkerUtil.initConfig("vue.ftl");
        new File(vuePath ).mkdirs();
        String fileName = vuePath + "/" + do_main + ".vue";
        System.out.println("开始生成：" + fileName);
        FreemarkerUtil.generator(fileName, param);
    }

    /**
     * 从配置文件中获取代码生成器的路径
     *
     * @return 代码生成器的路径
     * @throws DocumentException 如果读取配置文件时发生错误
     */
    private static String getBuilderPath() throws DocumentException {
        // 创建SAXReader对象，用于读取XML配置文件
        SAXReader saxReader = new SAXReader();

        // 创建一个命名空间映射，以便解析XML中的命名空间
        Map<String, String> map = new HashMap<String, String>();
        map.put("pom", "http://maven.apache.org/POM/4.0.0");

        // 设置XPath解析器的命名空间，以支持命名空间的查询
        saxReader.getDocumentFactory().setXPathNamespaceURIs(map);

        // 读取配置文件
        Document document = saxReader.read(pomPath);

        // 使用XPath查询配置文件中的configurationFile节点
        Node node = document.selectSingleNode("//pom:configurationFile");

        // 打印并返回节点的文本内容，即代码生成器的路径
        System.out.println(node.getText());
        return node.getText();
    }

    /**
     * 获取所有的Java类型，使用Set去重
     */
    private static Set<String> getJavaTypes(List<Field> fieldList) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < fieldList.size(); i++) {
            Field field = fieldList.get(i);
            set.add(field.getJavaType());
        }
        return set;
    }
}
