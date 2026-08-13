import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;

/**
 * Number & Math 基础语法综合示例
 * 覆盖：包装类、自动装箱/拆箱、Number 方法、Math 方法、大数运算、数值格式化
 */
public class NumberMathDemo {

    public static void main(String[] args) {
        wrapperClassDemo();
        autoBoxingDemo();
        numberMethodsDemo();
        compareDemo();
        mathBasicDemo();
        mathAdvancedDemo();
        floorRoundCeilDemo();
        bigNumberDemo();
        formatDemo();
    }

    // ==================== 1. 包装类概览 ====================
    static void wrapperClassDemo() {
        System.out.println("========== 1. 包装类概览 ==========");

        // 8 种基本类型 → 8 种包装类（Number 子类：Byte Short Integer Long Float Double）
        Byte    b = 127;           // byte 包装
        Short   s = 32767;         // short 包装
        Integer i = 2147483647;    // int 包装
        Long    l = 9223372036854775807L; // long 包装
        Float   f = 3.14f;         // float 包装
        Double  d = 3.1415926535;  // double 包装

        System.out.println("Byte:    " + b + "  (范围: " + Byte.MIN_VALUE + " ~ " + Byte.MAX_VALUE + ")");
        System.out.println("Short:   " + s + "  (范围: " + Short.MIN_VALUE + " ~ " + Short.MAX_VALUE + ")");
        System.out.println("Integer: " + i + "  (范围: " + Integer.MIN_VALUE + " ~ " + Integer.MAX_VALUE + ")");
        System.out.println("Long:    " + l);
        System.out.println("Float:   " + f);
        System.out.println("Double:  " + d);
        System.out.println();
    }

    // ==================== 2. 自动装箱与拆箱 ====================
    static void autoBoxingDemo() {
        System.out.println("========== 2. 自动装箱与拆箱 ==========");

        // 自动装箱：基本类型 → 包装对象
        Integer x = 5;  // 等价于 Integer.valueOf(5)

        // 自动拆箱：包装对象 → 基本类型，参与运算
        x = x + 10;     // 等价于 x = Integer.valueOf(x.intValue() + 10)
        System.out.println("x = 5; x = x + 10 → " + x);  // 15

        // 混合运算也会自动拆箱
        Integer a = 100;
        Double  b = 25.5;
        double result = a + b;  // a 拆箱为 int → 提升为 double → 加法
        System.out.println("100 + 25.5 = " + result);

        System.out.println();
    }

    // ==================== 3. Number 常用方法 ====================
    static void numberMethodsDemo() {
        System.out.println("========== 3. Number 常用方法 ==========");

        Number num = 1234.56;  // 实际是 Double 类型

        // xxxValue() 系列：转换为各种基本类型
        System.out.println("num = " + num);
        System.out.println("  intValue():    " + num.intValue());     // 1234（截断）
        System.out.println("  longValue():   " + num.longValue());    // 1234
        System.out.println("  floatValue():  " + num.floatValue());   // 1234.56
        System.out.println("  doubleValue(): " + num.doubleValue());  // 1234.56
        System.out.println("  byteValue():   " + num.byteValue());    // 截断低8位
        System.out.println("  shortValue():  " + num.shortValue());   // 截断低16位

        // valueOf()：从字符串/基本类型创建包装对象
        Integer i1 = Integer.valueOf(42);
        Integer i2 = Integer.valueOf("42");
        System.out.println("Integer.valueOf(42) == Integer.valueOf(\"42\"): " + i1.equals(i2));

        // parseInt()：字符串 → int
        int parsed = Integer.parseInt("256");
        System.out.println("Integer.parseInt(\"256\") = " + parsed);

        // toString()：包装对象 → 字符串
        String str = i1.toString();
        System.out.println("i1.toString() = \"" + str + "\"");

        System.out.println();
    }

    // ==================== 4. 数值比较 ====================
    static void compareDemo() {
        System.out.println("========== 4. 数值比较 ==========");

        Integer a = 10;
        Double  b = 10.0;

        // equals()：类型不同返回 false
        System.out.println("a.equals(b)? " + a.equals(b));  // false（Integer ≠ Double）

        // compareTo()：比较数值大小
        System.out.println("a.compareTo(5)?  " + a.compareTo(5));   // 正数（大于）
        System.out.println("a.compareTo(10)? " + a.compareTo(10));  // 0（等于）
        System.out.println("a.compareTo(20)? " + a.compareTo(20));  // 负数（小于）

        // 正确跨类型比较：转换为同一类型
        System.out.println("a.doubleValue() == b.doubleValue()? " + (a.doubleValue() == b.doubleValue())); // true

        // 注意：包装类用 == 比较的是引用地址（-128~127 有缓存）
        Integer c1 = 127;
        Integer c2 = 127;
        Integer c3 = 128;
        Integer c4 = 128;
        System.out.println("127 == 127? " + (c1 == c2));   // true（缓存）
        System.out.println("128 == 128? " + (c3 == c4));   // false（超出缓存范围）

        System.out.println();
    }

    // ==================== 5. Math 基本方法 ====================
    static void mathBasicDemo() {
        System.out.println("========== 5. Math 基本方法 ==========");

        System.out.println("PI  = " + Math.PI);
        System.out.println("E   = " + Math.E);

        // 三角函数
        System.out.println("sin(π/2)     = " + Math.sin(Math.PI / 2));   // 1.0
        System.out.println("cos(0)       = " + Math.cos(0));             // 1.0
        System.out.println("tan(π/3)     = " + Math.tan(Math.PI / 3));   // √3
        System.out.println("atan(1)      = " + Math.atan(1));            // π/4

        // 角度与弧度转换
        System.out.println("toDegrees(π/2) = " + Math.toDegrees(Math.PI / 2));  // 90.0
        System.out.println("toRadians(90)  = " + Math.toRadians(90));            // π/2

        // 取整方法
        System.out.println("ceil(3.2)   = " + Math.ceil(3.2));    // 4.0
        System.out.println("floor(3.8)  = " + Math.floor(3.8));   // 3.0
        System.out.println("round(3.5)  = " + Math.round(3.5));   // 4
        System.out.println("rint(3.5)   = " + Math.rint(3.5));    // 4.0（银行家舍入）

        // 绝对值、最值
        System.out.println("abs(-10)    = " + Math.abs(-10));
        System.out.println("min(3, 8)   = " + Math.min(3, 8));
        System.out.println("max(3, 8)   = " + Math.max(3, 8));

        System.out.println();
    }

    // ==================== 6. Math 高级运算 ====================
    static void mathAdvancedDemo() {
        System.out.println("========== 6. Math 高级运算 ==========");

        // 幂与对数
        System.out.println("pow(2, 10)      = " + Math.pow(2, 10));      // 1024.0
        System.out.println("sqrt(144)       = " + Math.sqrt(144));        // 12.0
        System.out.println("exp(1)          = " + Math.exp(1));           // e ≈ 2.718
        System.out.println("log(e)          = " + Math.log(Math.E));      // 1.0
        System.out.println("log10(100)      = " + Math.log10(100));       // 2.0

        // 随机数
        System.out.println("random()        = " + Math.random());         // [0.0, 1.0)
        int randomInt = (int) (Math.random() * 100) + 1;                  // [1, 100]
        System.out.println("random [1,100]  = " + randomInt);

        // hypot：计算 sqrt(x² + y²)
        System.out.println("hypot(3, 4)     = " + Math.hypot(3, 4));     // 5.0

        System.out.println();
    }

    // ==================== 7. floor / round / ceil 对比 ====================
    static void floorRoundCeilDemo() {
        System.out.println("========== 7. floor / round / ceil 对比 ==========");

        double[] nums = { 1.4, 1.5, 1.6, -1.4, -1.5, -1.6 };
        System.out.printf("%-8s %-10s %-10s %-10s%n", "参数", "floor", "round", "ceil");
        System.out.println("----------------------------------------");

        for (double num : nums) {
            System.out.printf("%-8s %-10.1f %-10d %-10.1f%n",
                    num,
                    Math.floor(num),
                    Math.round(num),
                    Math.ceil(num));
        }

        System.out.println();
    }

    // ==================== 8. 大数运算 ====================
    static void bigNumberDemo() {
        System.out.println("========== 8. 大数运算（BigInteger / BigDecimal）==========");

        // BigInteger：任意精度整数
        BigInteger bigInt = new BigInteger("12345678901234567890");
        BigInteger sum = bigInt.add(new BigInteger("1"));
        BigInteger product = bigInt.multiply(new BigInteger("2"));
        System.out.println("BigInteger: " + bigInt);
        System.out.println("  + 1  = " + sum);
        System.out.println("  * 2  = " + product);

        // BigDecimal：任意精度小数（精确计算，避免浮点误差）
        BigDecimal bigDec = new BigDecimal("1234567890.1234567890");
        BigDecimal decProduct = bigDec.multiply(new BigDecimal("2"));
        BigDecimal decDivide   = bigDec.divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
        System.out.println("BigDecimal: " + bigDec);
        System.out.println("  * 2  = " + decProduct);
        System.out.println("  / 3  = " + decDivide + " (保留4位小数)");

        // 对比：浮点数精度问题
        System.out.println("0.1 + 0.2 (double) = " + (0.1 + 0.2));  // 0.30000000000000004
        BigDecimal d1 = new BigDecimal("0.1");
        BigDecimal d2 = new BigDecimal("0.2");
        System.out.println("0.1 + 0.2 (BigDecimal) = " + d1.add(d2)); // 0.3

        System.out.println();
    }

    // ==================== 9. 数值格式化 ====================
    static void formatDemo() {
        System.out.println("========== 9. 数值格式化 ==========");

        double num = 1234567.89123;

        // NumberFormat：数字格式化
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);
        System.out.println("默认格式(保留2位): " + nf.format(num));  // 1,234,567.89

        // 百分比格式
        NumberFormat pct = NumberFormat.getPercentInstance();
        pct.setMaximumFractionDigits(1);
        System.out.println("百分比格式:        " + pct.format(0.856));  // 85.6%

        // 货币格式
        NumberFormat currency = NumberFormat.getCurrencyInstance();
        System.out.println("货币格式:          " + currency.format(num));

        System.out.println();
    }
}