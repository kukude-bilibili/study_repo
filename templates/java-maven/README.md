# Java Maven 项目模板

## 目录结构

```
java-maven/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── study/
    │               └── Main.java
    └── test/
        └── java/
            └── com/
                └── study/
                    └── MainTest.java
```

## 常用命令

```bash
# 编译
mvn compile

# 运行测试
mvn test

# 打包
mvn package

# 运行主类
mvn exec:java -Dexec.mainClass="com.study.Main"
```
