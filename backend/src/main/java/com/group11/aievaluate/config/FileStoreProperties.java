package com.group11.aievaluate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储配置。
 *
 * <p>该类用于集中管理文件存储相关的配置项，
 * 通过 Spring Boot 的 @ConfigurationProperties 机制，
 * 可以将配置文件（application.yml / application.properties）中
 * 以 "filestore" 为前缀的配置项自动绑定到本类的字段上。</p>
 *
 * <p>配置示例（application.yml）：</p>
 * <pre>
 * filestore:
 *   base-dir: /data/storage
 * </pre>
 *
 * <p>使用前需要在配置类或启动类上添加 @EnableConfigurationProperties(FileStoreProperties.class)
 * 或使用 @ConfigurationPropertiesScan 启用配置绑定。</p>
 */
// @ConfigurationProperties：Spring Boot 提供的注解，
// 表示该类的属性会从配置文件中读取，prefix 指定配置项的前缀为 "filestore"
@ConfigurationProperties(prefix = "filestore")
public class FileStoreProperties {

    /**
     * 存储根目录。
     *
     * <p>所有上传/存储的文件都存放在该目录之下。
     * 对应配置文件中的 "filestore.base-dir" 配置项。</p>
     *
     * <p>默认值为 "./storage"，即当前工作目录下的 storage 文件夹。</p>
     */
    // private：私有字段，外部无法直接访问，必须通过 getter/setter 操作
    private String baseDir = "./storage"; // 初始化默认值，未配置时使用相对路径 ./storage

    /**
     * 获取存储根目录路径。
     *
     * @return 存储根目录的路径字符串
     */
    // public：对外公开的读取方法
    // Spring Boot 绑定配置主要依赖 getter/setter（JavaBean 规范）
    public String getBaseDir() {
        // 直接返回成员变量 baseDir 的当前值
        return baseDir;
    }

    /**
     * 设置存储根目录路径。
     *
     * <p>由 Spring Boot 在启动时自动调用，
     * 将配置文件中的 "filestore.base-dir" 的值注入进来。</p>
     *
     * @param baseDir 存储根目录的路径字符串
     */
    // public：对外公开的写入方法，Spring 通过反射调用它完成属性绑定
    public void setBaseDir(String baseDir) {
        // this.baseDir 表示当前对象的成员变量，避免与参数同名混淆
        // 将传入的参数值赋给成员变量
        this.baseDir = baseDir;
    }
}

